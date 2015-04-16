package scala.tools.nsc

import java.nio.charset.Charset
import java.nio.file.{Paths, FileSystems, Files, Path}
import java.util.function.Predicate
import java.util.stream.Collectors

import org.openjdk.jmh.annotations._

import scala.reflect.io.AbstractFile

@State(Scope.Benchmark)
class CompileSourcesBenchmark {

  @Param(value = Array("vector", "scalap"))
  var baseDir: String = null
  @Param(value = Array("", "typer"))
  var stopAfter: String = null
  var sources:List[Path] = null
  var compiler: Global = null

  @Setup(Level.Invocation)
  def setup {
    import scala.collection.JavaConverters._
    val allFiles = Files.walk(Paths.get("src/main/resources/" + baseDir)).collect(Collectors.toList[Path]).asScala.toList
    sources = allFiles.filter(_.getFileName.toString.endsWith(".scala"))

    val temp: Path = Files.createTempDirectory("JavacBenchmark")
    val settings = new Settings(sys.error)
    settings.usejavacp.value = true
    settings.outdir.value = "target"
    settings.nowarn.value = true
    if (stopAfter != "")
      settings.stopAfter.value = stopAfter :: Nil
    compiler = new Global(settings)
  }

  @Benchmark
  def compile: Boolean = {
    val settings = new Settings(sys.error)
    settings.usejavacp.value = true
    val compiler = this.compiler
    val run = new compiler.Run
    val files = sources.iterator.map(_.toFile).map(AbstractFile.getFile(_)).toList
    run.compileFiles(files)
    assert(!compiler.reporter.hasErrors)
    true
  }
}
