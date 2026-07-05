package dev.cjfravel.nomos.json

/**
 * A first-party, dependency-free JSON value model.
 *
 * This exists so generated code and runtime validation never expose a third-party JSON library on a consumer's
 * classpath. It uses only the Scala standard library.
 *
 * '''Public, supported API.''' Because the runtime is dependency-free, this model is the JSON type consumers reach for
 * in hand-written runtime code (it also appears in public signatures such as `MultiValidator.validate` and the
 * `ValidatorRegistry` callback). It is a committed, semver-stable part of `nomos-runtime` and safe to depend on
 * directly.
 *
 * '''Scope (what this is).''' Deliberately minimal: an immutable JSON tree plus exactly the operations generated
 * codecs, validation, and straightforward hand-written runtime code need.
 *   - an immutable model: [[JsonNull]], [[JsonBoolean]], [[JsonString]], [[JsonNumber]], [[JsonArray]], [[JsonObject]]
 *     (insertion-ordered, order-independent equality);
 *   - parse and (compact / pretty) write via [[Json]];
 *   - type tests/accessors (`isObject`, `asString`, `asInt`, ...) that return `Option`;
 *   - shallow, single-level lookups (`JsonObject.field`, `JsonArray.get`/`apply`);
 *   - immutable single-level transforms (`JsonObject.updated`, `remove`, `mapKeys`);
 *   - exact number handling: the original lexeme is preserved so output round-trips.
 *
 * '''Out of scope (what this is not, and will not become).''' This is not a general-purpose JSON toolkit; the following
 * are intentionally excluded and requests for them are declined:
 *   - path/query languages (JSON Pointer, JSONPath) or deep/recursive navigation helpers;
 *   - streaming, incremental, or push/pull parsing (the API is whole-document, in-memory);
 *   - schema languages, diff/patch (JSON Merge Patch / RFC 6902), or canonicalization;
 *   - reflection- or macro-based mapping to arbitrary case classes (use generated codecs);
 *   - mutable builders or in-place mutation; lenses/optics; comments or JSON5 extensions.
 * If you need those, layer your own library on top of this model — nomos will not grow into one.
 */
sealed trait JsonValue {
  def isNull: Boolean = this eq JsonNull
  def isString: Boolean = this.isInstanceOf[JsonString]
  def isNumber: Boolean = this.isInstanceOf[JsonNumber]
  def isBoolean: Boolean = this.isInstanceOf[JsonBoolean]
  def isObject: Boolean = this.isInstanceOf[JsonObject]
  def isArray: Boolean = this.isInstanceOf[JsonArray]

  /** The JSON type name (object, array, string, number, boolean, null). */
  def typeName: String =
    this match {
      case JsonNull => "null"
      case _: JsonString => "string"
      case _: JsonNumber => "number"
      case _: JsonBoolean => "boolean"
      case _: JsonArray => "array"
      case _: JsonObject => "object"
    }

  def asString: Option[String] =
    this match {
      case JsonString(s) => Some(s)
      case _ => None
    }

  def asBoolean: Option[Boolean] =
    this match {
      case JsonBoolean(b) => Some(b)
      case _ => None
    }

  def asNumber: Option[JsonNumber] =
    this match {
      case n: JsonNumber => Some(n)
      case _ => None
    }

  def asArray: Option[JsonArray] =
    this match {
      case a: JsonArray => Some(a)
      case _ => None
    }

  def asObject: Option[JsonObject] =
    this match {
      case o: JsonObject => Some(o)
      case _ => None
    }
}

case object JsonNull extends JsonValue

final case class JsonString(value: String) extends JsonValue

final case class JsonBoolean(value: Boolean) extends JsonValue

/**
 * A JSON number, kept as its original textual lexeme so output round-trips exactly (e.g. `30.0` stays `30.0`). Typed
 * accessors derive values lazily and exactly.
 *
 * @param raw
 *   the original (or canonical) number text; validated on construction to be a well-formed JSON number lexeme so the
 *   writer can emit it verbatim as valid JSON
 */
final case class JsonNumber(raw: String) extends JsonValue {
  require(JsonNumber.isValidLexeme(raw), s"invalid JSON number lexeme: '$raw'")

  /** Exact decimal value of this number. */
  lazy val asBigDecimal: BigDecimal = BigDecimal(raw)

  /** Exact decimal value, or None if the exponent/magnitude is beyond `BigDecimal`'s range. */
  def asBigDecimalOption: Option[BigDecimal] =
    try Some(asBigDecimal)
    catch { case _: NumberFormatException | _: ArithmeticException => None }

  /** Double approximation of this number. */
  def asDouble: Double = java.lang.Double.parseDouble(raw)

  /** True when this number has no fractional part (e.g. `1`, `1.0`, `1e2` are all integral). */
  def isIntegral: Boolean =
    try asBigDecimal.bigDecimal.stripTrailingZeros.scale <= 0
    catch { case _: NumberFormatException => false }

  /** True when this number is integral and fits in a 32-bit Int. */
  def fitsInt: Boolean =
    isIntegral && {
      val bd = asBigDecimal
      bd >= JsonNumber.IntMin && bd <= JsonNumber.IntMax
    }

  /** True when this number is integral and fits in a 64-bit Long. */
  def fitsLong: Boolean =
    isIntegral && {
      val bd = asBigDecimal
      bd >= JsonNumber.LongMin && bd <= JsonNumber.LongMax
    }

  def asInt: Option[Int] = if (fitsInt) Some(asBigDecimal.toIntExact) else None

  def asLong: Option[Long] = if (fitsLong) Some(asBigDecimal.toLongExact) else None
}

