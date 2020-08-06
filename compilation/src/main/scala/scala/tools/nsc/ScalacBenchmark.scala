package scala.tools.nsc

import java.io.File
import java.nio.file._
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations.Mode._
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.profile.AsyncProfiler

import scala.tools.benchmark.BenchmarkDriver

trait BaseBenchmarkDriver {
  def source: String
  def extraArgs: String
  def extras: List[String] = if (extraArgs != null && extraArgs != "") extraArgs.split('|').toList else Nil
  def allArgs: List[String] = compilerArgs ++ extras ++ sourceFiles
  def corpusVersion: String
  def depsClasspath: String
  def tempDir: File
  def corpusSourcePath: Path
  def compilerArgs: List[String]
  def sourceFiles: List[String]
  def isResident: Boolean = false
  def compileProfiled(): Unit = {
    val profiler: AsyncProfiler.JavaApi = try {
      AsyncProfiler.JavaApi.getInstance()
    } catch {
      case _: LinkageError => null
    }
    if (profiler != null) profiler.filterThread(Thread.currentThread(), true)
    try {
      compileImpl()
    } finally {
      if (profiler != null) profiler.filterThread(Thread.currentThread(), false)
    }
  }
  def compileImpl(): Unit
}

@State(Scope.Benchmark)
class ScalacBenchmark extends BenchmarkDriver {
  @Param(value = Array())
  var source: String = _

  @Param(value = Array(""))
  var extraArgs: String = _

  @Param(value = Array("../corpus"))
  var corpusPath: String = "../corpus"

  // This parameter is set by ScalacBenchmarkRunner / UploadingRunner based on the Scala version.
  // When running the benchmark directly the "latest" symlink is used.
  @Param(value = Array("latest"))
  var corpusVersion: String = _

  @Param(value = Array("false"))
  var resident: Boolean = false

  @Param(value = Array())
  var scalaVersion: String = _

  override def isResident = resident

  var depsClasspath: String = _

  lazy val compilerArgs: List[String] = if (source.startsWith("@")) source :: Nil else Nil

  // lazy val so it's computed (and sources are copied) only once per JVM fork
  lazy val sourceFiles: List[String] =
    if (source.startsWith("@")) Nil
    else {
      val sourceDir = findSourceDir
      val sourceAssemblyDir = Paths.get(ConfigFactory.load.getString("sourceAssembly.localdir"))
      BenchmarkUtils.deleteRecursive(sourceAssemblyDir)
      BenchmarkUtils.prepareSources(sourceDir, sourceAssemblyDir, scalaVersion)
    }

  var tempDir: File = null

  // Executed once per fork
  @Setup(Level.Trial) def initTemp(): Unit = {
    val tempRootPath = ConfigFactory.load.getString("benchmark.outdir")
    val tempDirRoot = new java.io.File(tempRootPath)
    val tempFile = java.io.File.createTempFile("output", "", tempDirRoot)
    tempFile.delete()
    tempFile.mkdir()
    tempDir = tempFile
  }

  @TearDown(Level.Trial) def clearTemp(): Unit = {
    BenchmarkUtils.deleteRecursive(tempDir.toPath)
  }

  def corpusSourcePath: Path = Paths.get(s"$corpusPath/$source/$corpusVersion")

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
@BenchmarkMode(Array(org.openjdk.jmh.annotations.Mode.SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
// TODO -Xbatch reduces fork-to-fork variance, but incurs 5s -> 30s slowdown
@Fork(value = 16, jvmArgs = Array("-XX:CICompilerCount=2", "-Xms2G", "-Xmx2G", "-Xss2M"))
class ColdScalacBenchmark extends ScalacBenchmark {
  @Benchmark
  def compile(): Unit = compileProfiled()
}

@BenchmarkMode(Array(org.openjdk.jmh.annotations.Mode.SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1, time = 30, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-Xss2M"))
class WarmScalacBenchmark extends ScalacBenchmark {
  @Benchmark
  def compile(): Unit = compileProfiled()
}

@BenchmarkMode(Array(org.openjdk.jmh.annotations.Mode.SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-Xss2M"))
class HotScalacBenchmark extends ScalacBenchmark {
  @Benchmark
  def compile(): Unit = compileProfiled()
}
