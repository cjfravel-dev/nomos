package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratedFile, GeneratorConfig}
import dev.cjfravel.nomos.model.TypeDiscriminator
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * A `fallbackVariant` turns an unrecognized discriminator value into a catch-all case class that preserves the raw
 * object and re-emits it verbatim on encode (forward compatibility), instead of failing. These tests compile AND
 * execute the generated decode/encode to prove the round-trip.
 */
class FallbackVariantSpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  private val parser = new TemplateParser()

  private def generate(template: String): List[GeneratedFile] =
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(parser.parseMultiTemplate(template, "com.example").value)
      .value

  private val shapeTmpl =
    """{"definitions":[{"name":"Shape","template":{"$type":{
      |"discriminator":"kind",
      |"fallbackVariant":"UnknownShape","variants":{"circle":{"radius":"int"}}}}}]}""".stripMargin

  "parser" should "capture fallbackVariant" in {
    val disc =
      parser
        .parseMultiTemplate(shapeTmpl, "com.example")
        .value
        .definitions
        .head
        .templateType
        .asInstanceOf[TypeDiscriminator]
    disc.fallbackVariant shouldBe Some("UnknownShape")
  }

  "an unknown variant (plain discriminator)" should "decode into the fallback and re-emit the raw payload" in {
    val driver =
      """package com.example
        |import dev.cjfravel.nomos.json._
        |object ShapeDriver {
        |  def run(): String = {
        |    val unknown = Json.parse("{\"kind\":\"hexagon\",\"sides\":6}").toOption.get
        |    Shape.decode(unknown) match {
        |      case Right(u: UnknownShape) => "ok:" + u.kind + ":" + Json.write(Shape.encode(u))
        |      case other => "unexpected:" + other
        |    }
        |  }
        |}
        |""".stripMargin
    val files = GeneratedFile("com/example/ShapeDriver.scala", driver) :: generate(shapeTmpl)
    runDriver(files, "com.example.ShapeDriver") shouldBe """ok:hexagon:{"kind":"hexagon","sides":6}"""
  }

  "an unknown variant (variantNames + common fields)" should
    "decode the common field and round-trip the raw payload" in {
      val tmpl =
        """{"definitions":[{"name":"Condition","template":{"$type":{
        |"discriminator":"type","fallbackVariant":"UnknownCondition","commonFields":{"id":"string"},
        |"variants":{"threshold":{"limit":"int"}},"variantNames":{"threshold":"ThresholdCondition"}}}}]}""".stripMargin
      val driver =
        """package com.example
        |import dev.cjfravel.nomos.json._
        |object CondDriver {
        |  def run(): String = {
        |    val unknown = Json.parse("{\"type\":\"velocity\",\"id\":\"c1\",\"speed\":9}").toOption.get
        |    Condition.decode(unknown) match {
        |      case Right(u: UnknownCondition) => "ok:" + u.id + ":" + Json.write(Condition.encode(u))
        |      case other => "unexpected:" + other
        |    }
        |  }
        |}
        |""".stripMargin
      val files = GeneratedFile("com/example/CondDriver.scala", driver) :: generate(tmpl)
      runDriver(files, "com.example.CondDriver") shouldBe """ok:c1:{"type":"velocity","id":"c1","speed":9}"""
    }

  "validation" should "accept an unknown variant when a fallback is configured and reject it otherwise" in {
    val withoutFb =
      """{"definitions":[{"name":"Shape","template":{"$type":{
        |"discriminator":"kind","variants":{"circle":{"radius":"int"}}}}}]}""".stripMargin
    val payload = dev.cjfravel.nomos.json.Json.parse("""{"kind":"hexagon","sides":6}""").value

    new MultiValidator(parser.parseMultiTemplate(shapeTmpl, "com.example").value)
      .validateJson(payload, "com.example.Shape") shouldBe Symbol("right")
    new MultiValidator(parser.parseMultiTemplate(withoutFb, "com.example").value)
      .validateJson(payload, "com.example.Shape") shouldBe Symbol("left")
  }
}
