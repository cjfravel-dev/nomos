package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.{ParseError, TemplateParser}
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EscapedKeySpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String): Either[ParseError, MultiTemplate] =
    parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "parser" should "treat a backtick-quoted key as a literal field name" in {
    val d = parse("""{"name":"Guid","template":{"name":"string","`type`":"string"}}""").value.definitions.head
    val fields = d.templateType.asInstanceOf[ObjectType].fields
    fields.keySet should contain("type")
    fields.keySet should not contain "`type`"
  }

  "validator" should "validate a payload with the literal type key" in {
    val t = parse("""{"name":"Guid","template":{"name":"string","`type`":"string"}}""").value
    new MultiValidator(t).validate("""{"name":"Guid","type":"String"}""", "Guid") shouldBe a[Right[_, _]]
  }

  "generator" should "escape the reserved-keyword field name in generated code" in {
    val t = parse("""{"name":"Guid","template":{"name":"string","`type`":"string"}}""").value
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(t)
      .value
      .find(_.fileName == "Guid.scala")
      .get
      .content should include("`type`: String")
  }
}
