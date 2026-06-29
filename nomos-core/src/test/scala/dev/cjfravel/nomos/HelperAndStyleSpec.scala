package dev.cjfravel.nomos

import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig, TemplateSerializer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

class HelperMethodsSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String) = parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  val tmpl = """{"name":"User","methods":["def codeName: String = \"USR\""],
    "template":{"id":"string","email":"string"}}"""

  "parser" should "read a definition's methods list" in {
    parse(tmpl).value.definitions.head.methods shouldBe List("def codeName: String = \"USR\"")
  }

  "generator" should "emit declared methods into the companion object" in {
    val content = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(parse(tmpl).value).value.find(_.fileName == "User.scala").get.content
    content should include("def codeName: String")
    content.indexOf("def codeName") should be > content.indexOf("object User")
  }

  "serializer" should "round-trip methods" in {
    val d = parse(tmpl).value.definitions.head
    TemplateSerializer.serializeDefinition(d) should include("methods = List(")
  }
}

class FromJsonStyleSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()

  "default" should "generate an Either-returning fromJson" in {
    val t = parser.parseMultiTemplate("""{"definitions":[{"name":"U","template":{"id":"string"}}]}""", "com.example").value
    val content = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(t).value.find(_.fileName == "U.scala").get.content
    content should include("def fromJson(json: String): Either[String, U]")
  }

  "throwing style" should "generate a throwing fromJson" in {
    val t = parser.parseMultiTemplate("""{"fromJsonStyle":"throwing","definitions":[{"name":"U","template":{"id":"string"}}]}""", "com.example").value
    t.fromJsonStyle shouldBe "throwing"
    val content = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen", throwingFromJson = true))
      .generateMulti(t).value.find(_.fileName == "U.scala").get.content
    content should include("def fromJson(json: String): U =")
    content should not include "Either[String, U]"
  }
}
