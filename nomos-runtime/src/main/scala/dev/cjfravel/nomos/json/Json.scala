package dev.cjfravel.nomos.json

/**
 * Ergonomic facade over the JSON model, parser, and writer.
 *
 * This is a public, supported part of `nomos-runtime`: generated code and runtime validation use it, and consumers may
 * use it directly for hand-written runtime JSON (parse, inspect, transform, and write). See [[JsonValue]] for the
 * stability/scope contract.
 */
object Json {

  def parse(input: String): Either[String, JsonValue] = JsonParser.parse(input)

  def write(value: JsonValue): String = JsonWriter.write(value)

  def writePretty(value: JsonValue): String = JsonWriter.writePretty(value)

  // Construction helpers
  def obj(fields: (String, JsonValue)*): JsonObject = JsonObject.fromFields(fields)
  def obj(fields: Iterable[(String, JsonValue)]): JsonObject = JsonObject.fromFields(fields)
  def arr(values: JsonValue*): JsonArray = JsonArray(values.toVector)
  def arr(values: Iterable[JsonValue]): JsonArray = JsonArray(values.toVector)
  def str(value: String): JsonString = JsonString(value)
  def num(value: Int): JsonNumber = JsonNumber.fromInt(value)
  def num(value: Long): JsonNumber = JsonNumber.fromLong(value)
  def num(value: Double): JsonNumber = JsonNumber.fromDouble(value)
  def num(value: BigDecimal): JsonNumber = JsonNumber.fromBigDecimal(value)
  def bool(value: Boolean): JsonBoolean = JsonBoolean(value)
  val nul: JsonNull.type = JsonNull
}
