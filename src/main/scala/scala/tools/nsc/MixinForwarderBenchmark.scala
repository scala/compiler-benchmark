package scala.tools.nsc

import java.util.Random

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import scala.annotation.meta.{field, getter}

@State(Scope.Thread)
class MixinForwarderBenchmark {
  class C {
    def foo = 42
  }
  class C_1 extends C
  class C_2 extends C
  class C_3 extends C
  class C_4 extends C
  class C_5 extends C
  class C_6 extends C
  class C_7 extends C
  class C_8 extends C
  class C_9 extends C
  class C_10 extends C
  class C_11 extends C
  class C_12 extends C
  class C_13 extends C
  class C_14 extends C
  class C_15 extends C
  class C_16 extends C
  trait T {
    def foo = 42
  }
  class T_1 extends T
  class T_2 extends T
  class T_3 extends T
  class T_4 extends T
  class T_5 extends T
  class T_6 extends T
  class T_7 extends T
  class T_8 extends T
  class T_9 extends T
  class T_10 extends T
  class T_11 extends T
  class T_12 extends T
  class T_13 extends T
  class T_14 extends T
  class T_15 extends T
  class T_16 extends T

  @(Param @field)(Array("16"))
  private[this] var count: Int = 0

  private[this] var zero: Int = 0
  
  private[this] val cs_16 = Array(new C_1, new C_2, new C_3, new C_4, new C_5, new C_6, new C_7, new C_8, new C_9, new C_10, new C_11, new C_12, new C_13, new C_14, new C_15, new C_16)
  private[this] val ts_16 = Array(new T_1, new T_2, new T_3, new T_4, new T_5, new T_6, new T_7, new T_8, new T_9, new T_10, new T_11, new T_12, new T_13, new T_14, new T_15, new T_16)
  import MixinForwarderBenchmarkSupport._
  private[this] val js_16 = Array[J](new J_1, new J_2, new J_3, new J_4, new J_5, new J_6, new J_7, new J_8, new J_9, new J_10, new J_11, new J_12, new J_13, new J_14, new J_15, new J_16)

  private[this] var cs_16_random: Array[C] = _
  private[this] var ts_16_random: Array[T] = _
  private[this] var js_16_random: Array[J] = _

  @Setup
  def create(): Unit = {
    val r = new Random()
    cs_16_random = Array.tabulate(count) {
      _ => cs_16.apply(r.nextInt(cs_16.length))
    }

    ts_16_random = Array.tabulate(count) {
      _ => ts_16.apply(r.nextInt(ts_16.length))
    }

    js_16_random = Array.tabulate(count) {
      _ => js_16.apply(r.nextInt(js_16.length))
    }
  }

  @Benchmark
  def baseClass_1(bh: Blackhole): Unit = {
    var i = 0
    val c = count
    while (i < c) {
      bh.consume(cs_16_random(zero))
      i += 1
    }
  }

  @Benchmark
  def baseClass_16(bh: Blackhole): Unit = {
    var i = 0
    val c = count
    while (i < c) {
      bh.consume(cs_16_random(i).foo)
      i += 1
    }
  }

  @Benchmark
  def baseTrait_1(bh: Blackhole): Unit = {
    var i = 0
    val c = count
    while (i < c) {
      bh.consume(ts_16_random(zero).foo)
      i += 1
    }
  }

  @Benchmark
  def baseTrait_16(bh: Blackhole): Unit = {
    var i = 0
    val c = count
    while (i < c) {
      bh.consume(ts_16_random(i).foo)
      i += 1
    }
  }

  @Benchmark
  def jbaseInterface_1(bh: Blackhole): Unit = {
    var i = 0
    val c = count
    while (i < c) {
      bh.consume(js_16_random(zero).foo)
      i += 1
    }
  }

  @Benchmark
  def jbaseInterface_16(bh: Blackhole): Unit = {
    var i = 0
    val c = count
    while (i < c) {
      bh.consume(js_16_random(i).foo)
      i += 1
    }
  }
}
