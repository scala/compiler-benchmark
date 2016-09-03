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
  libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.3"
)

commands += Command.args("script", "") { (state: State, args: Seq[String]) =>
  val outDir = target.value
  val outFile = outDir / "combined.csv"

  val versions = List("2.11.8", "2.12.0-M5")
  val benches = List(
    "ColdScalacBenchmark",
    "HotScalacBenchmark"
  )
  def params(bench: String) = List(("better-files", "-p source=better-files"))
  val commands = for {
    v <- versions
    b <- benches
    p <- params(b)
    c <- List(s"""set version := "$v" """, s"compilation/jmh:run -p _scalaVersion=$v $b ${args.mkString(" ")} ${p._2} -rf csv -rff ${outDir}/${p._1}-$b-$v.csv")
  } yield {
    c
  }

  val runUI = "ui/runMain scalajmhsuite.PlotData"
  val extraCommands = commands :+ runUI
  extraCommands ::: state
}
