package scala.tools.nsc

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.annotations.Mode._

@State(Scope.Benchmark)
@BenchmarkMode(Array(SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
class CompileSourcesBenchmark {

  @Param(value = Array("vector", "scalap"))
  var baseDir: String = null
  @Param(value = Array("", "typer"))
  var stopAfter: String = null
  var sources: List[Path] = null
  var driver: MainClass = null

  @Setup(Level.Trial)
  def setup {
    import scala.collection.JavaConverters._
    var path: Path = corpusBase
    val allFiles = Files.walk(path).collect(Collectors.toList[Path]).asScala.toList
    sources = allFiles.filter(_.getFileName.toString.endsWith(".scala"))
    driver = new scala.tools.nsc.MainClass {
      override protected def processSettingsHook(): Boolean = {
        settings.usejavacp.value = true
        settings.outdir.value = tempOutDir.getAbsolutePath
        settings.nowarn.value = true
        if (stopAfter != null && stopAfter != "")
          settings.stopAfter.value = stopAfter :: Nil
        true
      }
    }
    compile
  }

  @Benchmark
  def compile: Unit = {
    driver.process(sources.map(_.toAbsolutePath.toString).toArray)
    assert(!driver.reporter.hasErrors)
  }

  private def tempOutDir: File = {
    val tempFile = java.io.File.createTempFile("output", "")
    tempFile.delete()
    tempFile.mkdir()
    tempFile
  }
  private def corpusBase: Path = {
    if (baseDir.startsWith("/")) Paths.get(baseDir)
    else {
      var path = Paths.get("src/main/corpus/" + baseDir)
      if (!Files.exists(path)) path = Paths.get("compilerMacroBench/src/main/corpus/" + baseDir)
      path
    }
  }

}
