# JMH benchmarks for the Scala Compiler

[![Join the chat at https://gitter.im/scala/compiler-benchmark](https://badges.gitter.im/scala/compiler-benchmark.svg)](https://gitter.im/scala/compiler-benchmark?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Goal: define a set of JMH benchmarks for the compiler to help drive performance
tuning and catch performance regressions.

Based on:
 - [OpenJDK JMH](http://openjdk.java.net/projects/code-tools/jmh/), the definitive Java benchmarking tool.
 - via [sbt-jmh](https://github.com/ktoso/sbt-jmh).

## Structure

| Project | Benchmark of|
| ------------- | ------------- |
| compilation  | The equivalent of `scalac ...`  |
| micro  | Finer grained parts of the compiler  |
| jvm | Pure Java benchmarks to demonstrate JVM quirks |
| infrastructure | Code to persist benchmark results and metadata |

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
      # Note: `extraArgs` accepts multiple `|`-separated compiler arguments
```

### Using aliases

Avoid the tedium of typing all that out with:

```
sbt> hot -psource=scalap 
sbt> cold -psource=better-files 
```

### Changing Scala Version

```
sbt> set scalaVersion in ThisBuild := "2.12.0-ab61fed-SNAPSHOT"
sbt> set scalaHome in ThisBuild := Some(file("/code/scala/build/pack"))
sbt> set scalaHome in compilation := "2.11.1" // if micro project isn't compatible with "2.11.1"
```

### Adding dependencies
List classpath entries for dependencies in a file `/path/to/corpus/deps.txt`.

### Persisting results

 - Provide `INFLUX_PASSWORD` as an environment variable
 - Replace `jmh/run` with `jmh:runMain scala.bench.UploadingRunner`

Results will be uploading into an [InfluxDB]() instance at `https://scala-ci.typesafe.com/influx/`. An quick introduction to InfluxDB is [here](https://github.com/scala/compiler-benchmark/wiki/InfluxDB-101).
 
These results will be plotted in our [Grafana dashboard](https://scala-ci.typesafe.com/grafana/dashboard/db/scala-benchmark) 

The [https://github.com/scala/compiler-benchq](scala/compiler-benchq) project triggers benchmarks
for merges and sets of commits that we're backtesting (UI on https://scala-ci.typesafe.com/benchq).

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
$ curl https://gist.githubusercontent.com/retronym/78d016a3f10c62da2fd47cacac867f25/raw/65d9a1e8458d5984784ecf411d6c4d257bfdf0c1/ArgsFile.scala >  ~/.sbt/0.13/plugins/ArgsFile.scala
$ cd /code/someProject
$ sbt core/compile:argsFilePrint
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

Place this output into a file. The full path to that file can be passed to the `-source=` option to run the benchmark
on that project: `-psource=@/path/to/args/file`
