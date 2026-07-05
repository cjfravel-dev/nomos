package dev.cjfravel.nomos.validation

/**
 * Registry of string format validators. Built-in formats are seeded; applications may register additional named
 * formats. A format predicate returns true when the value is valid.
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
    val guidPattern = """^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"""
    val guidUpperPattern = """^[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$"""
    val guidLowerPattern = """^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"""
    register("email")(_.matches("""^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"""))
    register("url")(_.matches("""^https?://.*"""))
    register("uuid")(_.matches(guidPattern))
    register("guid")(_.matches(guidPattern))
    register("uuidUpper")(_.matches(guidUpperPattern))
    register("guidUpper")(_.matches(guidUpperPattern))
    register("uuidLower")(_.matches(guidLowerPattern))
    register("guidLower")(_.matches(guidLowerPattern))
    register("iso8601")(_.matches("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$"""))
    register("alphaNoWhitespace")(_.matches("""^[a-zA-Z]+$"""))
    register("alpha")(_.matches("""^[A-Za-z]+$"""))
    register("alphanumeric")(_.matches("""^[A-Za-z0-9]+$"""))
    register("alphaUpper")(_.matches("""^[A-Z]+$"""))
    register("alphaLower")(_.matches("""^[a-z]+$"""))
    register("alphanumericUpper")(_.matches("""^[A-Z0-9]+$"""))
    register("alphanumericLower")(_.matches("""^[a-z0-9]+$"""))
    register("majorAndMinor")(_.matches("""^[0-9]+\.[0-9]+$"""))
    register("pascalCase")(_.matches("""^[A-Z][A-Za-z0-9]*$"""))
    register("camelCase")(_.matches("""^[a-z][a-zA-Z0-9]*$"""))
    register("snakeCase")(_.matches("""^[a-z0-9]+(_[a-z0-9]+)*$"""))
    register("kebabCase")(_.matches("""^[a-z0-9]+(-[a-z0-9]+)*$"""))
    register("screamingSnakeCase")(_.matches("""^[A-Z0-9]+(_[A-Z0-9]+)*$"""))
  }
}
