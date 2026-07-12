package dev.cjfravel.nomos.json

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JsonNumberSpec extends AnyFlatSpec with Matchers {

  "JsonNumber" should "preserve the original lexeme" in {
    JsonNumber("30.0").raw shouldBe "30.0"
    JsonNumber("1e3").raw shouldBe "1e3"
  }

  it should "treat 1, 1.0 and 1e2 as integral" in {
    JsonNumber("1").isIntegral shouldBe true
    JsonNumber("1.0").isIntegral shouldBe true
    JsonNumber("1e2").isIntegral shouldBe true
    JsonNumber("1.5").isIntegral shouldBe false
  }

  it should "report Int range correctly" in {
    JsonNumber("42").fitsInt shouldBe true
    JsonNumber("2147483647").fitsInt shouldBe true
    JsonNumber("2147483648").fitsInt shouldBe false
    JsonNumber("-2147483648").fitsInt shouldBe true
    JsonNumber("9999999999").fitsInt shouldBe false
    JsonNumber("1.5").fitsInt shouldBe false
  }

  it should "report Long range correctly" in {
    JsonNumber("9223372036854775807").fitsLong shouldBe true
    JsonNumber("9223372036854775808").fitsLong shouldBe false
    JsonNumber("99999999999999999999999").fitsLong shouldBe false
  }

  it should "expose typed accessors" in {
    JsonNumber("42").asInt shouldBe Some(42)
    JsonNumber("42").asLong shouldBe Some(42L)
    JsonNumber("3.14").asDouble shouldBe 3.14
    JsonNumber("3.14").asInt shouldBe None
    JsonNumber("3.5").asBigDecimal shouldBe BigDecimal("3.5")
    JsonNumber("1e2147483648").asBigDecimalOption shouldBe None
    JsonNumber("1e2147483648").isIntegral shouldBe false
  }

  it should "build from primitives" in {
    JsonNumber.fromInt(5).raw shouldBe "5"
    JsonNumber.fromLong(5L).raw shouldBe "5"
    JsonNumber.fromDouble(2.5).raw shouldBe "2.5"
  }

  it should "reject NaN and infinity" in {
    an[IllegalArgumentException] should be thrownBy JsonNumber.fromDouble(Double.NaN)
    an[IllegalArgumentException] should be thrownBy JsonNumber.fromDouble(Double.PositiveInfinity)
  }

  // raw is emitted verbatim by the writer, so construction validates the lexeme: an invalid
  // JsonNumber (which the writer would render as invalid JSON) cannot be built.
  it should "reject a non-numeric or malformed lexeme" in {
    an[IllegalArgumentException] should be thrownBy JsonNumber("NaN")
    an[IllegalArgumentException] should be thrownBy JsonNumber("Infinity")
    an[IllegalArgumentException] should be thrownBy JsonNumber("01")
    an[IllegalArgumentException] should be thrownBy JsonNumber("1.")
    an[IllegalArgumentException] should be thrownBy JsonNumber(".5")
    an[IllegalArgumentException] should be thrownBy JsonNumber("+1")
    an[IllegalArgumentException] should be thrownBy JsonNumber("1e")
    an[IllegalArgumentException] should be thrownBy JsonNumber("")
  }

  it should "accept every well-formed lexeme form" in {
    Seq("0", "-0", "42", "-2147483648", "30.0", "1e3", "-0.5E-2", "1E+10", "9999999999")
      .foreach(s => JsonNumber(s).raw shouldBe s)
  }
}

class JsonWriterSpec extends AnyFlatSpec with Matchers with EitherValues {

  "JsonWriter" should "write primitives" in {
    Json.write(JsonNull) shouldBe "null"
    Json.write(JsonBoolean(true)) shouldBe "true"
    Json.write(JsonString("hi")) shouldBe "\"hi\""
    Json.write(JsonNumber("30.0")) shouldBe "30.0"
  }

  it should "escape strings per JSON, not Scala" in {
    Json.write(JsonString("a\"b\\c\nd\t")) shouldBe "\"a\\\"b\\\\c\\nd\\t\""
  }

  it should "escape control characters as \\u" in {
    Json.write(JsonString("\u0001")) shouldBe "\"\\u0001\""
    Json.write(JsonString("\b\f\r")) shouldBe "\"\\b\\f\\r\""
  }

  it should "preserve object key order on output" in {
    val obj = JsonObject("b" -> JsonNumber("1"), "a" -> JsonNumber("2"))
    Json.write(obj) shouldBe """{"b":1,"a":2}"""
  }

  it should "write arrays" in {
    Json.write(JsonArray.of(JsonNumber("1"), JsonString("x"), JsonBoolean(false))) shouldBe """[1,"x",false]"""
  }

  it should "round-trip parse->write byte-for-byte" in {
    val samples =
      List(
        """{"id":"123","username":"john","email":"j@x.com","age":30.0,"roles":["admin","user"]}""",
        """{"nested":{"a":[1,2,3],"b":null},"flag":true}""",
        """[]""",
        """{}""",
        """{"unicode":"caf\u00e9 \uD83D\uDE00","neg":-1.5e10}""")
    samples.foreach(s => Json.write(Json.parse(s).value) shouldBe s)
  }

