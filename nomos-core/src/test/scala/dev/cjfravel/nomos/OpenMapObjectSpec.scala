package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

class OpenMapObjectSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String) = parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "generator" should "emit Map[String, Any] for an open object (additionalProperties true)" in {
    val t = parse("""{"name":"N","template":{"extras":{"$additionalProperties":true}}}""").value
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(t).value.find(_.fileName == "N.scala").get.content should include("extras: Map[String, Any]")
  }

  "generator" should "emit Map[String, T] for a typed open object" in {
    val t = parse("""{"name":"N","template":{"extras":{"$additionalProperties":"string"}}}""").value
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(t).value.find(_.fileName == "N.scala").get.content should include("extras: Map[String, String]")
  }

  "validator" should "accept arbitrary keys for an open object" in {
    val t = parse("""{"name":"N","template":{"extras":{"$additionalProperties":true}}}""").value
    new MultiValidator(t).validate("""{"extras":{"a":1,"b":"x"}}""", "N") shouldBe a[Right[_, _]]
  }
}