object JsonNumber {
  private val IntMin = BigDecimal(Int.MinValue)
  private val IntMax = BigDecimal(Int.MaxValue)
  private val LongMin = BigDecimal(Long.MinValue)
  private val LongMax = BigDecimal(Long.MaxValue)

  // The JSON grammar's number production: optional '-', an int part with no leading zeros, an
  // optional fraction, and an optional exponent. Used to reject lexemes the writer could not emit.
  private val Lexeme = java.util.regex.Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")

  private[nomos] def isValidLexeme(raw: String): Boolean =
    raw != null && Lexeme.matcher(raw).matches()

  def fromInt(value: Int): JsonNumber = JsonNumber(value.toString)
  def fromLong(value: Long): JsonNumber = JsonNumber(value.toString)

  def fromDouble(value: Double): JsonNumber = {
    require(!value.isNaN && !value.isInfinity, "JSON does not support NaN or Infinity")
    JsonNumber(value.toString)
  }

  def fromBigDecimal(value: BigDecimal): JsonNumber =
    JsonNumber(value.bigDecimal.toPlainString)
}

final case class JsonArray(values: Vector[JsonValue]) extends JsonValue {
  def size: Int = values.size
  def isEmpty: Boolean = values.isEmpty
  def apply(index: Int): JsonValue = values(index)
  def get(index: Int): Option[JsonValue] =
    if (index >= 0 && index < values.size) Some(values(index)) else None
}

object JsonArray {
  val empty: JsonArray = JsonArray(Vector.empty)
  def of(values: JsonValue*): JsonArray = JsonArray(values.toVector)
}

/**
 * A JSON object that preserves key insertion order (required for deterministic, byte-stable output). Equality is
 * order-independent and based on the field map, matching common JSON semantics. Construction collapses duplicate keys
 * last-wins while keeping first position.
 */
final class JsonObject private (val orderedFields: Vector[(String, JsonValue)]) extends JsonValue {

  /** Lazily-built lookup map (last value wins for duplicate keys). */
  lazy val fieldMap: Map[String, JsonValue] = {
    val b = Map.newBuilder[String, JsonValue]
    orderedFields.foreach { case (k, v) => b += k -> v }
    b.result()
  }

  def fields: Vector[(String, JsonValue)] = orderedFields
  def keys: Vector[String] = orderedFields.map(_._1)
  def size: Int = orderedFields.size
  def isEmpty: Boolean = orderedFields.isEmpty
  def field(name: String): Option[JsonValue] = fieldMap.get(name)
  def contains(name: String): Boolean = fieldMap.contains(name)

  /**
   * Returns a copy with `name` set to `value`: replaced in place if the key exists, otherwise appended. The original
   * object is unchanged (the model is immutable).
   */
  def updated(name: String, value: JsonValue): JsonObject =
    JsonObject.fromFields(orderedFields :+ name -> value)

  /** Returns a copy without `name` (unchanged if the key is absent). */
  def remove(name: String): JsonObject =
    new JsonObject(orderedFields.filterNot(_._1 == name))

  /**
   * Returns a copy with every key transformed by `f`, preserving order. Useful for runtime key rewriting; if `f`
   * collapses two keys, the later value wins (matching parser semantics).
   */
  def mapKeys(f: String => String): JsonObject =
    JsonObject.fromFields(orderedFields.map { case (k, v) => (f(k), v) })

  override def equals(other: Any): Boolean =
    other match {
      case that: JsonObject => this.fieldMap == that.fieldMap
      case _ => false
    }

  override def hashCode(): Int = fieldMap.hashCode()

  override def toString: String = s"JsonObject(${orderedFields.mkString(", ")})"
}

object JsonObject {
  val empty: JsonObject = new JsonObject(Vector.empty)

  def apply(fields: (String, JsonValue)*): JsonObject = fromFields(fields)

  /**
   * Builds an object from ordered fields. Duplicate keys collapse last-wins while keeping the first occurrence's
   * position, mirroring the parser's behavior.
   */
  def fromFields(fields: Iterable[(String, JsonValue)]): JsonObject = {
    val seen = scala.collection.mutable.LinkedHashMap.empty[String, JsonValue]
    fields.foreach { case (k, v) => seen(k) = v }
    new JsonObject(seen.toVector)
  }
}
