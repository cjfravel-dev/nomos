package dev.cjfravel.nomos

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig, TemplateSerializer}
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MapSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String) = parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "parser" should "parse $map into MapType" in {
    val d = parse("""{"name":"N","template":{"settings":{"$map":"string"}}}""").value.definitions.head
    d.templateType.asInstanceOf[ObjectType].fields("settings").fieldType shouldBe MapType(StringType())
  }

  def multi =
    MultiTemplate(
      "com.example",
      List(TemplateDefinition("N", ObjectType(ListMap("settings" -> FieldDef(MapType(StringType()), false))))))

  "generator" should "map $map to Map[String, T]" in {
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(multi)
      .value
      .find(_.fileName == "N.scala")
      .get
      .content should include("settings: Map[String, String]")
  }

  "validator" should "accept any keys and validate values, rejecting wrong value types" in {
    val v = new MultiValidator(multi)
    v.validate("""{"settings":{"theme":"dark","lang":"en"}}""", "N") shouldBe a[Right[_, _]]
    v.validate("""{"settings":{"theme":1}}""", "N") shouldBe a[Left[_, _]]
    v.validate("""{"settings":[]}""", "N") shouldBe a[Left[_, _]]
  }

  "serializer" should "round-trip MapType" in {
    TemplateSerializer.serializeTemplateType(MapType(StringType())) shouldBe "MapType(StringType(List()))"
  }
}
