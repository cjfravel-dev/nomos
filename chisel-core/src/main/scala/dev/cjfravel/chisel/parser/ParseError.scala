package dev.cjfravel.chisel.parser

/**
 * Errors that can occur during template parsing
 */
sealed trait ParseError {
  def message: String
  def path: String
}

object ParseError {
  /**
   * JSON syntax error
   */
  case class JsonSyntaxError(message: String, path: String = "") extends ParseError

  /**
   * Invalid type specification
   */
  case class InvalidType(typeName: String, path: String, details: String = "") extends ParseError {
    def message: String = {
      val detailMsg = if (details.nonEmpty) s": $details" else ""
      s"Invalid type '$typeName' at $path$detailMsg"
    }
  }

  /**
   * Missing required field
   */
  case class MissingField(fieldName: String, path: String) extends ParseError {
    def message: String = s"Missing required field '$fieldName' at $path"
  }

  /**
   * Invalid field value
   */
  case class InvalidFieldValue(fieldName: String, expected: String, actual: String, path: String) extends ParseError {
    def message: String = s"Invalid value for field '$fieldName' at $path: expected $expected, got $actual"
  }

  /**
   * Invalid discriminator definition
   */
  case class InvalidDiscriminator(details: String, path: String) extends ParseError {
    def message: String = s"Invalid discriminator at $path: $details"
  }

  /**
   * Invalid recursive reference
   */
  case class InvalidReference(refName: String, path: String, details: String = "") extends ParseError {
    def message: String = {
      val detailMsg = if (details.nonEmpty) s": $details" else ""
      s"Invalid reference '$refName' at $path$detailMsg"
    }
  }

  /**
   * Invalid constraint
   */
  case class InvalidConstraint(constraintName: String, path: String, details: String) extends ParseError {
    def message: String = s"Invalid constraint '$constraintName' at $path: $details"
  }

  /**
   * Template structure error
   */
  case class StructureError(details: String, path: String = "") extends ParseError {
    def message: String = s"Template structure error at $path: $details"
  }

  /**
   * Multiple errors accumulated during parsing
   */
  case class MultipleErrors(errors: List[ParseError]) extends ParseError {
    def message: String = errors.map(_.message).mkString("; ")
    def path: String = "<multiple>"
  }
}