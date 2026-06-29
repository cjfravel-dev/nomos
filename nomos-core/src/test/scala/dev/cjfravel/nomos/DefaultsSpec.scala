package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig, TemplateSerializer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

class DefaultsSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String) = parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "parser" should "parse boolean and string defaults" in {
    val d = parse("""{"name":"N","template":{"verbose":{"type":"boolean","default":false},"name":{"type":"string","default":"report"}}}""").value.definitions.head
    val f = d.templateType.asInstanceOf[ObjectType].fields
    f("verbose").default shouldBe Some("false")
    f("name").default shouldBe Some("\"report\"")
  }

  def multi = MultiTemplate("com.example", List(TemplateDefinition("N", ObjectType(ListMap(
    "verbose" -> FieldDef(BooleanType(), false, Some("false")),
    "name" -> FieldDef(StringType(), false))))))

  "generator" should "emit case-class default args" in {
    val content = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(multi).value.find(_.fileName == "N.scala").get.content
    content should include("verbose: Boolean = false")
  }

  "serializer" should "round-trip a default" in {
    TemplateSerializer.serializeFieldDef(FieldDef(BooleanType(), false, Some("false"))) should include("default = Some")
  }
}
