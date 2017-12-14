import sbt.complete.DefaultParsers.OptSpace
import sbt.complete.Parser

name := "compiler-benchmark"

version := "1.0-SNAPSHOT"

def scala211 = "2.11.11"
def dottyLatest = "0.2.0-RC1"
scalaVersion in ThisBuild := scala211

commands += Command.command("testAll") { s =>
  "test:compile" ::
    "compilation/test" ::
    "hot -psource=scalap -wi 1 -i 1 -f1" ::
    s"++$dottyLatest" ::
    "compilation/test" ::
    "hot -psource=vector -wi 1 -i 1 -f1" ::
    s"++$scala211" ::
    "micro/jmh:run -w1 -f1" ::
    s
}

resolvers += "scala-integration" at "https://scala-ci.typesafe.com/artifactory/scala-integration/"

// Convenient access to builds from PR validation
resolvers ++= (
  if (scalaVersion.value.endsWith("-SNAPSHOT"))
    List(
      "pr-scala snapshots" at "https://scala-ci.typesafe.com/artifactory/scala-pr-validation-snapshots/",
      Resolver.mavenLocal)
  else
    Nil
)

lazy val infrastructure = addJmh(project).settings(
  description := "Infrastrucuture to persist benchmark results annotated with metadata from Git",
  autoScalaLibrary := false,
  crossPaths := false,
  libraryDependencies ++= Seq(
    "org.influxdb" % "influxdb-java" % "2.5", // TODO update to 2.6 when released for fix for https://github.com/influxdata/influxdb-java/issues/269
    "org.eclipse.jgit" % "org.eclipse.jgit" % "4.6.0.201612231935-r",
    "com.google.guava" % "guava" % "21.0",
    "org.apache.commons" % "commons-lang3" % "3.5",
    "com.typesafe" % "config" % "1.3.1",
    "org.slf4j" % "slf4j-api" % "1.7.24",
    "org.slf4j" % "log4j-over-slf4j" % "1.7.24",  // for any java classes looking for this
    "ch.qos.logback" % "logback-classic" % "1.2.1"
  )
)

lazy val compilation = addJmh(project).settings(
  // We should be able to switch this project to a broad range of Scala versions for comparative
  // benchmarking. As such, this project should only depend on the high level `MainClass` compiler API.
  description := "Black box benchmark of the compiler",
  libraryDependencies += {
    if (isDotty.value) "ch.epfl.lamp" %% "dotty-compiler" % scalaVersion.value
    else scalaOrganization.value % "scala-compiler" % scalaVersion.value
  },
  crossScalaVersions := List(scala211, dottyLatest),
  unmanagedSourceDirectories.in(Compile) +=
    sourceDirectory.in(Compile).value / (if (isDotty.value) "dotc" else "scalac"),
  mainClass in (Jmh, run) := Some("scala.bench.ScalacBenchmarkRunner"),
  libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test,
  testOptions in Test += Tests.Argument(TestFrameworks.JUnit),
  fork in (Test, test) := true // jmh scoped tasks run with fork := true.
).settings(addJavaOptions).dependsOn(infrastructure)

lazy val micro = addJmh(project).settings(
  description := "Finer grained benchmarks of compiler internals",
  libraryDependencies += scalaOrganization.value % "scala-compiler" % scalaVersion.value
).settings(addJavaOptions).dependsOn(infrastructure)

lazy val jvm = addJmh(project).settings(
  description := "Pure Java benchmarks for demonstrating performance anomalies independent from the Scala language/library",
  autoScalaLibrary := false,
  crossPaths := false
).settings(addJavaOptions).dependsOn(infrastructure)

lazy val addJavaOptions = javaOptions ++= {
  def refOf(version: String) = {
    val HasSha = """.*(?:bin|pre)-([0-9a-f]{7,})(?:-.*)?""".r
    version match {
      case HasSha(sha) => sha
      case _ => "v" + version
    }
  }
  List(
    "-DscalaVersion=" + scalaVersion.value,
    "-DscalaRef=" + refOf(scalaVersion.value),
    "-Dsbt.launcher=" + (sys.props("java.class.path").split(java.io.File.pathSeparatorChar).find(_.contains("sbt-launch")).getOrElse(""))
  )
}

addCommandAlias("hot", "compilation/jmh:run HotScalacBenchmark -foe true")

addCommandAlias("cold", "compilation/jmh:run ColdScalacBenchmark -foe true")


def profParser(s: State): Parser[String] = {
  import Parser._
  token("prof" ~> OptSpace) flatMap { _ => matched(s.combinedParser)} map (_.trim)
}

commands += Command.arb(profParser)((s: State, line: String) => {
  val flameGraphOpts = s"--minwidth,1,--colors,java,--cp,--width,1800"
  abstract class Profiler(val name: String) {
    def command(outDir: File): String
  }

  object basic extends Profiler("basic") {
    def command(outDir: File): String = "-jvmArgs -Xprof -prof hs_comp -prof hs_gc -prof stack -prof hs_rt"
  }
  object jfr extends Profiler("jfr") {
    def command(outDir: File): String = s"-prof jmh.extras.JFR:dir=${outDir.getAbsolutePath};flameGraphOpts=$flameGraphOpts;verbose=true'"
  }
  object async extends Profiler("async") {
    val framebuf = 33554432
    def command(outDir: File): String = s"-prof jmh.extras.Async:dir=${outDir.getAbsolutePath};flameGraphOpts=$flameGraphOpts;verbose=true;event=cpu;framebuf=$framebuf" // + ";simpleName=true" TODO add this after upgrading next sbt-jmh release
  }
  object perfNorm extends Profiler("perfNorm") {
    def command(outDir: File): String = "-prof perfnorm"
  }

  val profs = List(perfNorm, basic, async, jfr)
  val commands: List[String] = profs.flatMap { (prof: Profiler) =>
    val outDir = file(s"target/profile-${prof.name}")
    IO.createDirectory(outDir)
    List(line + " -jvmArgs -Dsun.reflect.inflationThreshold=0 " + prof.command(outDir) + s" -o ${(outDir / "jmh.log").getAbsolutePath} -rf json -rff ${(outDir / "result.json").getAbsolutePath}", BasicCommandStrings.FailureWall)
  }
  s.copy(remainingCommands = BasicCommandStrings.ClearOnFailure :: commands ++ s.remainingCommands)
})


def addJmh(project: Project): Project = {
  // IntelliJ SBT project import doesn't like sbt-jmh's default setup, which results the prod and test
  // output paths overlapping. This is because sbt-jmh declares the `jmh` config as extending `test`, but
  // configures `classDirectory in Jmh := classDirectory in Compile`.
  project.enablePlugins(JmhPlugin).overrideConfigs(config("jmh").extend(Compile))
}
