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
}
