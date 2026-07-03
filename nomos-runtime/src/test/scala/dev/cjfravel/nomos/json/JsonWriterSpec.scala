package dev.cjfravel.nomos.json

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

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
  }

  it should "preserve object key order on output" in {
    val obj = JsonObject("b" -> JsonNumber("1"), "a" -> JsonNumber("2"))
    Json.write(obj) shouldBe """{"b":1,"a":2}"""
  }

  it should "write arrays" in {
    Json.write(JsonArray.of(JsonNumber("1"), JsonString("x"), JsonBoolean(false))) shouldBe """[1,"x",false]"""
  }

  it should "round-trip parse->write byte-for-byte" in {
    val samples = List(
      """{"id":"123","username":"john","email":"j@x.com","age":30.0,"roles":["admin","user"]}""",
      """{"nested":{"a":[1,2,3],"b":null},"flag":true}""",
      """[]""",
      """{}""",
      """{"unicode":"caf\u00e9 \uD83D\uDE00","neg":-1.5e10}"""
    )
    samples.foreach { s =>
      Json.write(Json.parse(s).value) shouldBe s
    }
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
    o.mapKeys(_.toUpperCase).keys shouldBe Vector("A", "B")
    // original is unchanged (immutable)
    o.keys shouldBe Vector("a", "b")
  }
}
