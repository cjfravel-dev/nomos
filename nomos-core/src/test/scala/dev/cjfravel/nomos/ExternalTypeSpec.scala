package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig, TemplateSerializer}
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

class ExternalTypeSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String) = parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "parser" should "parse $extern into an ExternalType" in {
    val d = parse("""{"name":"N","template":{"rules":["$extern:com.example.legacy.Rule"]}}""").value.definitions.head
    d.templateType.asInstanceOf[ObjectType].fields("rules").fieldType shouldBe ArrayType(ExternalType("com.example.legacy.Rule"))
  }

  "generator" should "emit the fully-qualified external type" in {
    val t = parse("""{"name":"N","template":{"rule":"$extern:com.example.legacy.Rule"}}""").value
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(t).value.find(_.fileName == "N.scala").get.content should include("rule: com.example.legacy.Rule")
  }

  "generator" should "not flag an external ref as unresolved" in {
    parse("""{"name":"N","template":{"rule":"$extern:com.example.legacy.Rule"}}""") shouldBe a[Right[_, _]]
  }

  "validator" should "treat an external type as opaque (accept any JSON)" in {
    val t = MultiTemplate("com.example", List(TemplateDefinition("N",
      ObjectType(ListMap("rule" -> FieldDef(ExternalType("com.example.legacy.Rule"), false))))))
    new MultiValidator(t).validate("""{"rule":{"anything":1}}""", "N") shouldBe a[Right[_, _]]
  }

  "serializer" should "round-trip an external type" in {
    TemplateSerializer.serializeTemplateType(ExternalType("com.x.Y")) shouldBe "ExternalType(\"com.x.Y\")"
  }
}
