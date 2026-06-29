package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig, TemplateSerializer}
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

class IntegerTypesSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String) = parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "parser" should "parse int/long/decimal scalar types" in {
    val d = parse("""{"name":"N","template":{"a":"int","b":"long","c":"decimal"}}""").value.definitions.head
    val f = d.templateType.asInstanceOf[ObjectType].fields
    f("a").fieldType shouldBe IntType()
    f("b").fieldType shouldBe LongType()
    f("c").fieldType shouldBe DecimalType()
  }

  it should "parse int with min constraint" in {
    val d = parse("""{"name":"N","template":{"depth":{"type":"int","min":1}}}""").value.definitions.head
    d.templateType.asInstanceOf[ObjectType].fields("depth").fieldType shouldBe IntType(List(Min(1)))
  }

  val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
  def multi = MultiTemplate("com.example", List(TemplateDefinition("N", ObjectType(ListMap(
    "a" -> FieldDef(IntType(), false), "b" -> FieldDef(LongType(), false), "c" -> FieldDef(DecimalType(), false))))))

  "generator" should "map int/long/decimal to Int/Long/BigDecimal" in {
    val content = gen.generateMulti(multi).value.find(_.fileName == "N.scala").get.content
    content should include("a: Int")
    content should include("b: Long")
    content should include("c: BigDecimal")
  }

  "validator" should "accept whole numbers and reject fractional for int/long" in {
    val v = new MultiValidator(multi)
    v.validate("""{"a":3,"b":9000000000,"c":12.5}""", "N") shouldBe a[Right[_, _]]
    v.validate("""{"a":1.5,"b":1,"c":1}""", "N") shouldBe a[Left[_, _]]
  }

  "serializer" should "round-trip int/long/decimal" in {
    TemplateSerializer.serializeTemplateType(IntType()) shouldBe "IntType(List())"
    TemplateSerializer.serializeTemplateType(LongType()) shouldBe "LongType(List())"
    TemplateSerializer.serializeTemplateType(DecimalType()) shouldBe "DecimalType(List())"
  }
}
