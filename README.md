# JMH benchmarks for the Scala Compiler

Goal: define a set of JMH benchmarks for the compiler to help drive performance
tuning and catch performance regressions.

Based on:
 - [OpenJDK JMH](http://openjdk.java.net/projects/code-tools/jmh/), the definitive Java benchmarking tool.
 - via [sbt-jmh](https://github.com/ktoso/sbt-jmh).

## Structure

| Project | Bencmark of|
| ------------- | ------------- |
| compilation  | The equivalent of `scalac ...`  |
| micro  | Finer grained parts of the compiler  |
| jvm | Pure Java benchmarks to demonstrate JVM quirks |

## Recipes

### Learning about JMH options

```
sbt> compilation/jmh:run -help
```

### Benchmarking compiler performance
  - (optional) add new Scala sources (say `aardvark`) to a new directory in src/main/corpus

```
compilation/jmh:run (Cold|Warm|Hot)CompilationBenchmark 
   -p source=(<subdir of corpus>|/path/to/src/dir|@/path/to/argsfile)
   -p extraArgs=-nowarn
```

### Changing Scala Version

```
sbt> set scalaVersion in ThisBuild := "2.12.0-ab61fed-SNAPSHOT"
sbt> set scalaHome in ThisBuild := Some(file("/code/scala/build/pack"))
sbt> set scalaHome in compilation := "2.11.1" // if micro project isn't compatible with "2.11.1"
```

### Collecting profiling data

```
sbt> .../jmh:run Benchmark -prof jmh.extras.JFR // Java Flight Recorder
```

### Using Graal

[Install](http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html) Graal VM and JDK8 with [JVMCI](http://openjdk.java.net/jeps/243).

```
compilation/jmh:run CompileSourcesBenchmark 
    -jvm      /path/to/labsjdk1.8.0_92-jvmci-0.20/bin/java
    -jvmArgs -XX:+UnlockExperimentalVMOptions
    -jvmArgs -XX:+EnableJVMCI
    -jvmArgs -Djvmci.class.path.append=/path/to/graalvm-0.15-re/lib/jvmci/graal.jar
    -jvmArgs -Xbootclasspath/a:/path/to/graalvm-0.15-re/lib/truffle-api.jar
    -jvmArgs -Djvmci.Compiler=graal 
    -jvmArgs -XX:+UseJVMCICompiler 
```

### Exporting an args file from SBT

```
// ~/.sbt/0.13/plugins/SbtArgsFilePlugin.scala
package io.github.retronym

import sbt._
import Keys._

object SbtArgsFilePlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = sbt.plugins.JvmPlugin
  val argsFileContents = taskKey[String]("Contents file suitable for `scalac @args.txt`")
  val argsFilePrint = taskKey[Unit]("Show contents file suitable for `scalac @args.txt`")
  override lazy val projectSettings = List(Compile, Test).flatMap(c => inConfig(c)(Seq(
    argsFileContents := {
      (scalacOptions.value ++ sources.value).mkString("\n")
    },
    argsFilePrint := println(argsFileContents.value)
  )))
}

```

```
% sbt core/compile:argsFilePrint
[info] Set current project to root (in build file:/Users/jason/code/better-files/)
-deprecation
-encoding
UTF-8
-feature
-language:implicitConversions
-language:reflectiveCalls
-unchecked
-Xfatal-warnings
-Xlint
-Yinline-warnings
-Yno-adapted-args
-Ywarn-dead-code
-Xfuture
/Users/jason/code/better-files/core/src/main/scala/better/files/Cmds.scala
/Users/jason/code/better-files/core/src/main/scala/better/files/File.scala
/Users/jason/code/better-files/core/src/main/scala/better/files/Implicits.scala
/Users/jason/code/better-files/core/src/main/scala/better/files/package.scala
/Users/jason/code/better-files/core/src/main/scala/better/files/Scanner.scala
/Users/jason/code/better-files/core/src/main/scala/better/files/ThreadBackedFileMonitor.scala
[success] Total time: 0 s, completed 02/09/2016 11:51:58 AM
```
