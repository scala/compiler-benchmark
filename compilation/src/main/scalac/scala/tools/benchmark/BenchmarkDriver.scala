package scala.tools.benchmark

import java.io.File
import java.nio.file._

import scala.tools.nsc._

trait BenchmarkDriver extends BaseBenchmarkDriver {
  private var driver: MainClass = _
  private var files: List[String] = _

  private def findScalaJars = {
    sys.props("java.class.path").split(File.pathSeparatorChar).filter(_.matches(""".*\bscala-(reflect|compiler|library).*\.jar""")).mkString(":")
  }

  // MainClass is copy-pasted from compiler for source compatibility with 2.10.x - 2.13.x
  private class MainClass extends Driver with EvalLoop {
    var compiler: Global = _
    override def newCompiler(): Global = {
      compiler = Global(settings, reporter)
      compiler
    }


    override protected def processSettingsHook(): Boolean = {
      if (!source.startsWith("@")) {
        // Don't set the classpath manually if it's to be loaded by the `@` processor
        if (source == "scala")
          settings.sourcepath.value = Paths.get(s"../corpus/$source/$corpusVersion/library").toAbsolutePath.normalize.toString
        else settings.classpath.value = findScalaJars
        if (depsClasspath != null && depsClasspath.nonEmpty)
          settings.processArgumentString(s"-cp $depsClasspath")
      }

      settings.outdir.value = tempDir.getAbsolutePath
      settings.nowarn.value = true
      true
    }
  }

  def compileImpl(): Unit = {
    if (isResident) {
      if (driver == null) {
        driver = new MainClass
        driver.process(allArgs.toArray)
        val command  = new CompilerCommand(allArgs, driver.compiler.settings)
        files = command.files
      } else {
        val compiler = driver.compiler
        compiler.reporter.reset()
        new compiler.Run() compile files
      }

    } else {
      driver = new MainClass
      driver.process(allArgs.toArray)
    }
    assert(!driver.reporter.hasErrors)
  }

}
