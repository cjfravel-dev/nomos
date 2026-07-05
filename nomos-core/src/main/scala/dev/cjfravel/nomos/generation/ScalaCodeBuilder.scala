package dev.cjfravel.nomos.generation

/**
 * Helper class for building Scala source code with proper indentation
 */
class ScalaCodeBuilder(indentSize: Int = 2, visibility: String = "") {
  private val buffer = new StringBuilder()
  private var currentIndent = 0

  /**
   * Appends a line with current indentation
   */
  def line(text: String): ScalaCodeBuilder = {
    if (text.nonEmpty) {
      buffer.append("  " * currentIndent)
      buffer.append(text)
    }
    buffer.append("\n")
    this
  }

  /**
   * Appends an empty line
   */
  def emptyLine(): ScalaCodeBuilder = {
    buffer.append("\n")
    this
  }

  /**
   * Increases indentation level
   */
  def indent(): ScalaCodeBuilder = {
    currentIndent += 1
    this
  }

  /**
   * Decreases indentation level
   */
  def dedent(): ScalaCodeBuilder = {
    if (currentIndent > 0) {
      currentIndent -= 1
    }
    this
  }

  /**
   * Alias for dedent()
   */
  def outdent(): ScalaCodeBuilder = dedent()

  /**
   * Executes a block with increased indentation
   */
  def indented(block: => Unit): ScalaCodeBuilder = {
    indent()
    block
    dedent()
    this
  }

  /**
   * Appends a block with braces
   */
  def block(header: String)(body: => Unit): ScalaCodeBuilder = {
    line(s"$header {")
    indented(body)
    line("}")
    this
  }

  /**
   * Appends a case class definition
   */
  def caseClass(
      name: String,
      fields: List[(String, String)],
      parent: Option[String] = None,
      body: List[String] = Nil): ScalaCodeBuilder = {
    val extendsClause = parent.map(p => s" extends $p").getOrElse("")
    val open = if (body.nonEmpty) " {" else ""

    if (fields.isEmpty) {
      line(s"${visibility}case class $name()$extendsClause$open")
    } else if (fields.length == 1) {
      val (fieldName, fieldType) = fields.head
      line(s"${visibility}case class $name($fieldName: $fieldType)$extendsClause$open")
    } else {
      line(s"${visibility}case class $name(")
      indented {
        fields.zipWithIndex.foreach { case ((fieldName, fieldType), idx) =>
          val comma = if (idx < fields.length - 1) "," else ""
          line(s"$fieldName: $fieldType$comma")
        }
      }
      line(s")$extendsClause$open")
    }
    if (body.nonEmpty) {
      indented {
        body.foreach(m => m.split("\n").foreach(line))
      }
      line("}")
    }
    this
  }

  /**
   * Appends a case class definition with override support Fields are (name, type, isOverride)
   */
  def caseClassWithOverride(
      name: String,
      fields: List[(String, String, Boolean)],
      parent: Option[String] = None,
      body: List[String] = Nil): ScalaCodeBuilder = {
    val extendsClause = parent.map(p => s" extends $p").getOrElse("")
    val open = if (body.nonEmpty) " {" else ""

    if (fields.isEmpty) {
      line(s"${visibility}case class $name()$extendsClause$open")
    } else if (fields.length == 1) {
      val (fieldName, fieldType, isOverride) = fields.head
      val overrideKeyword = if (isOverride) "override " else ""
      line(s"${visibility}case class $name(${overrideKeyword}val $fieldName: $fieldType)$extendsClause$open")
    } else {
      line(s"${visibility}case class $name(")
      indented {
        fields.zipWithIndex.foreach { case ((fieldName, fieldType, isOverride), idx) =>
          val comma = if (idx < fields.length - 1) "," else ""
          val overrideKeyword = if (isOverride) "override " else ""
          line(s"${overrideKeyword}val $fieldName: $fieldType$comma")
        }
      }
      line(s")$extendsClause$open")
    }
    if (body.nonEmpty) {
      indented {
        body.foreach(m => m.split("\n").foreach(line))
      }
      line("}")
    }
    this
  }

