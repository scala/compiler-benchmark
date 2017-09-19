package scala.tools.nsc

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.Mode._
import org.openjdk.jmh.annotations._

import scala.reflect.internal.util.BatchSourceFile

@BenchmarkMode(Array(Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 4)
@State(Scope.Thread)
class TreeTraverserBenchmark {
  var g: Global = _
  var tree: Global#Tree = _
  @Param(Array("../corpus/vector/latest/Vector.scala"))
  var file: String = _

  @Setup def setup(): Unit = {
    val settings = new Settings()
    settings.usejavacp.value = true
    settings.stopAfter.value = List("typer")
    val global = new Global(settings)
    g = global

    val run = new global.Run()
    val source = new BatchSourceFile(io.AbstractFile.getFile(file))
    run.compileSources(source :: Nil)
    tree = run.units.toList.head.body
    assert(tree.children.nonEmpty)
  }

  @Benchmark def measure(): Int = {
    var accum = 0
    tree.foreach(t => accum += System.identityHashCode(accum))
    accum
  }
}
