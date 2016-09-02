package scala.tools.nsc;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@State(Scope.Thread)
public class JavacBenchmark {
    // Prepare source somehow.
    String source = "public class Test { public Test() { System.out.println(\"world\"); } }";
    private JavaCompiler compiler;
    private Path path;

    @Setup
    public void setup() throws IOException {
        Path temp = Files.createTempDirectory("JavacBenchmark");
        path = temp.resolve("Test.java");
        compiler = ToolProvider.getSystemJavaCompiler();
        Files.write(path, source.getBytes(Charset.forName("UTF-8")));
    }

    @Benchmark
    public int bench() {
        // Compile source file.
        int run = compiler.run(null, null, null, path.toAbsolutePath().toString());
        assert run == 0;
        return run;
    }
}
