package dev.cjfravel.nomos.generation

/**
 * Helper class for building Scala source code with proper indentation
 */
class ScalaCodeBuilder(indentSize: Int = 2) {
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
  def caseClass(name: String, fields: List[(String, String)], parent: Option[String] = None): ScalaCodeBuilder = {
    val extendsClause = parent.map(p => s" extends $p").getOrElse("")
    
    if (fields.isEmpty) {
      line(s"case class $name()$extendsClause")
    } else if (fields.length == 1) {
      val (fieldName, fieldType) = fields.head
      line(s"case class $name($fieldName: $fieldType)$extendsClause")
    } else {
      line(s"case class $name(")
      indented {
        fields.zipWithIndex.foreach { case ((fieldName, fieldType), idx) =>
          val comma = if (idx < fields.length - 1) "," else ""
          line(s"$fieldName: $fieldType$comma")
        }
      }
      line(s")$extendsClause")
    }
    this
  }

  /**
   * Appends a case class definition with override support
   * Fields are (name, type, isOverride)
   */
  def caseClassWithOverride(name: String, fields: List[(String, String, Boolean)], parent: Option[String] = None): ScalaCodeBuilder = {
    val extendsClause = parent.map(p => s" extends $p").getOrElse("")
    
    if (fields.isEmpty) {
      line(s"case class $name()$extendsClause")
    } else if (fields.length == 1) {
      val (fieldName, fieldType, isOverride) = fields.head
      val overrideKeyword = if (isOverride) "override " else ""
      line(s"case class $name(${overrideKeyword}val $fieldName: $fieldType)$extendsClause")
    } else {
      line(s"case class $name(")
      indented {
        fields.zipWithIndex.foreach { case ((fieldName, fieldType, isOverride), idx) =>
          val comma = if (idx < fields.length - 1) "," else ""
          val overrideKeyword = if (isOverride) "override " else ""
          line(s"${overrideKeyword}val $fieldName: $fieldType$comma")
        }
      }
      line(s")$extendsClause")
    }
    this
  }

  /**
   * Appends a sealed trait definition
   */
  def sealedTrait(name: String): ScalaCodeBuilder = {
    line(s"sealed trait $name")
    this
  }

  /**
   * Appends a sealed trait definition with abstract val fields
   */
  def sealedTraitWithFields(name: String, fields: List[(String, String)]): ScalaCodeBuilder = {
    if (fields.isEmpty) {
      line(s"sealed trait $name")
    } else {
      line(s"sealed trait $name {")
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
   * Appends a companion object with json4s formats
   */
  def companionObject(name: String)(body: => Unit): ScalaCodeBuilder = {
    line(s"object $name {")
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
   * Adds custom Jackson-based serialization methods for a discriminated type
   */
  def customSerializer(
    traitName: String,
    discriminatorField: String,
    variants: Map[String, String] // discriminator value -> class name
  ): ScalaCodeBuilder = {
    line("import com.fasterxml.jackson.databind.JsonNode")
    emptyLine()
    line(s"def fromJson(json: String): Either[String, $traitName] = {")
    indent()
    line("try {")
    indent()
    line("val jsonNode = mapper.readTree(json)")
    line(s"""val discriminatorValue = jsonNode.get("$discriminatorField").asText()""")
    line("discriminatorValue match {")
    indent()
    variants.foreach { case (discriminatorValue, className) =>
      line(s"""case "$discriminatorValue" => Right(mapper.treeToValue(jsonNode, classOf[$className]))""")
    }
    line(s"""case other => Left(s"Unknown $discriminatorField value: $$other")""")
    outdent()
    line("}")
    outdent()
    line("} catch {")
    indent()
    line(s"""case e: Exception => Left(s"Failed to parse JSON: $${e.getMessage}")""")
    outdent()
    line("}")
    outdent()
    line("}")
    emptyLine()
    line(s"def toJson(obj: $traitName): String = {")
    indent()
    line("mapper.writeValueAsString(obj)")
    outdent()
    line("}")
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
  
  /**
   * Escapes a field name if it's a Scala keyword
   */
  def escapeKeyword(name: String): String = {
    val keywords = Set(
      "abstract", "case", "catch", "class", "def", "do", "else", "extends",
      "false", "final", "finally", "for", "forSome", "if", "implicit", "import",
      "lazy", "match", "new", "null", "object", "override", "package", "private",
      "protected", "return", "sealed", "super", "this", "throw", "trait", "true",
      "try", "type", "val", "var", "while", "with", "yield"
    )
    
    if (keywords.contains(name)) {
      s"`$name`"
    } else {
      name
    }
  }

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