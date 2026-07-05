package dev.cjfravel.nomos
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AdditionalPropertiesSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String) = parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "default" should "reject unknown keys" in {
    val t = parse("""{"name":"N","template":{"id":"string"}}""").value
    new MultiValidator(t).validate("""{"id":"a","extra":"x"}""", "N") shouldBe a[Left[_, _]]
  }

  "additionalProperties true" should "allow any extra keys" in {
    val t = parse("""{"name":"N","template":{"id":"string","$additionalProperties":true}}""").value
    new MultiValidator(t).validate("""{"id":"a","extra":1,"note":"k"}""", "N") shouldBe a[Right[_, _]]
  }

  "additionalProperties type" should "validate extras against a type" in {
    val t = parse("""{"name":"N","template":{"id":"string","$additionalProperties":"string"}}""").value
    val v = new MultiValidator(t)
    v.validate("""{"id":"a","extra":"ok"}""", "N") shouldBe a[Right[_, _]]
    v.validate("""{"id":"a","extra":5}""", "N") shouldBe a[Left[_, _]]
  }
}
