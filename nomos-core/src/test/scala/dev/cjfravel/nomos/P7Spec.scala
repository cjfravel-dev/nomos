package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

class P7Spec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String) = parser.parseMultiTemplate(json, "com.example").value
  def file(t: MultiTemplate, name: String, cfg: GeneratorConfig = GeneratorConfig("com.example", "target/test-gen")) =
    new CodeGenerator(cfg).generateMulti(t).value.find(_.fileName == name).get.content

  // ---- P7-1: boxed nullable numerics ----
  "a nullable numeric" should "generate a boxed Java type with null default (compiles)" in {
    val c = file(parse("""{"definitions":[{"name":"N","template":{
      "i":{"$optional":"int","nullable":true},
      "l":{"$optional":"long","nullable":true},
      "d":{"$optional":"number","nullable":true},
      "b":{"$optional":"boolean","nullable":true}
    }}]}"""), "N.scala")
    c should include("i: java.lang.Integer = null")
    c should include("l: java.lang.Long = null")
    c should include("d: java.lang.Double = null")
    c should include("b: java.lang.Boolean = null")
    c should not include "Int = null"
  }

  "a nullable reference type" should "stay a raw null without boxing" in {
    val c = file(parse("""{"definitions":[{"name":"N","template":{
      "tags":{"$optional":["string"],"nullable":true},
      "amount":{"$optional":"decimal","nullable":true}
    }}]}"""), "N.scala")
    c should include("tags: List[String] = null")
    c should include("amount: BigDecimal = null")
  }

  // ---- P7-2: throwing fromJson for unions ----
  val unionTmpl = """{"fromJsonStyle":"throwing","definitions":[{"name":"Shape","template":{"$type":{
    "discriminator":"kind","variants":{"circle":{"radius":"number"},"square":{"side":"number"}}}}}]}"""
  val unionNamesTmpl = """{"fromJsonStyle":"throwing","definitions":[{"name":"Shape","template":{"$type":{
    "discriminator":"kind","variantNames":{"circle":"Circle","square":"Square"},
    "variants":{"circle":{"radius":"number"},"square":{"side":"number"}}}}}]}"""

  "a throwing union" should "generate a throwing fromJson" in {
    val c = file(parse(unionTmpl), "Shape.scala", GeneratorConfig("com.example", "target/test-gen", throwingFromJson = true))
    c should include("def fromJson(json: String): Shape =")
    c should not include "def fromJson(json: String): Either"
  }

  "a throwing variantNames union" should "generate a throwing fromJson" in {
    val c = file(parse(unionNamesTmpl), "Shape.scala", GeneratorConfig("com.example", "target/test-gen", throwingFromJson = true))
    c should include("def fromJson(json: String): Shape =")
    c should not include "def fromJson(json: String): Either"
  }

  "a default union" should "still return Either" in {
    val c = file(parse("""{"definitions":[{"name":"Shape","template":{"$type":{
      "discriminator":"kind","variants":{"circle":{"radius":"number"}}}}}]}"""), "Shape.scala")
    c should include("def fromJson(json: String): Either[String, Shape]")
  }

  // ---- P7-3: map type ----
  "default map" should "be Map[String, T]" in {
    file(parse("""{"definitions":[{"name":"N","template":{"s":{"$map":"string"}}}]}"""), "N.scala") should include("s: Map[String, String]")
  }

  "the removed mapType setting" should "be rejected as an unknown top-level key" in {
    parser.parseMultiTemplate(
      """{"mapType":"java.util.Map","definitions":[{"name":"N","template":{"s":{"$map":"string"}}}]}""",
      "com.example") shouldBe a[Left[_, _]]
  }

  // ---- P7-4: double keyword ----
  "double keyword" should "parse and generate Double" in {
    val d = parse("""{"definitions":[{"name":"N","template":{"x":"double","y":{"type":"double","min":0}}}]}""").definitions.head
    val f = d.templateType.asInstanceOf[ObjectType].fields
    f("x").fieldType shouldBe NumberType()
    file(parse("""{"definitions":[{"name":"N","template":{"x":"double"}}]}"""), "N.scala") should include("x: Double")
  }
}
