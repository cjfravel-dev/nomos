package dev.cjfravel.nomos.json

/**
 * A dependency-free, recursive-descent JSON parser.
 *
 * Returns `Either[String, JsonValue]` so callers never deal with exceptions. The parser
 * enforces a maximum nesting depth and input size to bound resource use on hostile input.
 */
object JsonParser {

  /**
   * Parser limits.
   *
   * @param maxDepth maximum nesting depth of objects/arrays (guards against stack overflow / DoS)
   * @param maxInputChars maximum accepted input length in characters
   */
  final case class Config(maxDepth: Int = 512, maxInputChars: Int = 16 * 1024 * 1024)

  val defaultConfig: Config = Config()

  def parse(input: String): Either[String, JsonValue] = parse(input, defaultConfig)

  def parse(input: String, config: Config): Either[String, JsonValue] = {
    if (input == null) Left("JSON input is null")
    else if (input.length > config.maxInputChars)
      Left(s"JSON input exceeds maximum size ${config.maxInputChars} characters")
    else {
      try Right(new Parser(input, config).parseTopLevel())
      catch { case e: JsonParseException => Left(e.getMessage) }
    }
  }

  private final class JsonParseException(msg: String) extends RuntimeException(msg)

  private final class Parser(input: String, config: Config) {
    private var pos = 0
    private val len = input.length

    def parseTopLevel(): JsonValue = {
      skipWhitespace()
      val value = parseValue(0)
      skipWhitespace()
      if (pos != len) error(s"Unexpected trailing content")
      value
    }

    private def parseValue(depth: Int): JsonValue = {
      if (pos >= len) error("Unexpected end of input")
      input.charAt(pos) match {
        case '{' => parseObject(depth)
        case '[' => parseArray(depth)
        case '"' => JsonString(parseString())
        case 't' => parseLiteral("true", JsonBoolean(true))
        case 'f' => parseLiteral("false", JsonBoolean(false))
        case 'n' => parseLiteral("null", JsonNull)
        case c if c == '-' || (c >= '0' && c <= '9') => parseNumber()
        case c => error(s"Unexpected character '$c'")
      }
    }

    private def parseObject(depth: Int): JsonObject = {
      enterDepth(depth)
      pos += 1 // consume '{'
      skipWhitespace()
      if (peek() == '}') { pos += 1; return JsonObject.empty }
      val fields = Vector.newBuilder[(String, JsonValue)]
      var continue = true
      while (continue) {
        skipWhitespace()
        if (peek() != '"') error("Expected string key in object")
        val key = parseString()
        skipWhitespace()
        if (peek() != ':') error("Expected ':' after object key")
        pos += 1 // consume ':'
        skipWhitespace()
        val value = parseValue(depth + 1)
        fields += (key -> value)
        skipWhitespace()
        peek() match {
          case ',' => pos += 1
          case '}' => pos += 1; continue = false
          case _ => error("Expected ',' or '}' in object")
        }
      }
      JsonObject.fromFields(fields.result())
    }

    private def parseArray(depth: Int): JsonArray = {
      enterDepth(depth)
      pos += 1 // consume '['
      skipWhitespace()
      if (peek() == ']') { pos += 1; return JsonArray.empty }
      val values = Vector.newBuilder[JsonValue]
      var continue = true
      while (continue) {
        skipWhitespace()
        values += parseValue(depth + 1)
        skipWhitespace()
        peek() match {
          case ',' => pos += 1
          case ']' => pos += 1; continue = false
          case _ => error("Expected ',' or ']' in array")
        }
      }
      JsonArray(values.result())
    }

    private def parseString(): String = {
      pos += 1 // consume opening quote
      val sb = new StringBuilder
      var done = false
      while (!done) {
        if (pos >= len) error("Unterminated string")
        val c = input.charAt(pos)
        if (c == '"') { pos += 1; done = true }
        else if (c == '\\') { pos += 1; parseEscape(sb) }
        else if (c < 0x20) error(s"Unescaped control character in string")
        else { sb.append(c); pos += 1 }
      }
      sb.toString
    }

