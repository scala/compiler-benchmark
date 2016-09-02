package scalajmhsuite

import java.io.File
import com.github.tototoshi.csv._
import co.theasi.plotly._

object PlotData {
  def main(args: Array[String]): Unit = {
    // TODO unhardcode. (command line params? or trawl csv file names?)
    val sources = List("better-files")
    val versions = List("2.11.8", "2.12.0-M5")
    val bench = List("hot", "cold")

    def read(source: String, version: String, bench: String): List[Double] = {
      val reader = CSVReader.open(new File(s"compilation/$source-$bench-$version.csv"))
      try {
        reader.allWithHeaders().map(_.apply("Score").toDouble)
      } finally {
        reader.close()
      }
    }
    val data = for (s <- sources; b <- bench; v <- versions) yield (s, b, v, read(s, v, b))
    println(data.mkString("\n"))
    // TODO plots
    //    val xs = (0.0 to 2.0 by 0.1)
    //    val ys = xs.map { x => x*x }
    //    val draw1 = draw(plot, "my-first-plot", writer.FileOptions(overwrite=true))
    //    println(s"https://plot.ly/~${draw1.fileId.replaceAll(":", "/")}")
    //    println(s"https://plot.ly/~${draw1.fileId.replaceAll(":", "/")}.png")
  }
}
