name := """sbt-jmh-seed"""

version := "1.0-SNAPSHOT"

scalaVersion in ThisBuild := "2.11.8"

val compilerMacroBench = project.enablePlugins(JmhPlugin).settings(
  libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
)

//javaOptions in (Jmh, run) in ThisBuild := {
//  import scala.collection.JavaConverters._
//  java.lang.management.ManagementFactory.getRuntimeMXBean.getInputArguments.asScala
//}
