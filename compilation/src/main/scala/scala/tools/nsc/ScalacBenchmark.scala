package scala.tools.nsc

import java.io.{File, IOException}
import java.net.URL
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations.Mode._
import org.openjdk.jmh.annotations._

import scala.collection.JavaConverters._
import scala.tools.benchmark.BenchmarkDriver

trait BaseBenchmarkDriver {
  def source: String
  def extraArgs: String
  def corpusVersion: String
  def depsClasspath: String
  def tempDir: File
  def corpusSourcePath: Path
  def compilerArgs: Array[String]
}

@State(Scope.Benchmark)
class ScalacBenchmark extends BenchmarkDriver {
  @Param(value = Array())
  var source: String = _

  @Param(value = Array(""))
  var extraArgs: String = _

  // This parameter is set by ScalacBenchmarkRunner / UploadingRunner based on the Scala version.
  // When running the benchmark directly the "latest" symlink is used.
  @Param(value = Array("latest"))
  var corpusVersion: String = _

  var depsClasspath: String = _

  def compileImpl(): Unit = {

    // MainClass is copy-pasted from compiler for source compatibility with 2.10.x - 2.13.x
    class MainClass extends Driver with EvalLoop {
      def resident(compiler: Global): Unit = loop { line =>
        val command = new CompilerCommand(line split "\\s+" toList, new Settings(scalacError))
        compiler.reporter.reset()
        new compiler.Run() compile command.files
      }

      override def newCompiler(): Global = Global(settings, reporter)

      override protected def processSettingsHook(): Boolean = {
        if (source == "scala")
          settings.sourcepath.value = Paths.get(s"../corpus/$source/$corpusVersion/library").toAbsolutePath.normalize.toString
        else
          settings.usejavacp.value = true
        settings.outdir.value = tempDir.getAbsolutePath
        settings.nowarn.value = true
        if (depsClasspath != null)
          settings.processArgumentString(s"-cp $depsClasspath")
        true
      }
    }
    val driver = new MainClass

    val extras = if (extraArgs != null && extraArgs != "") extraArgs.split('|').toList else Nil
    val allArgs = compilerArgs ++ extras ++ sourceFiles
    driver.process(allArgs.toArray)
    assert(!driver.reporter.hasErrors)
  }

  def compilerArgs: List[String] = if (source.startsWith("@")) List(source) else Nil

  def sourceFiles: List[String] =
    if (source.startsWith("@")) Nil
    else {
      import scala.collection.JavaConverters._
      val allFiles = Files.walk(findSourceDir, FileVisitOption.FOLLOW_LINKS).collect(Collectors.toList[Path]).asScala.toList
      val files = allFiles.filter(f => {
        val name = f.getFileName.toString
        name.endsWith(".scala") || name.endsWith(".java")
      }).map(_.toAbsolutePath.normalize.toString).toList
      files
    }

  var tempDir: File = null

  // Executed once per fork
  @Setup(Level.Trial) def initTemp(): Unit = {
    val tempFile = java.io.File.createTempFile("output", "")
    tempFile.delete()
    tempFile.mkdir()
    tempDir = tempFile
  }
  @TearDown(Level.Trial) def clearTemp(): Unit = {
    BenchmarkUtils.deleteRecursive(tempDir.toPath)
  }

  def corpusSourcePath: Path = Paths.get(s"../corpus/$source/$corpusVersion")

  @Setup(Level.Trial) def initDepsClasspath(): Unit = {
    val classPath = BenchmarkUtils.initDeps(corpusSourcePath)
    if (classPath.nonEmpty) {
      val res = new StringBuilder()
      for (depFile <- classPath) {
        if (res.nonEmpty) res.append(File.pathSeparator)
        res.append(depFile.toAbsolutePath.normalize.toString)
      }
      depsClasspath = res.toString
    }
  }

  private def findSourceDir: Path = {
    val path = corpusSourcePath
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
// @Fork triggers match error in dotty, see https://github.com/lampepfl/dotty/issues/2704
// Comment out `@Fork` to run compilation/test with Dotty or wait for that issue to be fixed.
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
