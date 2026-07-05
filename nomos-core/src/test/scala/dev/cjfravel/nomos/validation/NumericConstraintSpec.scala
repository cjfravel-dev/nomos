package dev.cjfravel.nomos.validation

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Numeric constraints (multipleOf/min/max) must be evaluated with exact decimal arithmetic; in Double, `0.3 % 0.1` is
 * nonzero, so a valid cent/tenth value would be falsely rejected. These specs pin exact-decimal behavior on the
 * precise-decimal use cases a data-contract library must get right.
 */
class NumericConstraintSpec extends AnyFlatSpec with Matchers {

  private def validatorFor(t: TemplateType): MultiValidator =
    new MultiValidator(
      MultiTemplate(
        "com.example",
        List(TemplateDefinition("M", ObjectType(ListMap("n" -> FieldDef(t, optional = false)))))))

  "MultiValidator" should "accept a decimal that is an exact multiple of a decimal multipleOf" in {
    val v = validatorFor(NumberType(List(MultipleOf(0.1))))
    v.validate("""{"n":0.3}""", "M") shouldBe a[Right[_, _]]
    v.validate("""{"n":0.7}""", "M") shouldBe a[Right[_, _]]
  }

  it should "reject a decimal that is not a multiple" in {
    val v = validatorFor(NumberType(List(MultipleOf(0.1))))
    v.validate("""{"n":0.35}""", "M") shouldBe a[Left[_, _]]
  }

  it should "check multipleOf on cents exactly" in {
    val v = validatorFor(DecimalType(List(MultipleOf(0.01))))
    v.validate("""{"n":19.99}""", "M") shouldBe a[Right[_, _]]
    v.validate("""{"n":19.999}""", "M") shouldBe a[Left[_, _]]
  }

  it should "apply min/max exactly for decimals" in {
    val v = validatorFor(NumberType(List(Min(0.1), Max(0.3))))
    v.validate("""{"n":0.1}""", "M") shouldBe a[Right[_, _]]
    v.validate("""{"n":0.3}""", "M") shouldBe a[Right[_, _]]
    v.validate("""{"n":0.09}""", "M") shouldBe a[Left[_, _]]
    v.validate("""{"n":0.31}""", "M") shouldBe a[Left[_, _]]
  }
}
