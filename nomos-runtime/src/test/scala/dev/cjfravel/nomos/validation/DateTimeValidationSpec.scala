package dev.cjfravel.nomos.validation

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InstanceTemporalParser {
  def parse(_value: CharSequence): InstanceTemporalParser = this
}

/**
 * Datetime validation must accept exactly what the generated decoder accepts. With the default dateTimeType
 * (LocalDateTime) the decoder uses LocalDateTime.parse, which rejects a trailing-Z value, so validation must reject it
 * too (previously a flexible parser accepted it and validate() passed while fromJson() failed). With OffsetDateTime the
 * Z form is accepted by both.
 */
class DateTimeValidationSpec extends AnyFlatSpec with Matchers {

  private def validatorFor(dateTimeType: String): MultiValidator =
    new MultiValidator(
      MultiTemplate(
        "com.example",
        List(TemplateDefinition("E", ObjectType(ListMap("ts" -> FieldDef(DateTimeType(), optional = false))))),
        dateTimeType = dateTimeType))

  "MultiValidator" should "reject an unavailable configured temporal type" in {
    val error =
      intercept[IllegalArgumentException] {
        validatorFor("com.example.DoesNotExist")
      }

    error.getMessage should include("com.example.DoesNotExist")
    error.getMessage should include("parse(CharSequence)")
  }

  it should "reject a non-static temporal parse method" in {
    val error =
      intercept[IllegalArgumentException] {
        validatorFor(classOf[InstanceTemporalParser].getName)
      }

    error.getMessage should include("public static")
  }

  "datetime validation (default LocalDateTime)" should "accept a naive local datetime" in {
    validatorFor("java.time.LocalDateTime").validate("""{"ts":"2025-11-11T11:11:11"}""", "E") shouldBe a[Right[_, _]]
  }

  it should "reject a trailing-Z datetime, matching the decoder" in {
    validatorFor("java.time.LocalDateTime").validate("""{"ts":"2025-11-11T11:11:11Z"}""", "E") shouldBe a[Left[_, _]]
  }

  "datetime validation (OffsetDateTime)" should "accept a trailing-Z datetime" in {
    validatorFor("java.time.OffsetDateTime").validate("""{"ts":"2025-11-11T11:11:11Z"}""", "E") shouldBe a[Right[_, _]]
  }

  it should "reject a naive local datetime (no offset)" in {
    validatorFor("java.time.OffsetDateTime").validate("""{"ts":"2025-11-11T11:11:11"}""", "E") shouldBe a[Left[_, _]]
  }
}
