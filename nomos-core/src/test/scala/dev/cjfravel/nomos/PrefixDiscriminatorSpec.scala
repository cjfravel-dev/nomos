package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

class PrefixDiscriminatorSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  val tmpl =
    """{"definitions":[{"name":"Col","template":{"$type":{"discriminator":"type","variantMatch":"prefix",
       "variants":{"Decimal":{"scale":"int"},"String":{}}}}}]}"""

  "parser" should "capture variantMatch=prefix" in {
    val disc = parser.parseMultiTemplate(tmpl, "com.example").value.definitions.head.templateType.asInstanceOf[TypeDiscriminator]
    disc.variantMatch shouldBe "prefix"
  }

  "validator" should "resolve a parameterized discriminator value by prefix" in {
    val v = new MultiValidator(parser.parseMultiTemplate(tmpl, "com.example").value)
    v.validate("""{"type":"Decimal(28,8)","scale":8}""", "Col") shouldBe a[Right[_, _]]
    v.validate("""{"type":"String"}""", "Col") shouldBe a[Right[_, _]]
    v.validate("""{"type":"Unknown(1)"}""", "Col") shouldBe a[Left[_, _]]
  }

  "exact match" should "remain the default" in {
    val exact = parser.parseMultiTemplate(
      """{"definitions":[{"name":"C","template":{"$type":{"discriminator":"t","variants":{"A":{}}}}}]}""", "com.example").value
    new MultiValidator(exact).validate("""{"t":"A(1)"}""", "C") shouldBe a[Left[_, _]]
  }
}
