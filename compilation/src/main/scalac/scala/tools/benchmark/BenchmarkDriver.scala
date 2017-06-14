package scala.tools.benchmark

import java.nio.file._
import scala.tools.nsc._

trait BenchmarkDriver extends BaseBenchmarkDriver {
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
        if (extraArgs != null && extraArgs != "")
          settings.processArgumentString(extraArgs)
        true
      }
    }
    val driver = new MainClass
    driver.process(compilerArgs)
    assert(!driver.reporter.hasErrors)
  }
}