  /**
   * Appends a sealed trait definition
   */
  def sealedTrait(name: String): ScalaCodeBuilder = {
    line(s"${visibility}sealed trait $name")
    this
  }

  /**
   * Appends a sealed trait definition with abstract val fields
   */
  def sealedTraitWithFields(name: String, fields: List[(String, String)]): ScalaCodeBuilder = {
    if (fields.isEmpty) {
      line(s"${visibility}sealed trait $name")
    } else {
      line(s"${visibility}sealed trait $name {")
      indented {
        fields.foreach { case (fieldName, fieldType) =>
          line(s"val $fieldName: $fieldType")
        }
      }
      line("}")
    }
    this
  }

  /**
   * Appends a companion object with the given body.
   */
  def companionObject(name: String)(body: => Unit): ScalaCodeBuilder = {
    line(s"${visibility}object $name {")
    indented(body)
    line("}")
    this
  }

  /**
   * Appends an implicit val declaration
   */
  def implicitVal(name: String, typeName: String, value: String): ScalaCodeBuilder = {
    line(s"implicit val $name: $typeName = $value")
    this
  }

  /**
   * Returns the built code as a string
   */
  def build(): String = buffer.toString()

  /**
   * Clears the buffer
   */
  def clear(): ScalaCodeBuilder = {
    buffer.clear()
    currentIndent = 0
    this
  }
}

object ScalaCodeBuilder {
  def apply(): ScalaCodeBuilder = new ScalaCodeBuilder()
  def apply(visibility: String): ScalaCodeBuilder = new ScalaCodeBuilder(visibility = visibility)

  /** Reserved Scala 2 keywords that must be backtick-escaped when used as identifiers. */
  val scalaKeywords: Set[String] =
    Set(
      "abstract",
      "case",
      "catch",
      "class",
      "def",
      "do",
      "else",
      "extends",
      "false",
      "final",
      "finally",
      "for",
      "forSome",
      "if",
      "implicit",
      "import",
      "lazy",
      "macro",
      "match",
      "new",
      "null",
      "object",
      "override",
      "package",
      "private",
      "protected",
      "return",
      "sealed",
      "super",
      "this",
      "throw",
      "trait",
      "true",
      "try",
      "type",
      "val",
      "var",
      "while",
      "with",
      "yield")

  /**
   * Escapes a field name if it's a Scala keyword
   */
  def escapeKeyword(name: String): String =
    if (scalaKeywords.contains(name)) {
      s"`$name`"
    } else {
      name
    }

  /**
   * Escapes a string so it is safe to embed inside a Scala double-quoted string literal. Handles backslash, double
   * quote, and the CR/LF/tab control characters. Backslash is escaped first so the other replacements are not
   * double-escaped.
   */
  def escapeStringLiteral(s: String): String =
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  /**
   * True when the name is a plain Scala identifier: a letter or underscore followed by letters, digits, or underscores.
   * Keyword-ness is handled separately by escapeKeyword.
   */
  def isSimpleIdentifier(name: String): Boolean =
    name.nonEmpty &&
      (name.head.isLetter || name.head == '_') &&
      name.forall(c => c.isLetterOrDigit || c == '_')

  /**
   * True when the name is a dot-separated sequence of simple identifiers (e.g. a package or fully-qualified type name).
   * Empty segments are rejected.
   */
  def isQualifiedName(name: String): Boolean =
    name.nonEmpty && name.split("\\.", -1).forall(isSimpleIdentifier)

  /**
   * Converts a name to camelCase
   */
  def toCamelCase(name: String): String = {
    val parts = name.split("[-_]")
    if (parts.length == 1) {
      name
    } else {
      parts.head + parts.tail.map(_.capitalize).mkString
    }
  }

  /**
   * Converts a name to PascalCase
   */
  def toPascalCase(name: String): String = {
    val parts = name.split("[-_]")
    parts.map(_.capitalize).mkString
  }
}
