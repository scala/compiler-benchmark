import JmhKeys._

name := """sbt-jmh-seed"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.6"

jmhSettings

outputTarget in Jmh := target.value / s"scala-${scalaBinaryVersion.value}"

libraryDependencies ++= Seq(
  // Add your own project dependencies in the form:
  "org.scala-lang" % "scala-compiler" % scalaVersion.value
)

// Start SBT with `sbt -J-XX:+UnlockCommercialFeatures` to unlock use of the
// Java Flight Recorder based profiler
//
// Enable with `sbt> run ... -prof jrf`
//
javaOptions in run ++= {
  import java.lang.management.ManagementFactory
  val jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments()
  val unlockCommercial = "-XX:+UnlockCommercialFeatures"
  if (jvmArgs.contains(unlockCommercial)) Seq(unlockCommercial) else Seq()
}

javaOptions in run ++= Seq(
  s"-Djmh.jfr.saveTo=${target.value}",
  "-XX:+UnlockDiagnosticVMOptions",
  "-XX:+DebugNonSafepoints"
)
