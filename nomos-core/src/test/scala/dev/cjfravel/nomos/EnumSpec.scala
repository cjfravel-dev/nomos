package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig, TemplateSerializer}
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

class EnumSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String) = parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "parser" should "parse a scalar string enum" in {
    val d = parse("""{"name":"N","template":{"priority":{"type":"string","enum":["low","high"]}}}""").value.definitions.head
    d.templateType.asInstanceOf[ObjectType].fields("priority").fieldType shouldBe StringType(List(Enum(List("low", "high"))))
  }

  it should "parse an array with enum-constrained items" in {
    val d = parse("""{"name":"N","template":{"colors":{"type":"array","items":"string","enum":["red","blue"]}}}""").value.definitions.head
    d.templateType.asInstanceOf[ObjectType].fields("colors").fieldType shouldBe ArrayType(StringType(List(Enum(List("red", "blue")))))
  }

  def multi = MultiTemplate("com.example", List(TemplateDefinition("N", ObjectType(ListMap(
    "p" -> FieldDef(StringType(List(Enum(List("low", "high")))), false))))))

  "validator" should "accept allowed values and reject others" in {
    val v = new MultiValidator(multi)
    v.validate("""{"p":"high"}""", "N") shouldBe a[Right[_, _]]
    v.validate("""{"p":"mid"}""", "N") shouldBe a[Left[_, _]]
  }

  "generator" should "keep String type for enum" in {
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(multi).value.find(_.fileName == "N.scala").get.content should include("p: String")
  }

  "serializer" should "round-trip enum" in {
    TemplateSerializer.serializeConstraint(Enum(List("a", "b"))) shouldBe """Enum(List("a", "b"))"""
  }
}
