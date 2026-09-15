package dev.cjfravel.nomos.validation

import dev.cjfravel.nomos.json.JsonValue

/** Validation entry points implemented by nomos-generated companions. */
trait GeneratedValidator {
  def validateStructure(json: JsonValue, path: String, depth: Int): List[ValidationError]
  def validateCustom(json: JsonValue, root: JsonValue, path: String, depth: Int): List[ValidationError]
  def reachesAnyValidator: Boolean
}
