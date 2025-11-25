package dev.cjfravel.nomos.validation

/**
 * Represents a validation error with path information
 */
case class ValidationError(
  path: String,
  message: String,
  expected: String,
  actual: String
) {
  def fullMessage: String = s"$path: $message (expected: $expected, got: $actual)"
}

object ValidationError {
  def typeMismatch(path: String, expected: String, actual: String): ValidationError = {
    ValidationError(path, "Type mismatch", expected, actual)
  }

  def missingField(path: String, fieldName: String): ValidationError = {
    ValidationError(path, s"Missing required field '$fieldName'", fieldName, "missing")
  }

  def constraintViolation(path: String, constraint: String, value: String): ValidationError = {
    ValidationError(path, s"Constraint violation: $constraint", constraint, value)
  }

  def invalidDiscriminator(path: String, fieldName: String, expected: Set[String], actual: String): ValidationError = {
    ValidationError(
      path,
      s"Invalid discriminator value for field '$fieldName'",
      s"one of: ${expected.mkString(", ")}",
      actual
    )
  }

  def extraField(path: String, fieldName: String): ValidationError = {
    ValidationError(path, s"Extra field '$fieldName' not defined in template", "not present", fieldName)
  }
}