package scala.tools.nsc

import java.io.{File, IOException}
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import org.openjdk.jmh.annotations.Mode._
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class ScalacBenchmark {
  @Param(value = Array())
  var source: String = _

  @Param(value = Array(""))
  var extraArgs: String = _

  // This parameter is set by ScalacBenchmarkRunner / UploadingRunner based on the Scala version.
  // When running the benchmark directly the "latest" symlink is used.
  @Param(value = Array("latest"))
  var corpusVersion: String = _

  var driver: Driver = _

  def compileImpl(): Unit = {
    val compilerArgs =
      if (source.startsWith("@")) Array(source)
      else {
        import scala.collection.JavaConverters._
        val allFiles = Files.walk(findSourceDir, FileVisitOption.FOLLOW_LINKS).collect(Collectors.toList[Path]).asScala.toList
        allFiles.filter(_.getFileName.toString.endsWith(".scala")).map(_.toAbsolutePath.toString).toArray
      }

    // MainClass is copy-pasted from compiler for source compatibility with 2.10.x - 2.13.x
    class MainClass extends Driver with EvalLoop {
      def resident(compiler: Global): Unit = loop { line =>
        val command = new CompilerCommand(line split "\\s+" toList, new Settings(scalacError))
        compiler.reporter.reset()
        new compiler.Run() compile command.files
      }

      override def newCompiler(): Global = Global(settings, reporter)

      override protected def processSettingsHook(): Boolean = {
        settings.usejavacp.value = true
        settings.outdir.value = tempDir.getAbsolutePath
        settings.nowarn.value = true
        if (extraArgs != null && extraArgs != "")
          settings.processArgumentString(extraArgs)
        true
      }
    }
    val driver = new MainClass

    driver.process(compilerArgs)
    assert(!driver.reporter.hasErrors)
  }

  private var tempDir: File = null

  // Executed once per fork
  @Setup(Level.Trial) def initTemp(): Unit = {
    val tempFile = java.io.File.createTempFile("output", "")
    tempFile.delete()
    tempFile.mkdir()
    tempDir = tempFile
  }
  @TearDown(Level.Trial) def clearTemp(): Unit = {
    val directory = tempDir.toPath
    Files.walkFileTree(directory, new SimpleFileVisitor[Path]() {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }
      override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
        Files.delete(dir)
        FileVisitResult.CONTINUE
      }
    })
  }

  private def findSourceDir: Path = {
    val path = Paths.get(s"../corpus/$source/$corpusVersion")
    if (Files.exists(path)) path
    else Paths.get(source)
  }
}

// JMH-independent entry point to run the code in the benchmark, for debugging or
// using external profilers.
object ScalacBenchmarkStandalone {
  def main(args: Array[String]): Unit = {
    val bench = new ScalacBenchmark
    bench.source = args(0)
    val iterations = args(1).toInt
    bench.initTemp()
    var i = 0
    while (i < iterations) {
      bench.compileImpl()
      i += 1
    }
    bench.clearTemp()
  }
}

@State(Scope.Benchmark)
@BenchmarkMode(Array(SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
// TODO -Xbatch reduces fork-to-fork variance, but incurs 5s -> 30s slowdown
@Fork(value = 16, jvmArgs = Array("-XX:CICompilerCount=2", "-Xms2G", "-Xmx2G"))
class ColdScalacBenchmark extends ScalacBenchmark {
  @Benchmark
  def compile(): Unit = compileImpl()
}

@BenchmarkMode(Array(SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1, time = 30, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G"))
class WarmScalacBenchmark extends ScalacBenchmark {
  @Benchmark
  def compile(): Unit = compileImpl()
}

@BenchmarkMode(Array(SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G"))
class HotScalacBenchmark extends ScalacBenchmark {
  @Benchmark
  def compile(): Unit = compileImpl()
}
