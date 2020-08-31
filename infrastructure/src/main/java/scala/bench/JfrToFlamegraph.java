package scala.bench;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.util.InputStreamDrainer;
import org.openjdk.jmh.util.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public final class JfrToFlamegraph implements JavaFlightRecorderProfiler.PostProcessor {
  private static final Path JFR_FLAMEGRAPH_DIR = findJfrFlamegraph();
  private static final Path FLAMEGRAPH_DIR = findFlamegraphDir();
  private enum Direction {
    FORWARD,
    REVERSE
  }

  public List<File> postProcess(BenchmarkParams benchmarkParams, File jfrFile) {
    ArrayList<File> generated = new ArrayList<>();
    List<String> events = Arrays.asList("cpu", "allocation-tlab", "allocation-outside-tlab");
    if (JFR_FLAMEGRAPH_DIR != null) {
      for (String event : events) {
        Path csvFile = createCollapsed(event, jfrFile.toPath());
        generated.add(csvFile.toFile());
        if (FLAMEGRAPH_DIR != null) {
          for (Direction direction : EnumSet.allOf(Direction.class)) {
            Path svgPath = flameGraph(csvFile, Arrays.asList(), direction, Arrays.asList());
            generated.add(svgPath.toFile());
          }
        }
      }
    }
    return generated;
  }

  private Path createCollapsed(String eventName, Path jfrFile) {
    ArrayList<String> args = new ArrayList<>();
    args.add("bash");
    args.add("-e");
    args.add(JFR_FLAMEGRAPH_DIR.toAbsolutePath().toString());
    args.add("--output-type");
    args.add("folded");
    args.add("--jfrdump");
    args.add(jfrFile.toAbsolutePath().toString());
    args.add("--event");
    args.add(eventName);
    Path outFile = jfrFile.resolveSibling(jfrFile.getFileName().toString().replace(".jfr", "-" + eventName.toLowerCase() + ".csv"));
    args.add("--output");
    args.add(outFile.toString());
    Collection<String> errorOutput = Utils.tryWith(args.toArray(new String[0]));
    if (errorOutput.isEmpty()) {
      return outFile;
    } else {
      throw new RuntimeException("Error in :" + args.stream().collect(Collectors.joining(" ")), new RuntimeException("Failed to convert JFR to CSV: " + errorOutput.stream().collect(Collectors.joining(System.lineSeparator()) )));
    }
  }

  private static Path findJfrFlamegraph() {
    Path jfrFlameGraphDir = Paths.get(System.getenv("JFR_FLAME_GRAPH_DIR"));
    Path script = jfrFlameGraphDir.resolve("jfr-flame-graph");
    if (!Files.exists(script)) {
      script = jfrFlameGraphDir.resolve("build/install/jfr-flame-graph/bin/jfr-flame-graph");
    }
    if (!Files.exists(script)) {
      return null;
    } else {
      return script.toAbsolutePath();
    }
  }

  private static Path flameGraph(Path collapsedPath, List<String> extra, Direction direction, Collection<? extends String> flameGraphOptions) {
    ArrayList<String> args = new ArrayList<>();
    args.add("perl");
    args.add(FLAMEGRAPH_DIR.resolve("flamegraph.pl").toAbsolutePath().toString());
    args.addAll(flameGraphOptions);
    args.addAll(extra);
    if (direction == Direction.REVERSE) {
      args.add("--reverse");
    }
    args.addAll(Arrays.asList("--minwidth", "1", "--colors", "java", "--cp", "--width", "1800"));
    args.add(collapsedPath.toAbsolutePath().toString());
    Path outputFile = collapsedPath.resolveSibling(collapsedPath.getFileName().toString().replace(".csv", "-" + direction.name().toLowerCase() + ".svg"));
    ProcessBuilder processBuilder = new ProcessBuilder(args);
    processBuilder.redirectOutput(outputFile.toFile());
    try {
      Process p = processBuilder.start();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      InputStreamDrainer errDrainer = new InputStreamDrainer(p.getErrorStream(), baos);
      InputStreamDrainer outDrainer = new InputStreamDrainer(p.getInputStream(), baos);
      int err = p.waitFor();
      errDrainer.start();
      outDrainer.start();
      errDrainer.join();
      outDrainer.join();
      if (err != 0) {
        throw new RuntimeException("Non zero return code from " + args + System.lineSeparator() + baos.toString());
      }
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
    return outputFile;
  }


  private static Path findFlamegraphDir() {
    String flameGraphHome = System.getenv("FLAME_GRAPH_DIR");
    if (flameGraphHome == null) {
      return null;
    } else {
      return Paths.get(flameGraphHome);
    }
  }
}
