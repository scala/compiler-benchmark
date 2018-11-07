class Test {
	trait C; trait C1 extends C; trait C1A extends C1; trait C1B extends C1; trait C2 extends C; trait C2A extends C2; trait C2B extends C2; trait C12A extends C1A with C2A; class C12B extends C1B with C2B
	class D
	val x: Map[AnyRef, Seq[Class[_ <: C]]] = {
		Map(
			"" -> Seq(classOf[C1], classOf[C1A]),
			"" -> Seq(classOf[C1], classOf[C1A], classOf[C12B]),
			"" -> Seq(classOf[C1], classOf[C1A]),
			"" -> Seq(classOf[C1], classOf[C2A]),
			"" -> Seq(classOf[C2], classOf[C1]),
			"" -> Seq(classOf[C12A]),
			"" -> Seq(classOf[C2], classOf[C1], classOf[C12B])
		)
	}
}
