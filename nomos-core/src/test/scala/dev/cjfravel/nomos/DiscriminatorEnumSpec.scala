package dev.cjfravel.nomos

import dev.cjfravel.nomos.model.TypeDiscriminator
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratedFile, GeneratorConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * `discriminatorEnum` generates a sealed-trait enum over a union's discriminator values and types
 * the discriminator field (trait + every variant) as that enum, so call sites compare against
 * named constants (`x.kind == ShapeType.Circle`) instead of string literals. These tests compile
 * AND execute the generated code to prove the enum typing and the JSON round-trip.
 */
class DiscriminatorEnumSpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  private val parser = new TemplateParser()

  private def generate(template: String): List[GeneratedFile] =
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(parser.parseMultiTemplate(template, "com.example").value).value

  "parser" should "capture discriminatorEnum" in {
    val tmpl = """{"definitions":[{"name":"Shape","template":{"$type":{
      |"discriminator":"kind","discriminatorEnum":"ShapeType","variants":{"circle":{"radius":"int"}}}}}]}""".stripMargin
    val disc = parser.parseMultiTemplate(tmpl, "com.example").value
      .definitions.head.templateType.asInstanceOf[TypeDiscriminator]
    disc.discriminatorEnum shouldBe Some("ShapeType")
  }

  "a discriminatorEnum union (inline variants)" should "type the field as the enum and round-trip" in {
    val tmpl = """{"definitions":[{"name":"Shape","template":{"$type":{
      |"discriminator":"kind","discriminatorEnum":"ShapeType",
      |"variants":{"circle":{"radius":"int"},"square":{"side":"int"}}}}}]}""".stripMargin
    val driver =
      """package com.example
        |import dev.cjfravel.nomos.json._
        |object ShapeDriver {
        |  def run(): String = {
        |    val in = Json.parse("{\"kind\":\"circle\",\"radius\":3}").toOption.get
        |    Shape.decode(in) match {
        |      case Right(c: Circle) =>
        |        // the discriminator field is the enum, comparable to a named constant
        |        val typed: Boolean = (c.kind == ShapeType.Circle)
        |        "ok:" + typed + ":" + Json.write(Shape.encode(c))
        |      case other => "unexpected:" + other
        |    }
        |  }
        |}
        |""".stripMargin
    val files = GeneratedFile("com/example/ShapeDriver.scala", driver) :: generate(tmpl)
    runDriver(files, "com.example.ShapeDriver") shouldBe """ok:true:{"kind":"circle","radius":3}"""
  }

  "a discriminatorEnum union (variantNames + common fields)" should "type the trait val as the enum and round-trip" in {
    val tmpl = """{"definitions":[{"name":"Condition","template":{"$type":{
      |"discriminator":"type","discriminatorEnum":"ConditionType","commonFields":{"id":"string"},
      |"variantNames":{"threshold":"ThresholdCondition"},
      |"variants":{"threshold":{"limit":"int"},"window":{"seconds":"int"}}}}}]}""".stripMargin
    val driver =
      """package com.example
        |import dev.cjfravel.nomos.json._
        |object CondDriver {
        |  def run(): String = {
        |    val in = Json.parse("{\"type\":\"window\",\"id\":\"c1\",\"seconds\":30}").toOption.get
        |    Condition.decode(in) match {
        |      case Right(c) =>
        |        // discriminator typed as the enum on the trait
        |        val t: ConditionType = c.`type`
        |        "ok:" + (t == ConditionType.Window) + ":" + Json.write(Condition.encode(c))
        |      case other => "unexpected:" + other
        |    }
        |  }
        |}
        |""".stripMargin
    val files = GeneratedFile("com/example/CondDriver.scala", driver) :: generate(tmpl)
    runDriver(files, "com.example.CondDriver") shouldBe """ok:true:{"type":"window","id":"c1","seconds":30}"""
  }

  "discriminatorEnum" should "generate the enum file in the trait's package" in {
    val tmpl = """{"definitions":[{"name":"Shape","subPackage":"shapes","template":{"$type":{
      |"discriminator":"kind","discriminatorEnum":"ShapeType","variants":{"circle":{"radius":"int"}}}}}]}""".stripMargin
    val files = generate(tmpl)
    val enumFile = files.find(_.fileName == "ShapeType.scala")
    enumFile should not be empty
    enumFile.get.content should include("package com.example.shapes")
    enumFile.get.content should include("case object Circle extends ShapeType")
  }

  "validation" should "reject discriminatorEnum combined with fallbackVariant or prefix matching" in {
    val withFallback = """{"definitions":[{"name":"Shape","template":{"$type":{
      |"discriminator":"kind","discriminatorEnum":"ShapeType","fallbackVariant":"UnknownShape",
      |"variants":{"circle":{"radius":"int"}}}}}]}""".stripMargin
    parser.parseMultiTemplate(withFallback, "com.example").left.value.toString should include("incompatible with fallbackVariant")

    val withPrefix = """{"definitions":[{"name":"Shape","template":{"$type":{
      |"discriminator":"kind","discriminatorEnum":"ShapeType","variantMatch":"prefix",
      |"variants":{"circle":{"radius":"int"}}}}}]}""".stripMargin
    parser.parseMultiTemplate(withPrefix, "com.example").left.value.toString should include("incompatible with variantMatch")
  }
}
