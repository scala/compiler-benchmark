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

### Using GraalVM

[Install](http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html) GraalVM for you operating system.

To run benchmarks on Linux:

```
sbt> compilation/jmh:run CompileSourcesBenchmark -jvm /path/to/graalvm/bin/java
```

And on OS X:
```
sbt> compilation/jmh:run CompileSourcesBenchmark -jvm /path/to/graalvm/Contents/Home/bin/java
```

To run only the open-source version of GraalVM add `-jvmArgs -Dgraal.CompilerConfiguration=core` to the command.

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

### Running the benchmark without SBT or JMH's forked processes

Perform an initial run with SBT with extra logging of the command line:

```
$ sbt 'compilation/jmh:run HotScalacBenchmark -foe true -psource=scala -bm ss -wi 4 -i 4 -vEXTRA -f0'
[info] -DscalaVersion=2.12.6 -DscalaRef=v2.12.6 -Dsbt.launcher=/usr/local/Cellar/sbt/1.1.6/libexec/bin/sbt-launch.jar -classpath /var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/job-1/target/e761655a/compilation_2.12-0.1.0-SNAPSHOT-jmh.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/f45a42bd/compilation_2.12-0.1.0-SNAPSHOT.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/bf49deb0/compilation_2.12-0.1.0-SNAPSHOT-tests.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/e2c6f222/infrastructure-0.1.0-SNAPSHOT.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/dd48b2f7/scala-compiler.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/6bd975dd/scala-library.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/f2c1ebc3/scala-reflect.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/e22de336/scala-xml_2.12-1.0.6.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/bc774206/sbt-jmh-extras-0.3.4.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/44244710/jmh-core-1.21.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/306816fb/jopt-simple-4.6.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/ec2544ab/commons-math3-3.2.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/6a52cbbd/jmh-generator-bytecode-1.21.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/ed5a2bdc/jmh-generator-reflection-1.21.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/670ffd88/jmh-generator-asm-1.21.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/dcc2193d/asm-5.0.3.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/ff3cebae/influxdb-java-2.5.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/3a3d111b/guava-21.0.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/2de7cd8b/retrofit-2.1.0.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/4b0bb6bf/okhttp-3.5.0.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/840897fc/okio-1.11.0.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/9cf9b317/converter-moshi-2.1.0.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/c27dff15/moshi-1.2.0.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/94c11f91/logging-interceptor-3.5.0.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/b705df6c/org.eclipse.jgit-4.6.0.201612231935-r.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/658b682d/jsch-0.1.53.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/94ad16d7/JavaEWAH-1.1.6.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/4c47155e/httpclient-4.3.6.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/f91b7a4a/httpcore-4.3.3.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/f6f66e96/commons-logging-1.1.3.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/b7f0fc8f/commons-codec-1.6.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/3f6b4bd4/slf4j-api-1.7.24.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/6c6c702c/commons-lang3-3.5.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/2cf7a6cc/config-1.3.1.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/6ab46c51/log4j-over-slf4j-1.7.24.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/706a8b82/logback-classic-1.2.1.jar:/var/folders/b7/xcc2k0ln6ldcv247ffpy2d1w0000gp/T/sbt_5a5e5313/target/0378913d/logback-core-1.2.1.jar scala.bench.ScalacBenchmarkRunner HotScalacBenchmark -foe true -psource=scala -bm ss -wi 0 -i 1 -vEXTRA -f0
```

Exit SBT, and run directly from the command line:

```
$ java -Xmx1G <command line from above>
```
