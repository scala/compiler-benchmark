// Comment to get more information during initialization
logLevel := Level.Warn

// sbt-jmh plugin - pulls in JMH dependencies too
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.3")

// sbt-dotty plugin - to support `scalaVersion := "0.x"`
addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.4.2")
