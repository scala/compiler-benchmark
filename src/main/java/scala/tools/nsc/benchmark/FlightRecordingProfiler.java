package scala.tools.nsc.benchmark;

import com.oracle.jrockit.jfr.client.FlightRecordingClient;
import com.oracle.jrockit.jfr.management.FlightRecordingMBean;
import com.sun.management.DiagnosticCommandMBean;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.profile.ExternalProfiler;
import org.openjdk.jmh.results.*;
import org.openjdk.jmh.util.FileUtils;
import sun.management.ManagementFactoryHelper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.util.*;

// Effectively equivalent to passing jvm args to append for each benchmark. e.g.,
//
//@Fork(jvmArgsAppend =
//{"-XX:+UnlockCommercialFeatures",
//  "-XX:+FlightRecorder",
//  "-XX:StartFlightRecording=duration=60s,filename=./profiling-data.jfr,name=profile,settings=profile",
//  "-XX:FlightRecorderOptions=settings=./openjdk/jdk1.8.0/jre/lib/jfr/profile.jfc,samplethreads=true"
//})

/**
 * The flight recording profiler enables flight recording for benchmarks and starts recording right away.
 */
public class FlightRecordingProfiler implements ExternalProfiler {

    private String startFlightRecordingOptions = "duration=120s,name=profile,settings=profile,";
    private String flightRecorderOptions = "samplethreads=true,stackdepth=1024,";

    /**
     * Directory to contain all generated reports.
     */
    private static final String SAVE_FLIGHT_OUTPUT_TO = System.getProperty("jmh.jfr.saveTo", ".");

    /**
     * Temporary location to record data
     */
    private final String jfrData;

    /**
     * Holds whether recording is supported (checking the existence of the needed unlocking flag)
     */
    private static final boolean IS_SUPPORTED;

    static {
        IS_SUPPORTED = ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-XX:+UnlockCommercialFeatures");
    }

    public FlightRecordingProfiler() throws IOException {
        jfrData = FileUtils.tempFile("jfrData").getAbsolutePath();
    }

    @Override
    public Collection<String> addJVMInvokeOptions(BenchmarkParams params) {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> addJVMOptions(BenchmarkParams params) {

        startFlightRecordingOptions += "filename=" + jfrData;
        flightRecorderOptions       += "settings=" + params.getJvm().replace("bin/java", "lib/jfr/profile.jfc");

        return Arrays.asList(
                "-XX:+FlightRecorder",
                "-XX:StartFlightRecording="  + startFlightRecordingOptions,
                "-XX:FlightRecorderOptions=" + flightRecorderOptions);
    }

    @Override
    public void beforeTrial(BenchmarkParams benchmarkParams) {
    }

    static int currentId;

    @Override
    public Collection<? extends Result> afterTrial(BenchmarkResult benchmarkResult, long l, File stdOut, File stdErr) {
        String target = SAVE_FLIGHT_OUTPUT_TO + "/" + benchmarkResult.getParams().id().replaceAll("/", "-") + "-" + currentId++ + ".jfr";

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        try {
            FileUtils.copy(jfrData, target);
            pw.println("Flight Recording output saved to " + target);
        } catch (IOException e) {
            pw.println("Unable to save flight output to " + target);
        }

        pw.flush();
        pw.close();

        NoResult r = new NoResult(sw.toString());

        return Collections.singleton(r);
    }

    @Override
    public boolean allowPrintOut() {
        return true;
    }

    @Override
    public boolean allowPrintErr() {
        return false;
    }

    @Override
    public boolean checkSupport(List<String> msgs) {
        msgs.add("Commercial features of the JVM need to be enabled for this profiler.");
        return IS_SUPPORTED;
    }

    @Override
    public String label() {
        return "jfr";
    }

    @Override
    public String getDescription() {
        return "Java Flight Recording profiler runs for every benchmark.";
    }

    private class NoResult extends Result<NoResult> {
        private final String output;

        public NoResult(String output) {
            super(ResultRole.SECONDARY, "JFR", of(Double.NaN), "N/A", AggregationPolicy.SUM);

            this.output = output;
        }

        @Override
        protected Aggregator<NoResult> getThreadAggregator() {
            return new NoResultAggregator();
        }

        @Override
        protected Aggregator<NoResult> getIterationAggregator() {
            return new NoResultAggregator();
        }

        @Override
        public String extendedInfo() {
            return "JFR Messages:\n--------------------------------------------\n" + output;
        }

        private class NoResultAggregator implements Aggregator<NoResult> {
            @Override
            public NoResult aggregate(Collection<NoResult> results) {
                String output = "";
                for (NoResult r : results) {
                    output += r.output;
                }
                return new NoResult(output);
            }
        }
    }
}
