package scalajmhsuite

import java.io.{ByteArrayOutputStream, File}
import java.nio.file.{Files, Path, Paths}
import java.util.stream.{Collector, Collectors}

import scala.collection.JavaConverters._
import com.github.tototoshi.csv._

object PlotData {
  def main(args: Array[String]): Unit = {
    def read(path: Path): (List[String], List[Map[String, String]]) = {
      val reader = CSVReader.open(path.toFile)
      try {
        reader.allWithOrderedHeaders()
      } finally {
        reader.close()
      }
    }

    val files = Files.list(Paths.get("target")).collect(Collectors.toList[Path]).asScala.filter(_.toString.endsWith(".csv"))
    files.sorted.toList match {
      case all @ (head :: tail) =>
        val baos = new ByteArrayOutputStream()
        val writer = CSVWriter.open(baos)
        try {
          val header: List[String] = read(head)._1
          writer.writeRow(header)
          writer.writeAll(all.flatMap(file => read(file)._2.map((row: Map[String, String]) => header.map(key => row(key)))))
        } finally writer.close()
        val combined = Paths.get("target/aggregrate.csv")
        Files.write(combined, baos.toByteArray)
        println(s"Combined results: $combined")
      case Nil =>
    }
  }
}
