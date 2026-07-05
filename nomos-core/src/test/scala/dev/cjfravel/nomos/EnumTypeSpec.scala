package dev.cjfravel.nomos

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig, TemplateSerializer}
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.{ParseError, TemplateParser}
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EnumTypeSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String): Either[ParseError, MultiTemplate] =
    parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  val tmpl =
    """{"name":"User","template":{"status":{"type":"string","enum":["active","inactive"],""" +
      """"as":"enumType","name":"Status"}}}"""

  "parser" should "produce an EnumType when as=enumType" in {
    val d = parse(tmpl).value.definitions.head
    d.templateType.asInstanceOf[ObjectType].fields("status").fieldType shouldBe EnumType(
      "Status",
      List("active", "inactive"))
  }

  "parser" should "keep enum as a String constraint when as is absent" in {
    val d = parse("""{"name":"U","template":{"s":{"type":"string","enum":["a","b"]}}}""").value.definitions.head
    d.templateType.asInstanceOf[ObjectType].fields("s").fieldType shouldBe StringType(List(Enum(List("a", "b"))))
  }

  "generator" should "type the field as the enum and emit a sealed trait file" in {
    val files =
      new CodeGenerator(GeneratorConfig("com.example", "target/test-gen")).generateMulti(parse(tmpl).value).value
    files.find(_.fileName == "User.scala").get.content should include("status: Status")
    val enumFile = files.find(_.fileName == "Status.scala").get.content
    enumFile should include("sealed trait Status")
    enumFile should include("case object Active extends Status")
    enumFile should include("case object Inactive extends Status")
    enumFile should include("def fromString")
    enumFile should include("def decode(json: JsonValue)")
    enumFile should include("def encode(v: Status): JsonValue")
  }

  "validator" should "accept enum values and reject others" in {
    val t =
      MultiTemplate(
        "com.example",
        List(
          TemplateDefinition(
            "User",
            ObjectType(ListMap("status" -> FieldDef(EnumType("Status", List("active", "inactive")), false))))))
    val v = new MultiValidator(t)
    v.validate("""{"status":"active"}""", "User") shouldBe a[Right[_, _]]
    v.validate("""{"status":"bogus"}""", "User") shouldBe a[Left[_, _]]
  }

  "serializer" should "round-trip an EnumType" in {
    TemplateSerializer.serializeTemplateType(EnumType("Status", List("active", "inactive"))) shouldBe
      "EnumType(\"Status\", List(\"active\", \"inactive\"))"
  }
}
