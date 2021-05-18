package scala.tools.nsc

import java.io._
import java.nio.file._
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations._

import scala.bench.IOUtils

@State(Scope.Benchmark)
@BenchmarkMode(Array(org.openjdk.jmh.annotations.Mode.SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
class HotSbtBenchmark {
  @Param(value = Array())
  var source: String = _

  @Param(value = Array(""))
  var extraArgs: String = _

  @Param(value = Array("0.13.15"))
  var sbtVersion: String = _

  @Param(value = Array(""))
  var extraBuild: String = _

  @Param(value= Array("compile"))
  var compileCmd: String = _

  // This parameter is set by ScalacBenchmarkRunner / UploadingRunner based on the Scala version.
  // When running the benchmark directly the "latest" symlink is used.
  @Param(value = Array("latest"))
  var corpusVersion: String = _

  @Param(value = Array())
  var scalaVersion: String = _

  var sbtProcess: Process = _
  var inputRedirect: ProcessBuilder.Redirect = _
  var outputRedirect: ProcessBuilder.Redirect = _
  var tempDir: Path = _
  var srcDir: Path = _
  var scalaHome: Path = _
  var processOutputReader: BufferedReader = _
  var processInputReader: BufferedWriter = _
  var output= new java.lang.StringBuilder()

  def compileRawSetting = if (sbtVersion.startsWith("1.")) "" else
    """
      |val compileRaw = taskKey[Unit]("Compile directly, bypassing the incremental compiler")
      |
      |def addCompileRaw = compileRaw := {
      |    val compiler = new sbt.compiler.RawCompiler(scalaInstance.value, sbt.ClasspathOptions.auto, streams.value.log)
      |    classDirectory.value.mkdirs()
      |    compiler.apply(sources.value, classDirectory.value +: dependencyClasspath.value.map(_.data), classDirectory.value, scalacOptions.value)
      |  }
      |
      |inConfig(Compile)(List(addCompileRaw))
    """.stripMargin

  def buildDef =
    s"""
       |scalaHome := Some(file("${scalaHome.toAbsolutePath.toString}"))
       |
       |val cleanClasses = taskKey[Unit]("clean the classes directory")
       |
       |cleanClasses := IO.delete((classDirectory in Compile).value)
       |
       |scalaSource in Compile := file("${srcDir.toAbsolutePath.toString}")
       |
       |libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
       |libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
       |
       |traceLevel := Int.MaxValue
       |traceLevel in runMain := Int.MaxValue
       |
       |$compileRawSetting
       |
       |$extraBuild
       |// TODO support .java sources
    """.stripMargin

  @Setup(Level.Trial) def spawn(): Unit = {
    tempDir = Files.createTempDirectory("sbt-")
    scalaHome = Files.createTempDirectory("scalaHome-")
    initDepsClasspath()
    srcDir = Files.createDirectory(tempDir.resolve("src"))
    println(srcDir)
    BenchmarkUtils.prepareSources(corpusSourcePath, srcDir, scalaVersion)
    Files.createDirectory(tempDir.resolve("project"))
    Files.write(tempDir.resolve("project/build.properties"), java.util.Arrays.asList("sbt.version=" + sbtVersion))
    Files.write(tempDir.resolve("build.sbt"), buildDef.getBytes("UTF-8"))

    val sbtLaucherPath = System.getProperty("sbt.launcher")
    if (sbtLaucherPath == null) sys.error("System property -Dsbt.launcher absent")
    val builder = new ProcessBuilder(sys.props("java.home") + "/bin/java", "-Xms2G", "-Xmx2G", "-Dsbt.log.format=false", "-jar", sbtLaucherPath)
    builder.directory(tempDir.toFile)
    inputRedirect = builder.redirectInput()
    outputRedirect = builder.redirectOutput()
    sbtProcess = builder.start()
    processOutputReader = new BufferedReader(new InputStreamReader(sbtProcess.getInputStream))
    processInputReader = new BufferedWriter(new OutputStreamWriter(sbtProcess.getOutputStream))
    awaitPrompt()
  }

  @Benchmark
  def compile(): Unit = {
    issue(s";cleanClasses;$compileCmd")
    awaitPrompt()
  }

  def issue(str: String) = {
    processInputReader.write(str + "\n")
    processInputReader.flush()
  }

  def awaitPrompt(): Unit = {
    output.setLength(0)
    var line = ""
    val buffer = new Array[Char](128)
    var read : Int = -1
    while (true) {
      read = processOutputReader.read(buffer)
      if (read == -1) sys.error("EOF: " + output.toString)
      else {
        output.append(buffer, 0, read)
        if (output.toString.contains("\n> ")) {
          if (output.toString.contains("[error")) sys.error(output.toString)
          return
        }
      }
    }

  }

  private def corpusSourcePath = Paths.get(s"../corpus/$source/$corpusVersion")

  def initDepsClasspath(): Unit = {
    val libDir = tempDir.resolve("lib")
    Files.createDirectories(libDir)
    for (depFile <- BenchmarkUtils.initDeps(corpusSourcePath)) {
      val libDirFile = libDir.resolve(depFile.getFileName)
      Files.copy(depFile, libDir)
    }

    val scalaHomeLibDir = scalaHome.resolve("lib")
    Files.createDirectories(scalaHomeLibDir)
    for (elem <- sys.props("java.class.path").split(File.pathSeparatorChar)) {
      val jarFile = Paths.get(elem)
      var name = jarFile.getFileName.toString
      if (name.startsWith("scala") && name.endsWith(".jar")) {
        if (name.startsWith("scala-library"))
          name = "scala-library.jar"
        else if (name.startsWith("scala-reflect"))
          name = "scala-reflect.jar"
        else if (name.startsWith("scala-compiler"))
          name = "scala-compiler.jar"
        Files.copy(jarFile, scalaHomeLibDir.resolve(name))
      }
    }

  }

  @TearDown(Level.Trial) def terminate(): Unit = {
    processOutputReader.close()
    sbtProcess.destroyForcibly()
    IOUtils.deleteRecursive(tempDir)
    IOUtils.deleteRecursive(scalaHome)
  }
}
