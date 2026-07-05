package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import dev.cjfravel.nomos.parser.TemplateParser
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NullableDiscriminatorSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def gen(t: String): String =
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(parser.parseMultiTemplate(t, "com.example").value)
      .value
      .find(_.fileName == "Event.scala")
      .get
      .content

  "a plain discriminator" should "honor nullable in common and variant fields" in {
    val content =
      gen("""{"definitions":[{"name":"Event","template":{"$type":{"discriminator":"kind",
      "commonFields":{"note":{"$optional":"string","nullable":true}},
      "variants":{"click":{"tags":{"$optional":["string"],"nullable":true}}}}}}]}""")
    content should include("note: String = null")
    content should include("tags: List[String] = null")
    content should not include "Option[String]"
    content should not include "Option[List[String]]"
  }

  "a variantNames discriminator" should "honor nullable in fields" in {
    val content =
      gen("""{"definitions":[{"name":"Event","template":{"$type":{"discriminator":"kind",
      "variantNames":{"click":"Click"},
      "commonFields":{"note":{"$optional":"string","nullable":true}},
      "variants":{"click":{"tags":{"$optional":["string"],"nullable":true}}}}}}]}""")
    content should include("note: String")
    content should not include "Option[String]"
    content should not include "Option[List[String]]"
  }
}
