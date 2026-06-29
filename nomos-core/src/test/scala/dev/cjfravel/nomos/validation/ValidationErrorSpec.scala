package dev.cjfravel.nomos.validation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ValidationErrorSpec extends AnyFlatSpec with Matchers {

  "ValidationError" should "render a full message" in {
    ValidationError("p", "msg", "x", "y").fullMessage shouldBe "p: msg (expected: x, got: y)"
  }

  it should "provide factory helpers" in {
    ValidationError.typeMismatch("p", "string", "int").message shouldBe "Type mismatch"
    ValidationError.missingField("p", "id").message should include("id")
    ValidationError.constraintViolation("p", "min", "1").message should include("min")
    ValidationError.invalidDiscriminator("p", "t", Set("a", "b"), "c").expected should include("a")
    ValidationError.extraField("p", "x").actual shouldBe "x"
  }
}
