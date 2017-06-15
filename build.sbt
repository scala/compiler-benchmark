name := "compiler-benchmark"

version := "1.0-SNAPSHOT"

scalaVersion in ThisBuild := "2.11.8"

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

def addJmh(project: Project): Project = {
  // IntelliJ SBT project import doesn't like sbt-jmh's default setup, which results the prod and test
  // output paths overlapping. This is because sbt-jmh declares the `jmh` config as extending `test`, but
  // configures `classDirectory in Jmh := classDirectory in Compile`.
  project.enablePlugins(JmhPlugin).overrideConfigs(config("jmh").extend(Compile))
}
