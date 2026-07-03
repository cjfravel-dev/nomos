package dev.cjfravel.nomos.json

/**
 * Serializes a [[JsonValue]] back to JSON text. Object key order is preserved and numbers are
 * emitted from their original lexeme, so parse-then-write is a semantic round-trip: number lexemes
 * are preserved exactly, but string escapes are normalized (e.g. `\u00e9` -> `é`) and duplicate
 * object keys are collapsed (last value wins).
 */
object JsonWriter {

  def write(value: JsonValue): String = writeCompact(value)

  def writeCompact(value: JsonValue): String = {
    val sb = new StringBuilder
    writeValue(value, sb)
    sb.toString
  }

  def writePretty(value: JsonValue, indent: String = "  "): String = {
    val sb = new StringBuilder
    writePrettyValue(value, sb, indent, 0)
    sb.toString
  }

  private def writeValue(value: JsonValue, sb: StringBuilder): Unit = value match {
    case JsonNull => sb.append("null")
    case JsonBoolean(b) => sb.append(if (b) "true" else "false")
    case JsonNumber(raw) => sb.append(raw)
    case JsonString(s) => writeString(s, sb)
    case JsonArray(values) =>
      sb.append('[')
      var first = true
      values.foreach { v =>
        if (!first) sb.append(',')
        writeValue(v, sb)
        first = false
      }
      sb.append(']')
    case o: JsonObject =>
      sb.append('{')
      var first = true
      o.orderedFields.foreach { case (k, v) =>
        if (!first) sb.append(',')
        writeString(k, sb)
        sb.append(':')
        writeValue(v, sb)
        first = false
      }
      sb.append('}')
  }

  private def writePrettyValue(value: JsonValue, sb: StringBuilder, indent: String, level: Int): Unit =
    value match {
      case JsonArray(values) if values.nonEmpty =>
        sb.append("[\n")
        val childPad = indent * (level + 1)
        var first = true
        values.foreach { v =>
          if (!first) sb.append(",\n")
          sb.append(childPad)
          writePrettyValue(v, sb, indent, level + 1)
          first = false
        }
        sb.append('\n').append(indent * level).append(']')
      case o: JsonObject if o.orderedFields.nonEmpty =>
        sb.append("{\n")
        val childPad = indent * (level + 1)
        var first = true
        o.orderedFields.foreach { case (k, v) =>
          if (!first) sb.append(",\n")
          sb.append(childPad)
          writeString(k, sb)
          sb.append(": ")
          writePrettyValue(v, sb, indent, level + 1)
          first = false
        }
        sb.append('\n').append(indent * level).append('}')
      case other => writeValue(other, sb)
    }

  /**
   * Writes a JSON string literal, escaping per the JSON spec. This is distinct from Scala
   * source escaping: it escapes the JSON control set and emits other characters verbatim.
   */
  private def writeString(s: String, sb: StringBuilder): Unit = {
    sb.append('"')
    var i = 0
    val n = s.length
    while (i < n) {
      val c = s.charAt(i)
      c match {
        case '"' => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case ch if ch < 0x20 => sb.append("\\u%04x".format(ch.toInt))
        case ch => sb.append(ch)
      }
      i += 1
    }
    sb.append('"')
  }
}
