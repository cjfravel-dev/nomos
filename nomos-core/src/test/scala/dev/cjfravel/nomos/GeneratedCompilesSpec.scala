package dev.cjfravel.nomos

import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratedFile, GeneratorConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * Compiles generated output in-process with the Scala compiler. String-only assertions on
 * generated source have let type errors (e.g. an unconfigurable temporal `parse`) slip through;
 * these tests actually compile the emitted code against nomos-runtime.
 */
class GeneratedCompilesSpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  private val parser = new TemplateParser()

  private def generate(template: String, cfg: GeneratorConfig): List[GeneratedFile] =
    new CodeGenerator(cfg).generateMulti(parser.parseMultiTemplate(template, "com.example").value).value

  "generated code with dateType = java.util.Date" should "compile" in {
    val tmpl = """{"definitions":[{"name":"N","template":{"d":"date","ts":"datetime"}}]}"""
    val files = generate(tmpl, GeneratorConfig("com.example", "target/test-gen",
      dateType = "java.util.Date", dateTimeType = "java.util.Date"))
    compileErrors(files) shouldBe empty
  }

  "generated code with default java.time date types" should "compile" in {
    val tmpl = """{"definitions":[{"name":"N","template":{"d":"date","ts":"datetime"}}]}"""
    compileErrors(generate(tmpl, GeneratorConfig("com.example", "target/test-gen"))) shouldBe empty
  }

  "generated code across object, enum, map, optional, nullable, and array shapes" should "compile" in {
    val tmpl =
      """{"definitions":[{"name":"N","template":{
        |"id":"string",
        |"count":"int",
        |"big":"decimal",
        |"flag":"boolean",
        |"tags":["string"],
        |"meta":{"$additionalProperties":"string"},
        |"open":{"$additionalProperties":true},
        |"opt":{"$optional":"string"},
        |"nul":{"$optional":"int","nullable":true},
        |"status":{"type":"string","as":"enumType","name":"Status","enum":["active","inactive"]}
        |}}]}""".stripMargin
    compileErrors(generate(tmpl, GeneratorConfig("com.example", "target/test-gen"))) shouldBe empty
  }

  "generated code for a prefix discriminator" should "compile" in {
    val tmpl =
      """{"definitions":[{"name":"Col","template":{"$type":{
        |"discriminator":"type","variantMatch":"prefix",
        |"variants":{"Decimal":{"scale":"int"},"Varchar":{}}
        |}}}]}""".stripMargin
    compileErrors(generate(tmpl, GeneratorConfig("com.example", "target/test-gen"))) shouldBe empty
  }

  "generated code with a $gen reference to another generated type" should "compile against that type's decode/encode" in {    // A stand-in for a nomos-generated type defined in another module: a companion with the
    // same decode/encode shape the generator emits.
    val ownerStub =
      """package com.other.models
        |import dev.cjfravel.nomos.json._
        |case class Owner(team: String)
        |object Owner {
        |  def decode(json: JsonValue): Either[String, Owner] = json match {
        |    case o: JsonObject => o.field("team") match {
        |      case Some(JsonString(s)) => Right(Owner(s))
        |      case _ => Left("team")
        |    }
        |    case _ => Left("expected object")
        |  }
        |  def encode(obj: Owner): JsonValue = JsonObject("team" -> JsonString(obj.team))
        |}
        |""".stripMargin
    val tmpl = """{"definitions":[{"name":"Holder","template":{"id":"string","owner":"$gen:com.other.models.Owner"}}]}"""
    val files = GeneratedFile("com/other/models/Owner.scala", ownerStub) :: generate(tmpl, GeneratorConfig("com.example", "target/test-gen"))
    compileErrors(files) shouldBe empty
  }

  "two definitions with the same simple name in different sub-packages, each referencing its own" should "compile" in {
    val tmpl =
      """{"definitions":[
        |{"name":"UpstreamMapping","subPackage":"lineage","template":{"src":"string"}},
        |{"name":"UpstreamMapping","subPackage":"sla","template":{"target":"int"}},
        |{"name":"LineageHolder","subPackage":"lineage","template":{"m":"$ref:UpstreamMapping"}},
        |{"name":"SlaHolder","subPackage":"sla","template":{"m":"$ref:UpstreamMapping"}}
        |]}""".stripMargin
    val files = generate(tmpl, GeneratorConfig("com.example", "target/test-gen"))
    compileErrors(files) shouldBe empty
  }

  "a variantNames discriminator with variantSubPackage (variants reference another type)" should "compile" in {
    val tmpl =
      """{"definitions":[
        |{"name":"Props","subPackage":"defs","template":{"limit":"int"}},
        |{"name":"Condition","template":{"$type":{
        |"discriminator":"type","variantSubPackage":"defs","commonFields":{"id":"string"},
        |"variantNames":{"threshold":"ThresholdCondition"},
        |"variants":{"threshold":{"props":"$ref:Props"}}}}}
        |]}""".stripMargin
    val files = generate(tmpl, GeneratorConfig("com.example", "target/test-gen"))
    compileErrors(files) shouldBe empty
  }

  "an inline-variants discriminator with variantSubPackage" should "compile" in {
    val tmpl =
      """{"definitions":[{"name":"Shape","subPackage":"shapes","template":{"$type":{
        |"discriminator":"kind","variantSubPackage":"kinds",
        |"variants":{"circle":{"radius":"number"},"square":{"side":"number"}}}}}]}""".stripMargin
    val files = generate(tmpl, GeneratorConfig("com.example", "target/test-gen"))
    compileErrors(files) shouldBe empty
  }
}
