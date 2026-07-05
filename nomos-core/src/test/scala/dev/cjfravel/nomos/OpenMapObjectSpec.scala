package dev.cjfravel.nomos
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import dev.cjfravel.nomos.model.MultiTemplate
import dev.cjfravel.nomos.parser.{ParseError, TemplateParser}
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OpenMapObjectSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String): Either[ParseError, MultiTemplate] =
    parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "generator" should "emit Map[String, Any] for an open object (additionalProperties true)" in {
    val t = parse("""{"name":"N","template":{"extras":{"$additionalProperties":true}}}""").value
    val content =
      new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
        .generateMulti(t)
        .value
        .find(_.fileName == "N.scala")
        .get
        .content
    content should include("extras: Map[String, Any]")
    // The codec must build/read a Map, not fall through to Codecs.any / JsonNull.
    content should include("Codecs.map[Any]")
    content should not include """"extras" -> JsonNull"""
  }

  "generator" should "emit Map[String, T] for a typed open object" in {
    val t = parse("""{"name":"N","template":{"extras":{"$additionalProperties":"string"}}}""").value
    val content =
      new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
        .generateMulti(t)
        .value
        .find(_.fileName == "N.scala")
        .get
        .content
    content should include("extras: Map[String, String]")
    content should include("Codecs.map(Codecs.string)")
    content should not include """"extras" -> JsonNull"""
  }

  "validator" should "accept arbitrary keys for an open object" in {
    val t = parse("""{"name":"N","template":{"extras":{"$additionalProperties":true}}}""").value
    new MultiValidator(t).validate("""{"extras":{"a":1,"b":"x"}}""", "N") shouldBe a[Right[_, _]]
  }
}
