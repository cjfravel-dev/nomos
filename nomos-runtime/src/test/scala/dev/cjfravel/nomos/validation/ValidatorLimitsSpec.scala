package dev.cjfravel.nomos.validation

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The validator recurses over the template with a depth budget so an adversarial/cyclic template (e.g. a reference
 * cycle that consumes no payload) fails with an error instead of a StackOverflowError, and constraint regexes are
 * compiled once and reused.
 */
class ValidatorLimitsSpec extends AnyFlatSpec with Matchers {

  "MultiValidator" should "fail with an error instead of StackOverflow on a reference cycle" in {
    val t =
      MultiTemplate(
        "com.example",
        List(TemplateDefinition("A", ReferenceType("B")), TemplateDefinition("B", ReferenceType("A"))))
    new MultiValidator(t).validate("""{"x":1}""", "A") shouldBe a[Left[_, _]]
  }

  "pattern validation" should "accept matching and reject non-matching values (compiled once, reused)" in {
    val t =
      MultiTemplate(
        "com.example",
        List(
          TemplateDefinition(
            "N",
            ObjectType(ListMap("code" -> FieldDef(StringType(List(Pattern("^[0-9]{3}$"))), optional = false))))))
    val v = new MultiValidator(t)
    v.validate("""{"code":"123"}""", "N") shouldBe a[Right[_, _]]
    v.validate("""{"code":"12"}""", "N") shouldBe a[Left[_, _]]
    v.validate("""{"code":"abc"}""", "N") shouldBe a[Left[_, _]]
    // Re-validate to exercise the cached compiled pattern.
    v.validate("""{"code":"999"}""", "N") shouldBe a[Right[_, _]]
  }
}
