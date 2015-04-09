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
