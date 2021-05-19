package scala.tools.nsc;

import org.openjdk.jmh.annotations.*;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openjdk.jmh.annotations.Mode.SampleTime;

@BenchmarkMode(SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = {"-Xms2G", "-Xmx2G"})
@State(Scope.Thread)
public class JavacBenchmark {
    @Param(value = {})
    String source;

    // This parameter is set by ScalacBenchmarkRunner / UploadingRunner based on the Scala version.
    // When running the benchmark directly the "latest" symlink is used.
    @Param(value = {"latest"})
    String corpusVersion;

    private JavaCompiler compiler;
    private Path path;

    private Path corpusSourcePath() {
        return Paths.get("..", "corpus", source, corpusVersion); };

    private Path findSourceDir() {
        Path path = corpusSourcePath();
        return Files.exists(path) ? path : Paths.get(source);
    }

    private List<String> sourceFiles() {
        if (source.startsWith("@")) return Collections.emptyList();
        else {
            List<Path> allFiles = walk(findSourceDir()).collect(Collectors.<Path>toList());
            return allFiles.stream().filter(f -> {
                String name = f.getFileName().toString();
                return name.endsWith(".scala") || name.endsWith(".java");
            }).map(x -> x.toAbsolutePath().normalize().toString()).collect(Collectors.toList());
        }
    }

    private Stream<Path> walk(Path dir)  {
        try {
            return Files.walk(dir, FileVisitOption.FOLLOW_LINKS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Setup
    public void setup() throws IOException {
        compiler = ToolProvider.getSystemJavaCompiler();
    }

    @Benchmark
    public int bench() {
        // Compile source file.
        List<String> args = new ArrayList();
        args.add("-d");
        args.add(tempDir.getAbsolutePath());
        args.addAll(sourceFiles());
        int run = compiler.run(null, null, null, args.toArray(new String[]{}));
        assert run == 0;
        return run;
    }


    private File tempDir = null;

    // Executed once per fork
    @Setup(Level.Trial) public void initTemp() {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("output", "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        tempFile.delete();
        tempFile.mkdir();
        tempDir = tempFile;
    }
    @TearDown(Level.Trial) public void clearTemp() throws IOException {
        scala.bench.IOUtils.deleteRecursive(tempDir.toPath());
    }
}
