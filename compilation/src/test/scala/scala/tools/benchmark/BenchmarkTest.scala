package scala.tools.benchmark

import scala.tools.nsc.ScalacBenchmark
import org.junit.Test

class BenchmarkTest {
  @Test def compilesOK() = {
    val bench = new ScalacBenchmark
    bench.source = "../corpus/vector"
    bench.corpusVersion = "latest"
    bench.initTemp()
    bench.compileImpl()
    bench.clearTemp()
  }
}
