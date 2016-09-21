package scala.tools.nsc

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.annotations.Mode._

@State(Scope.Benchmark)
class ScalacBenchmark {
  @Param(value = Array[String]())
  var _scalaVersion: String = _
  @Param(value = Array[String]())
  var source: String = _
  @Param(value = Array(""))
  var extraArgs: String = _
  var driver: MainClass = _

  protected def compile(): Unit = {
     var compilerArgs =
      if (source.startsWith("@")) Array(source)
      else {
        import scala.collection.JavaConverters._
        val allFiles = Files.walk(findSourceDir).collect(Collectors.toList[Path]).asScala.toList
        allFiles.filter(_.getFileName.toString.endsWith(".scala")).map(_.toAbsolutePath.toString).toArray
      }
    val driver = new scala.tools.nsc.MainClass {
      override protected def processSettingsHook(): Boolean = {
        settings.usejavacp.value = true
        settings.outdir.value = tempOutDir.getAbsolutePath
        settings.nowarn.value = true
        if (extraArgs != null && extraArgs != "")
          settings.processArgumentString(extraArgs)
        true
      }
    }
    driver.process(compilerArgs)
    assert(!driver.reporter.hasErrors)
  }

  private def tempOutDir: File = {
    val tempFile = java.io.File.createTempFile("output", "")
    tempFile.delete()
    tempFile.mkdir()
    tempFile
  }
  private def findSourceDir: Path = {
    val path = Paths.get("../corpus/" + source)
    if (Files.exists(path)) path
    else Paths.get(source)
  }
}

@State(Scope.Benchmark)
@BenchmarkMode(Array(SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
// TODO -Xbatch reduces fork-to-fork variance, but incurs 5s -> 30s slowdown
@Fork(value = 16, jvmArgs = Array("-XX:CICompilerCount=2"))
class ColdScalacBenchmark extends ScalacBenchmark {
  @Benchmark
  override def compile(): Unit = super.compile()
}

@BenchmarkMode(Array(SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1, time = 30, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
class WarmScalacBenchmark extends ScalacBenchmark {
  @Benchmark
  override def compile(): Unit = super.compile()
}

@BenchmarkMode(Array(SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 6, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 12, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
class HotScalacBenchmark extends ScalacBenchmark {
  @Benchmark
  override def compile(): Unit = super.compile()
}
