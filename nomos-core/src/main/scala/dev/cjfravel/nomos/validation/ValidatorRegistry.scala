package dev.cjfravel.nomos.validation

import com.fasterxml.jackson.databind.JsonNode

/**
 * Registry of named custom validators. Applications register cross-field or conditional
 * rules by name; templates reference them in their `validators` list. Errors carry the name.
 */
object ValidatorRegistry {
  private val validators = scala.collection.mutable.Map.empty[String, JsonNode => List[ValidationError]]

  def register(name: String)(fn: JsonNode => List[ValidationError]): Unit =
    validators(name) = fn

  def isRegistered(name: String): Boolean = validators.contains(name)

  def run(name: String, json: JsonNode): List[ValidationError] =
    validators.get(name) match {
      case Some(fn) => fn(json).map(e => e.copy(message = s"[$name] ${e.message}"))
      case None => List(ValidationError("root", s"Unknown validator: $name", "registered validator", name))
    }

  def clear(): Unit = validators.clear()
}
