package dev.cjfravel.nomos.validation

import dev.cjfravel.nomos.json.JsonValue

/**
 * Registry of named custom validators. Applications register checks by name; each receives the
 * whole JSON value and returns errors. Templates reference them in their `validators` list, and
 * errors carry the validator name.
 */
object ValidatorRegistry {
  private val validators = scala.collection.concurrent.TrieMap.empty[String, JsonValue => List[ValidationError]]

  def register(name: String)(fn: JsonValue => List[ValidationError]): Unit =
    validators(name) = fn

  def isRegistered(name: String): Boolean = validators.contains(name)

  def run(name: String, json: JsonValue): List[ValidationError] =
    validators.get(name) match {
      case Some(fn) => fn(json).map(e => e.copy(message = s"[$name] ${e.message}"))
      case None => List(ValidationError("root", s"Unknown validator: $name", "registered validator", name))
    }

  def clear(): Unit = validators.clear()
}
