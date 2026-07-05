package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation._
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.{ParseError, TemplateParser}
import dev.cjfravel.nomos.serialization.AdapterRegistry
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AdapterSpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  val parser = new TemplateParser()
  def parse(json: String): Either[ParseError, MultiTemplate] =
    parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")
  private def generate(template: String): List[GeneratedFile] =
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(parser.parseMultiTemplate(template, "com.example").value)
      .value

  "parser" should "carry a per-field adapter name" in {
    val d =
      parse(
        """{"name":"N","template":{"createdAt":{"type":"string","adapter":"epochMillis"}}}""").value.definitions.head
    d.templateType.asInstanceOf[ObjectType].fields("createdAt").adapter shouldBe Some("epochMillis")
  }

  it should "reject an adapter on a non-string field" in {
    val err = parse("""{"name":"N","template":{"count":{"type":"int","adapter":"epochMillis"}}}""").left.value.toString
    err should include("adapter is only supported on string fields")
  }

  "serializer" should "round-trip a field adapter" in {
    TemplateSerializer.serializeFieldDef(FieldDef(StringType(), false, None, Some("epochMillis"))) should include(
      "adapter = Some")
  }

  "AdapterRegistry" should "transform values on encode/decode" in {
    AdapterRegistry.register("epochMillis")(decode = _.toLong.toString, encode = _.toString)
    AdapterRegistry.decode("epochMillis", "1704067200000") shouldBe "1704067200000"
    AdapterRegistry.encode("epochMillis", "5") shouldBe "5"
    AdapterRegistry.isRegistered("epochMillis") shouldBe true
  }

  "a field adapter" should "transform on decode and re-apply on encode in generated code" in {
    AdapterRegistry.register("wrap")(decode = _.stripPrefix("wire:"), encode = "wire:" + _)
    val tmpl = """{"definitions":[{"name":"N","template":{"createdAt":{"type":"string","adapter":"wrap"}}}]}"""
    val driver =
      """package com.example
        |import dev.cjfravel.nomos.json._
        |object AdapterDriver {
        |  def run(): String = {
        |    val in = Json.parse("{\"createdAt\":\"wire:hello\"}").toOption.get
        |    N.decode(in) match {
        |      case Right(n) => "ok:" + n.createdAt + ":" + Json.write(N.encode(n))
        |      case other => "unexpected:" + other
        |    }
        |  }
        |}
        |""".stripMargin
    val files = GeneratedFile("com/example/AdapterDriver.scala", driver) :: generate(tmpl)
    runDriver(files, "com.example.AdapterDriver") shouldBe """ok:hello:{"createdAt":"wire:hello"}"""
  }
}
