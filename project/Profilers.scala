package build

import java.io.File
import sbt._
import sbt.complete.DefaultParsers.OptSpace
import sbt.complete.Parser

abstract class Profiler(val name: String) {
  def command(outDir: File): String
  val flameGraphOpts = s"--minwidth,1,--colors,java,--cp,--width,1800"
}
object Profiler {
  def all = List(basic, jfr, asyncAlloc, asyncCpu, perfNorm)

  def commands = Profiler.all.map { prof => Command.arb(profParser("prof" + prof.toString.capitalize))(commandImpl(List(prof))) } :+
    Command.arb(profParser("prof"))(commandImpl(Profiler.all))

  def profParser(name: String)(s: State): Parser[String] = {
    import Parser._
    token(name ~> OptSpace) flatMap { _ => matched(s.combinedParser)} map (_.trim)
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
  def command(outDir: File): String = s"-prof jmh.extras.JFR:dir=${outDir.getAbsolutePath};flameGraphOpts=$flameGraphOpts;verbose=true'"
}
sealed abstract class async(event: String) extends Profiler("async-" + event) {
  val framebuf = 33554432
  def command(outDir: File): String = {
    s"""-prof "jmh.extras.Async:dir=${outDir.getAbsolutePath};flameGraphOpts=$flameGraphOpts;verbose=true;event=$event;framebuf=${framebuf}" """
  } // + ";simplename=true" TODO add this after upgrading next sbt-jmh release
}
case object asyncCpu extends async("cpu")
case object asyncAlloc extends async("alloc")
case object perfNorm extends Profiler("perfNorm") {
  def command(outDir: File): String = "-prof perfnorm"
}