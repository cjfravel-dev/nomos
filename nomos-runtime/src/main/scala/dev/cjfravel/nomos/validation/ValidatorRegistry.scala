package dev.cjfravel.nomos.validation

/**
 * Registry of named custom validators. Applications register checks by name; each receives a
 * [[ValidatorContext]] (the node at its definition level, the whole document root, and the JSON
 * path) and returns errors. Templates reference them in their `validators` list, and errors carry
 * the validator name.
 */
object ValidatorRegistry {
  private val validators = scala.collection.concurrent.TrieMap.empty[String, ValidatorContext => List[ValidationError]]

  def register(name: String)(fn: ValidatorContext => List[ValidationError]): Unit =
    validators(name) = fn

  def isRegistered(name: String): Boolean = validators.contains(name)

  def run(name: String, context: ValidatorContext): List[ValidationError] =
    validators.get(name) match {
      case Some(fn) => fn(context).map(e => e.copy(message = s"[$name] ${e.message}"))
      case None => List(ValidationError(context.path, s"Unknown validator: $name", "registered validator", name))
    }

  def clear(): Unit = validators.clear()
}