    private def parseEscape(sb: StringBuilder): Unit = {
      if (pos >= len) error("Unterminated escape sequence")
      val c = input.charAt(pos)
      pos += 1
      c match {
        case '"' => sb.append('"')
        case '\\' => sb.append('\\')
        case '/' => sb.append('/')
        case 'b' => sb.append('\b')
        case 'f' => sb.append('\f')
        case 'n' => sb.append('\n')
        case 'r' => sb.append('\r')
        case 't' => sb.append('\t')
        case 'u' =>
          val hi = parseHex4()
          if (hi >= 0xD800 && hi <= 0xDBFF) {
            // High surrogate: must be followed by a low-surrogate escape
            if (pos + 1 >= len || input.charAt(pos) != '\\' || input.charAt(pos + 1) != 'u')
              error("High surrogate must be followed by low surrogate")
            pos += 2 // consume the backslash and the 'u'
            val lo = parseHex4()
            if (lo < 0xDC00 || lo > 0xDFFF) error("Invalid low surrogate")
            sb.append(hi.toChar)
            sb.append(lo.toChar)
          } else if (hi >= 0xDC00 && hi <= 0xDFFF) {
            error("Unexpected low surrogate")
          } else {
            sb.append(hi.toChar)
          }
        case other => error(s"Invalid escape sequence '\\$other'")
      }
    }

    private def parseHex4(): Int = {
      if (pos + 4 > len) error("Incomplete unicode escape")
      var value = 0
      var i = 0
      while (i < 4) {
        val c = input.charAt(pos + i)
        val digit =
          if (c >= '0' && c <= '9') c - '0'
          else if (c >= 'a' && c <= 'f') c - 'a' + 10
          else if (c >= 'A' && c <= 'F') c - 'A' + 10
          else error("Invalid unicode escape")
        value = (value << 4) | digit
        i += 1
      }
      pos += 4
      value
    }

    private def parseNumber(): JsonNumber = {
      val start = pos
      if (peek() == '-') pos += 1
      // integer part
      if (peek() == '0') pos += 1
      else if (peek() >= '1' && peek() <= '9') consumeDigits()
      else error("Invalid number: expected digit")
      // fraction
      if (pos < len && input.charAt(pos) == '.') {
        pos += 1
        if (!isDigit(peek())) error("Invalid number: expected digit after '.'")
        consumeDigits()
      }
      // exponent
      if (pos < len && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
        pos += 1
        if (pos < len && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos += 1
        if (!isDigit(peek())) error("Invalid number: expected digit in exponent")
        consumeDigits()
      }
      JsonNumber(input.substring(start, pos))
    }

    private def consumeDigits(): Unit = {
      while (pos < len && isDigit(input.charAt(pos))) pos += 1
    }

    private def isDigit(c: Char): Boolean = c >= '0' && c <= '9'

    private def parseLiteral(expected: String, value: JsonValue): JsonValue = {
      if (pos + expected.length > len || input.substring(pos, pos + expected.length) != expected)
        error(s"Invalid literal, expected '$expected'")
      pos += expected.length
      value
    }

    private def peek(): Char = if (pos < len) input.charAt(pos) else '\u0000'

    private def skipWhitespace(): Unit = {
      while (pos < len) {
        input.charAt(pos) match {
          case ' ' | '\t' | '\n' | '\r' => pos += 1
          case _ => return
        }
      }
    }

    private def enterDepth(depth: Int): Unit =
      if (depth >= config.maxDepth)
        error(s"Maximum JSON nesting depth ${config.maxDepth} exceeded")

    private def error(message: String): Nothing = {
      val (line, col) = lineAndColumn()
      throw new JsonParseException(s"$message at line $line, column $col (offset $pos)")
    }

    private def lineAndColumn(): (Int, Int) = {
      var line = 1
      var col = 1
      var i = 0
      val bound = math.min(pos, len)
      while (i < bound) {
        if (input.charAt(i) == '\n') { line += 1; col = 1 } else col += 1
        i += 1
      }
      (line, col)
    }
  }
}
