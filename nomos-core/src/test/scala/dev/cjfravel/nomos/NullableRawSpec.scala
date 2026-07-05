package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig, TemplateSerializer}
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.{ParseError, TemplateParser}
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NullableRawSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String): Either[ParseError, MultiTemplate] =
    parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "parser" should "mark a field nullable" in {
    val d =
      parse("""{"name":"N","template":{"tags":{"$optional":["string"],"nullable":true}}}""").value.definitions.head
    val f = d.templateType.asInstanceOf[ObjectType].fields("tags")
    f.optional shouldBe true
    f.nullable shouldBe true
  }

  "generator" should "emit a raw nullable type with no Option wrapper" in {
    val t = parse("""{"name":"N","template":{"tags":{"$optional":["string"],"nullable":true}}}""").value
    val content =
      new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
        .generateMulti(t)
        .value
        .find(_.fileName == "N.scala")
        .get
        .content
    content should include("tags: List[String] = null")
    content should not include "Option[List[String]]"
  }

  "generator" should "still use Option for plain optional fields" in {
    val t = parse("""{"name":"N","template":{"tags":{"$optional":["string"]}}}""").value
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(t)
      .value
      .find(_.fileName == "N.scala")
      .get
      .content should include("Option[List[String]]")
  }

  "serializer" should "round-trip the nullable flag" in {
    TemplateSerializer.serializeFieldDef(FieldDef(StringType(), optional = true, nullable = true)) should include(
      "nullable = true")
  }
}
