package scala.tools.nsc

import java.io.{IOException, PrintWriter}
import java.net.URL
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.io.Codec

object BenchmarkUtils {
  def prepareSources(sourceDir: Path, targetDir: Path, scalaVersion: String): List[String] = {
    val filterProcessor = new FilterExprProcessor(scalaVersion)

    val allFiles = Files.walk(sourceDir, FileVisitOption.FOLLOW_LINKS).collect(Collectors.toList[Path]).asScala.toList
    def isSource(f: Path) = {
      val name = f.getFileName.toString
      name.endsWith(".scala") || name.endsWith(".java")
    }

    allFiles collect {
      case f if isSource(f) =>
        val targetFile = targetDir.resolve(sourceDir.relativize(f))
        Files.createDirectories(targetFile.getParent)
        val w = new PrintWriter(targetFile.toFile)
        val codec = new Codec(java.nio.charset.Charset.forName("UTF-8"))
        Source.fromFile(f.toFile)(codec).getLines().foreach(line => {
          val t = line.trim
          if (t.startsWith("//#")) filterProcessor(t)
          else if (filterProcessor.on) w.println(line)
        })
        w.close()
        targetFile.toAbsolutePath.normalize.toString
    }
  }

  class FilterExprProcessor(scalaVersion: String) {
    var on = true

    // 0: no matching //#if yet
    // 1: matching //#if found
    private var state = 0

    def apply(trimmedLine: String): Unit = {
      val (cond, expr) = trimmedLine.split(" ").toList.filter(_.nonEmpty) match {
        case c :: e :: _ => (c, e)
        case c :: _ => (c, "")
      }
      def exprMatches = expr.split('|').exists(scalaVersion.startsWith)
      cond match {
        case "//#if" =>
          if (exprMatches && state == 0) {
            on = true
            state = 1
          } else {
            on = false
          }
        case "//#else" =>
          on = state == 0
        case "//#fi" =>
          on = true
          state = 0
      }
    }
  }

  def deleteRecursive(directory: Path): Unit = {
    if (Files.exists(directory)) {
      Files.walkFileTree(directory, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }
        override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
          Files.delete(dir)
          FileVisitResult.CONTINUE
        }
      })
    }
  }

  def initDeps(corpusSourcePath: Path): Seq[Path] = {
    val depsDir = Paths.get(ConfigFactory.load.getString("deps.localdir"))
    val depsFile = corpusSourcePath.resolve("deps.txt")
    val depsClasspath = new ListBuffer[Path]
    if (Files.exists(depsFile)) {
      for (depUrlString <- Files.lines(depsFile).iterator().asScala) {
        val depUrl = new URL(depUrlString)
        val filename = Paths.get(depUrl.getPath).getFileName.toString
        val depFile = depsDir.resolve(filename)
        // TODO: check hash if file exists, or after downloading
        if (!Files.exists(depFile)) {
          if (!Files.exists(depsDir)) Files.createDirectories(depsDir)
          val in = depUrl.openStream
          Files.copy(in, depFile, StandardCopyOption.REPLACE_EXISTING)
          in.close()
        }
        depsClasspath += depFile
      }
      depsClasspath.result()
    } else Nil
  }
}
