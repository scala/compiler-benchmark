package scala.tools.nsc

import java.util.Random

import org.openjdk.jmh.annotations.{CompilerControl, _}
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Thread)
class BreakVsNonLocalReturnBenchmark {

  case class Foreach(size: Int) {
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    def foreach(as: Any => Unit) = {
      var i = 0
      while (i < size) {
        as("")
        i += 1
      }
    }
  }

  private[this] var fs: Array[Foreach] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val r = new Random()
    fs = scala.util.Random.shuffle(List.fill(16)(new Foreach(0)) ++ List.fill(16)(new Foreach(1))).toArray
  }

  @Benchmark
  def nonLocalReturn(bh: Blackhole): Unit = {
    def doIt(f: Foreach): Boolean = {
      f.foreach(i => return false)
      true
    }
    var i = 0
    while (i < fs.length) {
      bh.consume(doIt(fs(i)))
      i += 1
    }
  }

  @Benchmark
  def breakable(bh: Blackhole): Unit = {
    def doIt(f: Foreach): Boolean = {
      import scala.util.control.Breaks
      var result = true
      Breaks.breakable {
        f.foreach(i => {result = false; Breaks.break})
      }
      result
    }
    var i = 0
    while (i < fs.length) {
      bh.consume(doIt(fs(i)))
      i += 1
    }
  }
}
