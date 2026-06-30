package dev.cjfravel.nomos

import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

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
    content should not include "def fromJson(json: String): Either"
  }
}
