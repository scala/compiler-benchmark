package scala.tools.benchmark

import scala.tools.nsc.ScalacBenchmarkStandalone
import org.junit.Test

class BenchmarkTest {
  @Test def compilesOK =
    ScalacBenchmarkStandalone.main(Array("../corpus/vector", "1"))
}
