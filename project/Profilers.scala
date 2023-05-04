package build

import sbt.Keys.scalaInstance

import java.io.File
import sbt._
import sbt.complete.DefaultParsers.OptSpace
import sbt.complete.Parser
import sbt.internal.inc.classpath.ClasspathUtil

import java.util.stream.Collectors

abstract class Profiler(val name: String) {
  def command(outDir: File): String
  val flameGraphOpts = s"--minwidth,1,--colors,java,--cp,--width,1800"
}
object Profiler {
  def all = List(basic, jfr, asyncAlloc, asyncCpu, asyncCpuAlloc, asyncWall, perfNorm)

  def commands = Profiler.all.map { prof => Command.arb(profParser("prof" + prof.toString.capitalize))(commandImpl(List(prof))) } :+
    Command.arb(profParser("prof"))(commandImpl(Profiler.all)) :+ jfr2flameCommand

  def profParser(name: String)(s: State): Parser[String] = {
    import Parser._
    token(name ~> OptSpace) flatMap { _ => matched(s.combinedParser)} map (_.trim)
  }

  def jfr2flameCommand = Command.command("jfr2flame") { state =>
    import java.nio.file._
    import scala.collection.JavaConverters._
    val jfrFiles: List[Path] = Files.walk(Paths.get(System.getProperty("user.dir"))).iterator.asScala.filter(x => x.toString.endsWith(".jfr")).toList
    for (jfrFile <- jfrFiles) {
      val converterJar = file(System.getenv("ASYNC_PROFILER_DIR")) / "build" / "converter.jar"
      val (_, si) = Project.extract(state).runTask(scalaInstance, state)
      val run = new Run(cp => ClasspathUtil.makeLoader(cp.map(_.toPath), si), trapExit = true)
      def jfr2flame(forward: Boolean, event: String): Unit = {
        val jfrFileNameString = jfrFile.toAbsolutePath.toString
        val flameFileName = jfrFileNameString.replaceAll(""".jfr$""", s"${if (event == "cpu") "" else "-" + event}-${if (forward) "forward" else "reverse"}.html")
        val directionOpts = if (forward) Nil else List("--reverse")
        val eventOpts = if (event == "cpu") Nil else List("--" + event)
        val flameOpts = List("--minwidth", "0.2")
        run.run("jfr2flame", converterJar :: Nil, eventOpts ++ flameOpts ++ directionOpts ++ List(jfrFileNameString, flameFileName), state.log).get
      }
      for (forward <- List(true, false)) {
        jfr2flame(forward, "cpu")
        jfr2flame(forward, "alloc")
      }
    }
    state
  }
  def jfr2flameParser(name: String)(s: State): Parser[String] = {
    import Parser._
    token("jfr2flame" ~> OptSpace) flatMap { _ => matched(s.combinedParser)} map (_.trim)
  }

  def commandImpl(profs: List[Profiler]) = (s: State, line: String) => {
    val commands: List[String] = profs.flatMap { (prof: Profiler) =>
      val outDir = file(s"target/profile-${prof.name}")
      IO.createDirectory(outDir)
      List(line + " -jvmArgs -Dsun.reflect.inflationThreshold=0 " + prof.command(outDir) + s" -o ${(outDir / "jmh.log").getAbsolutePath} -rf json -rff ${(outDir / "result.json").getAbsolutePath}", BasicCommandStrings.FailureWall)
    }
    val remainingCommands1 = (BasicCommandStrings.ClearOnFailure :: commands).map(s => Exec(s, None)) ++ s.remainingCommands
    s.copy(remainingCommands = remainingCommands1)
  }
}

case object basic extends Profiler("basic") {
  def command(outDir: File): String = "-jvmArgs -Xprof -prof hs_comp -prof gc -prof stack -prof hs_rt -prof scala.tools.nsc.ThreadCpuTimeProfiler"
}
case object jfr extends Profiler("jfr") {
  def command(outDir: File): String = s"""-jvmArgs -XX:+UnlockCommercialFeatures -prof "jfr:dir=${outDir.getAbsolutePath};stackDepth=1024;postProcessor=scala.bench.JfrToFlamegraph;verbose=true" """
}
sealed abstract class async(event: String, secondaryAlloc: Boolean = false) extends Profiler("async-" + event + (if (secondaryAlloc) "-alloc" else "")) {
  require(event != "alloc" || !secondaryAlloc)
  def command(outDir: File): String = {
    val opts = collection.mutable.ListBuffer[String]()
    val doFlame = !secondaryAlloc
    opts ++= List(s"dir=${outDir.getAbsolutePath}", s"libPath=${System.getenv("ASYNC_PROFILER_DIR")}/build/libasyncProfiler.so", "verbose=true")
    opts += s"event=${event}"
    if (doFlame) {
      opts ++= List("output=flamegraph", "minwidth=1","width=1800", s"filter=${event == "wall"}")
    }
    if (secondaryAlloc) {
      opts += "alloc"
      opts += "output=jfr"
    } else {
      opts ++= List("flat=40", "traces=10", "output=text")
    }
    val r = s"""-prof "async:${opts.mkString(";")}" """
    println(r)
    r
  }
}
case object asyncCpu extends async("cpu")
case object asyncAlloc extends async("alloc")
case object asyncCpuAlloc extends async("cpu", true)
case object asyncWall extends async("wall")
case object perfNorm extends Profiler("perfNorm") {
  def command(outDir: File): String = "-prof perfnorm"
}
