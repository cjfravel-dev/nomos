package dev.cjfravel.nomos

import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * A present JSON `null` must be accepted by validation for fields that decode treats leniently — optional, nullable, or
 * defaulted — rather than reported as a type mismatch. (Previously the validator had no null handling, so a nullable
 * field carrying `null` failed.)
 */
class NullableValidationSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val parser = new TemplateParser()
  private def validator(tmpl: String) =
    new MultiValidator(parser.parseMultiTemplate(tmpl, "com.example").value)

  "a present null" should "be accepted for an optional+nullable field" in {
    val v =
      validator("""{"definitions":[{"name":"N","template":{
      |"id":"string","default_value":{"$optional":"string","nullable":true}}}]}""".stripMargin)
    v.validate("""{"id":"a","default_value":null}""", "com.example.N") shouldBe a[Right[_, _]]
    v.validate("""{"id":"a","default_value":"x"}""", "com.example.N") shouldBe a[Right[_, _]]
    v.validate("""{"id":"a"}""", "com.example.N") shouldBe a[Right[_, _]]
  }

  it should "be accepted for a plain optional field and a defaulted field" in {
    val v =
      validator("""{"definitions":[{"name":"N","template":{
      |"id":"string","opt":{"$optional":"string"},"flag":{"type":"boolean","default":false}}}]}""".stripMargin)
    v.validate("""{"id":"a","opt":null,"flag":null}""", "com.example.N") shouldBe a[Right[_, _]]
  }

  it should "be accepted for nullable common and variant fields of a discriminated union" in {
    val v =
      validator("""{"definitions":[{"name":"U","template":{"$type":{"discriminator":"type",
      |"commonFields":{"note":{"$optional":"string","nullable":true}},
      |"variants":{"a":{"extra":{"$optional":"string","nullable":true}}}}}}]}""".stripMargin)
    v.validate("""{"type":"a","note":null,"extra":null}""", "com.example.U") shouldBe a[Right[_, _]]
  }

  "a present null" should "still be rejected for a required, non-nullable field" in {
    val v = validator("""{"definitions":[{"name":"N","template":{"id":"string"}}]}""")
    v.validate("""{"id":null}""", "com.example.N") shouldBe a[Left[_, _]]
  }
}
