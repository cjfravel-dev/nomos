package dev.cjfravel.nomos

import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratedFile, GeneratorConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * For `includeDiscriminator: true` with exact matching, the discriminator value is fully
 * determined by the variant, so it is emitted as a fixed `override val` rather than a
 * constructor parameter: kept out of the constructor and `unapply`, matching idiomatic
 * hand-written sealed-trait models. These tests compile AND execute the generated code to prove
 * construction/pattern-match arity, the fixed value, and JSON round-trip.
 */
class FixedDiscriminatorSpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  private val parser = new TemplateParser()

  private def generate(template: String): List[GeneratedFile] =
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(parser.parseMultiTemplate(template, "com.example").value).value

  "an exact string-discriminator variant" should "put the discriminator out of the ctor/unapply as a fixed override" in {
    val tmpl = """{"definitions":[{"name":"Condition","template":{"$type":{
      |"discriminator":"type",
      |"variants":{"OtherColumnIsEqualTo":{"other_column":"string","is_equal_to":"string"}}}}}]}""".stripMargin
    val driver =
      """package com.example
        |object CondDriver {
        |  def run(): String = {
        |    // construct WITHOUT the discriminator (it is a fixed override, not a ctor param)
        |    val c = OtherColumnIsEqualTo("a", "b")
        |    val fixed = c.`type` == "OtherColumnIsEqualTo"
        |    // unapply has arity 2 (no discriminator)
        |    val matched = c match { case OtherColumnIsEqualTo(oc, ie) => oc + "," + ie }
        |    val json = Condition.toJson(c)
        |    val back = Condition.fromJson(json).toOption.get
        |    s"$fixed|$matched|$json|${back == c}"
        |  }
        |}
        |""".stripMargin
    val files = GeneratedFile("com/example/CondDriver.scala", driver) :: generate(tmpl)
    runDriver(files, "com.example.CondDriver") shouldBe
      """true|a,b|{"type":"OtherColumnIsEqualTo","other_column":"a","is_equal_to":"b"}|true"""
  }

  "an exact enum-discriminator variant (variantNames)" should "fix the enum value out of the ctor/unapply" in {
    val tmpl = """{"definitions":[{"name":"Location","template":{"$type":{
      |"discriminator":"type","discriminatorEnum":"LocationType",
      |"variantNames":{"DataLake":"DataLakeLocation","EventHub":"EventHubLocation"},
      |"variants":{"DataLake":{"name":"string"},"EventHub":{"name":"string"}}}}}]}""".stripMargin
    val driver =
      """package com.example
        |object LocDriver {
        |  def run(): String = {
        |    val loc = DataLakeLocation("mylake")            // no discriminator ctor arg
        |    val fixed = loc.`type` == LocationType.DataLake
        |    val matched = loc match { case DataLakeLocation(n) => n }
        |    val json = Location.toJson(loc)
        |    val back = Location.fromJson(json).toOption.get
        |    s"$fixed|$matched|$json|${back == loc}"
        |  }
        |}
        |""".stripMargin
    val files = GeneratedFile("com/example/LocDriver.scala", driver) :: generate(tmpl)
    runDriver(files, "com.example.LocDriver") shouldBe
      """true|mylake|{"type":"DataLake","name":"mylake"}|true"""
  }

  "a grouped class (several discriminator values -> one class)" should "keep the discriminator as a ctor field to round-trip which value it was" in {
    val tmpl = """{"definitions":[{"name":"U","template":{"$type":{
      |"discriminator":"type","variantNames":{"a":"Foo","b":"Foo"},
      |"variants":{"a":{"x":"string"},"b":{"x":"string"}}}}}]}""".stripMargin
    val driver =
      """package com.example
        |object UDriver {
        |  def run(): String = {
        |    val fa = U.fromJson("{\"type\":\"a\",\"x\":\"v\"}").toOption.get
        |    val fb = U.fromJson("{\"type\":\"b\",\"x\":\"v\"}").toOption.get
        |    // grouped class keeps the value, so both round-trip to their own discriminator
        |    s"${fa.`type`}|${fb.`type`}|${U.toJson(fa)}|${U.toJson(fb)}"
        |  }
        |}
        |""".stripMargin
    val files = GeneratedFile("com/example/UDriver.scala", driver) :: generate(tmpl)
    runDriver(files, "com.example.UDriver") shouldBe
      """a|b|{"type":"a","x":"v"}|{"type":"b","x":"v"}"""
  }

  "an exact discriminator with common fields" should "keep common+variant fields in the ctor and fix the discriminator" in {
    val tmpl = """{"definitions":[{"name":"Shape","template":{"$type":{
      |"discriminator":"shapeType","commonFields":{"color":"string"},
      |"variants":{"circle":{"radius":"number"}}}}}]}""".stripMargin
    val driver =
      """package com.example
        |object ShapeDriver {
        |  def run(): String = {
        |    val c = Circle("red", 2.0)                 // (color, radius) — no discriminator
        |    val fixed = c.shapeType == "circle"
        |    val matched = c match { case Circle(color, radius) => color + "," + radius }
        |    val json = Shape.toJson(c)
        |    s"$fixed|$matched|$json"
        |  }
        |}
        |""".stripMargin
    val files = GeneratedFile("com/example/ShapeDriver.scala", driver) :: generate(tmpl)
    runDriver(files, "com.example.ShapeDriver") shouldBe
      """true|red,2.0|{"shapeType":"circle","color":"red","radius":2.0}"""
  }
}
