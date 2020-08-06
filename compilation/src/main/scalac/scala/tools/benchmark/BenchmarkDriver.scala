package scala.tools.benchmark

import java.io.File
import java.nio.file._

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.profile.AsyncProfiler

import scala.tools.nsc._

trait BenchmarkDriver extends BaseBenchmarkDriver {
  private var residentDriver: ThreadLocal[MainClass] = new ThreadLocal[MainClass]()
  private var files: List[String] = _
  private def findScalaJars = {
    System.getProperty("scala.compiler.class.path") match {
      case null =>
        sys.props("java.class.path").split(File.pathSeparatorChar).filter(_.matches(""".*\bscala-(reflect|compiler|library).*\.jar""")).mkString(":")
      case s => s
    }
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
          settings.sourcepath.value = Paths.get(ConfigFactory.load.getString("sourceAssembly.localdir")).resolve("library").toAbsolutePath.normalize.toString
        else settings.classpath.value = findScalaJars
        if (depsClasspath != null && depsClasspath.nonEmpty) {
          settings.processArgumentString(s"-cp $depsClasspath")
          if (source != "scala")
            settings.classpath.value = findScalaJars + File.pathSeparator + settings.classpath.value
        }

      }

      settings.outdir.value = tempDir.getAbsolutePath
      settings.nowarn.value = true
      true
    }
  }

  def compileImpl(): Unit = {
    if (isResident) {
      if (residentDriver.get() == null) {
        val driver = new MainClass
        residentDriver.set(driver)
        driver.process(allArgs.toArray)
        val command  = new CompilerCommand(allArgs, driver.compiler.settings)
        files = command.files
      }
      val compiler = residentDriver.get().compiler
      compiler.reporter.reset()
      new compiler.Run() compile files
      assert(!residentDriver.get().reporter.hasErrors)
    } else {
      val driver = new MainClass
      driver.process(allArgs.toArray)
      assert(!driver.reporter.hasErrors)
    }
  }

}
