package scala.tools.nsc

import java.nio.charset.Charset
import java.nio.file.{Files, Path}

import org.openjdk.jmh.annotations._

import scala.reflect.io.AbstractFile

@State(Scope.Thread)
class CompilerBenchmark {

  val source = "class Test { System.out.println(\"world\") }"
  var path: Path = null
  var compiler: Global = null

  @Setup
  def setup {
    val temp: Path = Files.createTempDirectory("JavacBenchmark")
    path = temp.resolve("Test.scala")
    val settings = new Settings(sys.error)
    settings.usejavacp.value = true
    compiler = new Global(settings)
    Files.write(path, source.getBytes(Charset.forName("UTF-8")))
  }

  @Benchmark
  def compile: Boolean = {
    val settings = new Settings(sys.error)
    settings.usejavacp.value = true
    val compiler = this.compiler
    val run = new compiler.Run
    run.compileFiles(AbstractFile.getFile(path.toFile) :: Nil)
    true
  }
}
