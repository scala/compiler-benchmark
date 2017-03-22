package scala.bench;

import com.google.common.base.Throwables;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormat;
import org.openjdk.jmh.results.format.ResultFormatFactory;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.format.OutputFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static scala.bench.GitWalker.sanitize;

public class UploadingOutputFormat extends DelegatingOutputFormat {

    private final GitWalkerResult gitResult;

    public UploadingOutputFormat(OutputFormat delegate) {
        super(delegate);
        Repository repository = GitFactory.openGit();
        gitResult = GitWalker.walk(repository);
    }

    @Override
    public void endBenchmark(BenchmarkResult result) {
        super.endBenchmark(result);
        try {
            uploadResult(result);
        } catch (RuntimeException ex){
            System.err.println(Throwables.getStackTraceAsString(ex));
        }
    }

    private byte[] toJSON(BenchmarkResult result) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintStream printStream = new PrintStream(out)) {
            ResultFormat format = ResultFormatFactory.getInstance(ResultFormatType.JSON, printStream);
            format.writeOut(Collections.singleton(new RunResult(result.getParams(), Collections.singleton(result))));
        }
        return out.toByteArray();
    }

    private void uploadResult(BenchmarkResult result) {
        InfluxDB influxDB = Database.connectDb();
        try (Repository repo = GitFactory.openGit()) {
            BatchPoints batchPoints = BatchPoints
                    .database("scala_benchmark")
                    .retentionPolicy("autogen")
                    .consistency(InfluxDB.ConsistencyLevel.ALL)
                    .build();
            Point.Builder pointBuilder = Point.measurement("result");
            BenchmarkParams params = result.getParams();
            Collection<String> paramsKeys = params.getParamsKeys();
            pointBuilder.tag("label", result.getPrimaryResult().getLabel());
            String benchmarkName = result.getParams().getBenchmark().replace("scala.tools.nsc.", "");
            pointBuilder.tag("benchmark", benchmarkName);
            for (String key : paramsKeys) {
                String value = params.getParam(key);
                if (value != null && !value.isEmpty()) {
                    pointBuilder.tag(key, value);
                }
            }
            pointBuilder.addField("score", result.getPrimaryResult().getScore());
            pointBuilder.addField("sampleCount", result.getPrimaryResult().getSampleCount());
            pointBuilder.addField("scoreError", result.getPrimaryResult().getScoreError());
            pointBuilder.addField("scoreUnit", result.getPrimaryResult().getScoreUnit());

            double[] scoreConfidence = result.getPrimaryResult().getScoreConfidence();
            if (scoreConfidence.length == 2) {
                pointBuilder.addField("scoreConfidenceLower", scoreConfidence[0]);
                pointBuilder.addField("scoreConfidenceUpper", scoreConfidence[1]);
            }
            pointBuilder.addField("extendedInfo", result.getPrimaryResult().extendedInfo());

            String scalaVersion = System.getProperty("scalaVersion");
            String scalaRef = System.getProperty("scalaRef");
            List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
            if (scalaRef == null) {
                throw new RuntimeException("Please provide -DscalaRef=...");
            }
            try {
                String branch = gitResult.branchOfRef(scalaRef);
                pointBuilder.tag("branch", branch);
            } catch (IllegalArgumentException iea ){
                pointBuilder.tag("branch", "<none>");
            }
            pointBuilder.tag("hostId", getHostId());
            pointBuilder.addField("javaVersion", System.getProperty("java.runtime.version"));
            pointBuilder.addField("inputArguments", inputArguments.stream().collect(Collectors.joining(" ")));
            pointBuilder.tag("scalaVersion", scalaVersion);


            try (RevWalk walk = new RevWalk(repo)) {
                RevCommit revCommit = walk.parseCommit(repo.resolve(scalaRef));
                pointBuilder.tag("scalaSha", revCommit.getName());
                pointBuilder.tag("commitShortMessage", sanitize(revCommit.getShortMessage()));
                logJSON(result, benchmarkName, scalaRef, revCommit);
                int adjustedCommitTime = GitWalker.adjustCommitTime(revCommit);
                pointBuilder.time(adjustedCommitTime, TimeUnit.MILLISECONDS);
                batchPoints.point(pointBuilder.build());
                influxDB.write(batchPoints);
            }
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        } finally {
            influxDB.close();
        }
    }

    private void logJSON(BenchmarkResult result, String benchmarkName, String scalaRef, RevCommit revCommit) throws IOException {
        String timestamp = DateFormatUtils.format(new Date(), "yyyyMMdd_kkmmss");
        Path path = Paths.get(timestamp + "-" + scalaRef + "-" + benchmarkName + ".json");
        java.nio.file.Files.write(path, toJSON(result));
        System.out.println("Uploading points to benchmark database for " + scalaRef + "/" + revCommit.getName() + ", " + revCommit.getCommitTime() + "s");
        System.out.println("Data in JSON format: " + path);
    }

    private static String getHostId() throws Exception {
        StringBuilder hostId = new StringBuilder();
        hostId.append(InetAddress.getLocalHost().getHostName());
        String mac = getHardwareAddress();
        if (mac != null) {
            hostId.append("@").append(hostId);
        }
        return hostId.toString();
    }

    private static String getHardwareAddress() throws Exception {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface ni = networkInterfaces.nextElement();
            if (!ni.isVirtual() && !ni.isLoopback() && !ni.isPointToPoint() && ni.isUp() && !ni.getName().startsWith("docker")) {
                final byte[] bb = ni.getHardwareAddress();
                StringBuilder builder = new StringBuilder();
                for (byte b : bb) {
                    builder.append(String.format("%02X", b));
                }
                return builder.toString();
            }
        }
        return null;
    }
}
