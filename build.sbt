name := """scala-jmh-suite"""

version := "1.0-SNAPSHOT"

scalaVersion in ThisBuild := "2.11.8"

// Convenient access to builds from PR validation
resolvers ++= (
  if (scalaVersion.value.endsWith("-SNAPSHOT"))
    List(
      "pr-scala snapshots old" at "http://private-repo.typesafe.com/typesafe/scala-pr-validation-snapshots/",
      "pr-scala snapshots" at "https://scala-ci.typesafe.com/artifactory/scala-pr-validation-snapshots/",
      Resolver.mavenLocal,
      Resolver.sonatypeRepo("snapshots")
    )
  else
    Nil
)

val compilation = project.enablePlugins(JmhPlugin).settings(
  // We should be able to switch this project to a broad range of Scala versions for comparative
  // benchmarking. As such, this project should only depend on the high level `MainClass` compiler API.
  description := "Black box benchmark of the compiler",
  libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
)

val micro = project.enablePlugins(JmhPlugin).settings(
  description := "Finer grained benchmarks of compiler internals",
  libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
)

val jvm = project.enablePlugins(JmhPlugin).settings(
  description := "Pure Java benchmarks for demonstrating performance anomalies independent from the Scala language/library",
  autoScalaLibrary := false
)

val ui = project.settings(
  scalaVersion := "2.11.8",
  libraryDependencies += "co.theasi" %% "plotly" % "0.1",
  libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.3"
)

commands += Command.args("script", "") { (state: State, args: Seq[String]) =>
  val versions = List("2.11.8", "2.12.0-M5")
  val benches = List(
    s"compilation/jmh:run ColdScalacBenchmark ${args.mkString(" ")} -p source=@/code/better-files/args.txt -rf csv -rff better-files-cold",
    s"compilation/jmh:run HotScalacBenchmark ${args.mkString(" ")} -f1 -p source=@/code/better-files/args.txt -rf csv -rff better-files-hot"
  )
  val runUI = "ui/runMain scalajmhsuite.PlotData"
  val extraCommands = versions.flatMap(v => s"""set scalaVersion in ThisBuild := "$v" """ +: benches.map(_ + s"-$v.csv")) :+ runUI
  extraCommands ::: state
}