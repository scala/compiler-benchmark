val jmhV = System.getProperty("jmh.version", "1.31")  // duplicated in build.sbt

libraryDependencies ++= List(
  "org.openjdk.jmh"     % "jmh-core"                 % jmhV,
  "org.openjdk.jmh"     % "jmh-generator-bytecode"   % jmhV,
  "org.openjdk.jmh"     % "jmh-generator-reflection" % jmhV
)

resolvers in Global += Resolver.mavenLocal
