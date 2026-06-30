package dev.cjfravel.nomos.validation

import com.fasterxml.jackson.databind.JsonNode

/**
 * Registry of named custom validators. Applications register checks by name; each receives the
 * whole JSON node and returns errors. Templates reference them in their `validators` list, and
 * errors carry the validator name.
 */
object ValidatorRegistry {
  private val validators = scala.collection.concurrent.TrieMap.empty[String, JsonNode => List[ValidationError]]

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