  it should "pretty-print nested structures" in {
    val pretty = Json.writePretty(Json.parse("""{"a":1,"b":[2,3]}""").value)
    pretty should include("\n")
    Json.parse(pretty).value shouldBe Json.parse("""{"a":1,"b":[2,3]}""").value
  }
}

class JsonObjectSpec extends AnyFlatSpec with Matchers {

  "JsonObject" should "have order-independent equality" in {
    JsonObject("a" -> JsonNumber("1"), "b" -> JsonNumber("2")) shouldBe
      JsonObject("b" -> JsonNumber("2"), "a" -> JsonNumber("1"))
  }

  it should "look up fields and report keys" in {
    val o = JsonObject("x" -> JsonString("v"))
    o.field("x") shouldBe Some(JsonString("v"))
    o.field("missing") shouldBe None
    o.contains("x") shouldBe true
  }

  it should "expose typeName and accessors on JsonValue" in {
    (JsonString("x"): JsonValue).asString shouldBe Some("x")
    (JsonNumber("1"): JsonValue).asNumber.map(_.raw) shouldBe Some("1")
    (JsonNull: JsonValue).typeName shouldBe "null"
    (JsonArray.empty: JsonValue).isArray shouldBe true
  }

  it should "support immutable updated/remove/mapKeys preserving order" in {
    val o = JsonObject("a" -> JsonNumber("1"), "b" -> JsonNumber("2"))
    // updated replaces in place
    o.updated("a", JsonNumber("9")).keys shouldBe Vector("a", "b")
    o.updated("a", JsonNumber("9")).field("a") shouldBe Some(JsonNumber("9"))
    // updated appends a new key
    o.updated("c", JsonNumber("3")).keys shouldBe Vector("a", "b", "c")
    // remove
    o.remove("a").keys shouldBe Vector("b")
    o.remove("missing") shouldBe o
    // mapKeys (runtime key rewriting), order preserved
    o.mapKeys(_.toUpperCase(java.util.Locale.ROOT)).keys shouldBe Vector("A", "B")
    // original is unchanged (immutable)
    o.keys shouldBe Vector("a", "b")
  }

  it should "expose collection metadata and stable value semantics" in {
    JsonObject.empty.isEmpty shouldBe true
    JsonObject.empty.size shouldBe 0
    val one = JsonObject("x" -> JsonNumber("1"))
    one.isEmpty shouldBe false
    one.size shouldBe 1
    one.hashCode() shouldBe JsonObject("x" -> JsonNumber("1")).hashCode()
    one.equals("not an object") shouldBe false
    one.toString should include("JsonObject")
  }

  it should "collapse duplicate construction keys at their first position" in {
    val obj =
      JsonObject.fromFields(Vector("a" -> JsonNumber("1"), "b" -> JsonNumber("2"), "a" -> JsonNumber("3")))
    obj.keys shouldBe Vector("a", "b")
    obj.field("a") shouldBe Some(JsonNumber("3"))
  }
}

class JsonFacadeSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Json construction helpers" should "build every public JSON value shape" in {
    Json.obj("a" -> Json.num(1)).field("a") shouldBe Some(JsonNumber("1"))
    Json.obj(List("a" -> Json.str("x"))).field("a") shouldBe Some(JsonString("x"))
    Json.arr(Json.bool(true), Json.nul).values shouldBe Vector(JsonBoolean(true), JsonNull)
    Json.arr(List(Json.str("x"))).values shouldBe Vector(JsonString("x"))
    Json.num(1L) shouldBe JsonNumber("1")
    Json.num(1.5) shouldBe JsonNumber("1.5")
    Json.num(BigDecimal("1.50")) shouldBe JsonNumber("1.50")
  }

  it should "expose every type predicate and accessor" in {
    val values =
      List[JsonValue](JsonNull, JsonString("x"), JsonNumber("1"), JsonBoolean(true), JsonArray.empty, JsonObject.empty)
    values.map(_.typeName) shouldBe List("null", "string", "number", "boolean", "array", "object")
    JsonNull.isNull shouldBe true
    JsonString("x").isString shouldBe true
    JsonNumber("1").isNumber shouldBe true
    JsonBoolean(true).isBoolean shouldBe true
    JsonObject.empty.isObject shouldBe true
    JsonArray.empty.isArray shouldBe true
    JsonString("x").asBoolean shouldBe None
    JsonString("x").asNumber shouldBe None
    JsonString("x").asArray shouldBe None
    JsonString("x").asObject shouldBe None
    JsonBoolean(true).asBoolean shouldBe Some(true)
    JsonArray.empty.asArray shouldBe Some(JsonArray.empty)
    JsonObject.empty.asObject shouldBe Some(JsonObject.empty)
  }

  "JsonArray" should "support indexed access and bounds-safe lookup" in {
    val array = JsonArray.of(JsonString("a"), JsonString("b"))
    array.size shouldBe 2
    array.isEmpty shouldBe false
    array(1) shouldBe JsonString("b")
    array.get(0) shouldBe Some(JsonString("a"))
    array.get(-1) shouldBe None
    array.get(2) shouldBe None
    JsonArray.empty.isEmpty shouldBe true
  }
}
