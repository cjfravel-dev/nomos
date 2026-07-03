package dev.cjfravel.nomos.validation

/**
 * Registry of string format validators. Built-in formats are seeded; applications may
 * register additional named formats. A format predicate returns true when the value is valid.
 */
object FormatRegistry {
  private val formats = scala.collection.concurrent.TrieMap.empty[String, String => Boolean]

  def register(name: String)(predicate: String => Boolean): Unit = formats(name) = predicate

  def isRegistered(name: String): Boolean = formats.contains(name)

  /** True when the value satisfies the named format. Unknown formats are treated as valid. */
  def validate(name: String, value: String): Boolean =
    formats.get(name).forall(_(value))

  registerBuiltins()

  private def registerBuiltins(): Unit = {
    register("email")(_.matches("""^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"""))
    register("url")(_.matches("""^https?://.*"""))
    register("uuid")(_.matches("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"""))
    register("iso8601")(_.matches("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$"""))
    register("alphaNoWhitespace")(_.matches("""^[a-zA-Z]+$"""))
    register("majorAndMinor")(_.matches("""^[0-9]+\.[0-9]+$"""))
  }
}
