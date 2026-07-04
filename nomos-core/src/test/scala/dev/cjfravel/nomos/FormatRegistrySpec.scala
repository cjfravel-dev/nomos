package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.validation.{MultiValidator, FormatRegistry}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.immutable.ListMap

class FormatRegistrySpec extends AnyFlatSpec with Matchers {

  def multi(fmt: String) = MultiTemplate("com.example", List(TemplateDefinition("N", ObjectType(ListMap(
    "code" -> FieldDef(StringType(List(Format(fmt))), false))))))

  "built-in formats" should "still validate" in {
    new MultiValidator(multi("email")).validate("""{"code":"a@b.com"}""", "N") shouldBe a[Right[_, _]]
    new MultiValidator(multi("email")).validate("""{"code":"nope"}""", "N") shouldBe a[Left[_, _]]
  }

  "registered custom format" should "be enforced" in {
    FormatRegistry.register("hexColor")(v => v.matches("^#[0-9a-fA-F]{6}$"))
    new MultiValidator(multi("hexColor")).validate("""{"code":"#ff8800"}""", "N") shouldBe a[Right[_, _]]
    new MultiValidator(multi("hexColor")).validate("""{"code":"red"}""", "N") shouldBe a[Left[_, _]]
  }

  "unknown format" should "pass through without error" in {
    new MultiValidator(multi("nonexistent")).validate("""{"code":"anything"}""", "N") shouldBe a[Right[_, _]]
  }

  "new built-in string formats" should "accept valid values and reject invalid ones" in {
    val cases = Seq(
      ("guid",              "12345678-1234-1234-1234-123456789abc", "12345678-1234-1234-1234-12345678"),
      ("guidUpper",         "12345678-1234-1234-1234-123456789ABC", "12345678-1234-1234-1234-123456789abc"),
      ("guidLower",         "12345678-1234-1234-1234-123456789abc", "12345678-1234-1234-1234-123456789ABC"),
      ("alpha",             "abcXYZ",                               "abc1"),
      ("alphanumeric",      "abc123",                               "abc-1"),
      ("alphaUpper",        "ABC",                                  "Abc"),
      ("alphaLower",        "abc",                                  "aBc"),
      ("alphanumericUpper", "ABC123",                               "abc123"),
      ("alphanumericLower", "abc123",                               "ABC123"),
      ("pascalCase",        "UserV2",                               "camelCase")
    )
    for ((fmt, ok, bad) <- cases) {
      withClue(s"$fmt should accept '$ok': ") { FormatRegistry.validate(fmt, ok) shouldBe true }
      withClue(s"$fmt should reject '$bad': ")  { FormatRegistry.validate(fmt, bad) shouldBe false }
    }
  }

  "guid" should "be an alias of uuid and enforce end-to-end" in {
    val g = "12345678-1234-1234-1234-123456789abc"
    new MultiValidator(multi("guid")).validate(s"""{"code":"$g"}""", "N") shouldBe a[Right[_, _]]
    new MultiValidator(multi("guid")).validate("""{"code":"nope"}""", "N") shouldBe a[Left[_, _]]
  }

  "empty string" should "not satisfy alpha/alphanumeric (one-or-more)" in {
    FormatRegistry.validate("alpha", "") shouldBe false
    FormatRegistry.validate("alphanumeric", "") shouldBe false
  }
}
