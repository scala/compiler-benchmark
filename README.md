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
sbt> set scalaVersion in compilation := "2.12.0-ab61fed-SNAPSHOT"
```

This version may be a local Scala version created in with a `scala/scala` checkout by
`sbt setupPublishCore publishLocal`. I can also be a pre-built binary from a pull request
(we have the custom resolvers configured in this build to find them.)


### Benchmarking compilation of a SBT sub-project

Install the `ArgsFile` plugin for SBT from source:
```
$ curl https://gist.githubusercontent.com/retronym/c0a5cd3ec72b75b1ddeb88a5a01840f4/raw/9e91fef3c4f4070337f0cb4b9f7b776947df2205/ArgsFile.scala>  ~/.sbt/1.0/plugins/ArgsFile.scala
```

In the test project, switch to a release Scala that is binary compatible with the version you want
to benchmark, and then export the compiler option files:

```
$ cd ~/code/monix
$ sbt clean '++2.13.1!' compile:argsFile test:argsFile
...
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-catnap/jvm/target/monix-catnap-compile.args
[info] Compiling 22 Scala sources to /Users/jz/code/monix/monix-catnap/jvm/target/scala-2.13/classes ...
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-eval/jvm/target/monix-eval-compile.args
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-tail/jvm/target/monix-tail-compile.args
[info] Compiling 58 Scala sources to /Users/jz/code/monix/monix-eval/jvm/target/scala-2.13/classes ...
[info] Compiling 73 Scala sources to /Users/jz/code/monix/monix-tail/jvm/target/scala-2.13/classes ...
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-eval/js/target/monix-eval-compile.args
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-tail/js/target/monix-tail-compile.args
[info] Compiling 58 Scala sources to /Users/jz/code/monix/monix-eval/js/target/scala-2.13/classes ...
[info] Compiling 73 Scala sources to /Users/jz/code/monix/monix-tail/js/target/scala-2.13/classes ...
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-java/target/monix-java-compile.args
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-reactive/jvm/target/monix-reactive-compile.args
[info] Compiling 2 Scala sources to /Users/jz/code/monix/monix-java/target/scala-2.13/classes ...
[info] Compiling 196 Scala sources and 1 Java source to /Users/jz/code/monix/monix-reactive/jvm/target/scala-2.13/classes ...
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-reactive/js/target/monix-reactive-compile.args
[info] Compiling 193 Scala sources to /Users/jz/code/monix/monix-reactive/js/target/scala-2.13/classes ...
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix/jvm/target/monix-compile.args
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix/js/target/monix-compile.args
[success] Total time: 52 s, completed 06/11/2019 5:00:34 PM
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-execution/js/target/monix-execution-test.args
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-execution/jvm/target/monix-execution-test.args
[info] Compiling 62 Scala sources to /Users/jz/code/monix/monix-execution/jvm/target/scala-2.13/test-classes ...
[info] Compiling 46 Scala sources to /Users/jz/code/monix/monix-execution/js/target/scala-2.13/test-classes ...
[info] Compiling 1 Scala source to /Users/jz/code/monix/monix/jvm/target/scala-2.13/classes ...
[info] Compiling 1 Scala source to /Users/jz/code/monix/monix/js/target/scala-2.13/classes ...
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-catnap/jvm/target/monix-catnap-test.args
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-eval/jvm/target/monix-eval-test.args
[info] Compiling 97 Scala sources to /Users/jz/code/monix/monix-eval/jvm/target/scala-2.13/test-classes ...
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-catnap/js/target/monix-catnap-test.args
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-eval/js/target/monix-eval-test.args
[info] Compiling 82 Scala sources to /Users/jz/code/monix/monix-eval/js/target/scala-2.13/test-classes ...
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-tail/jvm/target/monix-tail-test.args
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-reactive/jvm/target/monix-reactive-test.args
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-tail/js/target/monix-tail-test.args
[info] Wrote compiler comand line to: /Users/jz/code/monix/monix-reactive/js/target/monix-reactive-test.args
```

The full path to that file can be passed to the `-source=` option to run the benchmark
on that project.

```
$ sbt 'set scalaVersion in compilation := "2.13.2-bin-4c6c676-SNAPSHOT"' 'hot -psource=@/Users/jz/code/monix/monix/jvm/target/monix-compile.args'
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

#### Async Profiler

  - [Install](https://github.com/jvm-profiling-tools/async-profiler/releases) async-profiler 2.x.
  - export `ASYNC_PROFILER_DIR=/path/to/async-profiler` (the directory containing `build`)
  - Create a JFR profile and flamegraphs with CPU or allocation tracing: `{profAsyncCpu, profAsyncAlloc} hot -psource=scala -f1`
  - Create a combined JFR profile with CPU and allocation tracing: `profAsyncCpuAlloc hot -psource=scala -f1`
     - This can be converted to flamegraphs with the converter provided by async-profiler: `java -cp converter.jar jfr2flame`
       or by using the convenience sbt command `sbt jfr2flame`.

#### Java Flight Recorder

 - `profJfr hot -psource=scala -f1`

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
