package dev.cjfravel.nomos.json

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

class JsonParserSpec extends AnyFlatSpec with Matchers with EitherValues {

  "JsonParser" should "parse primitives" in {
    Json.parse("null").value shouldBe JsonNull
    Json.parse("true").value shouldBe JsonBoolean(true)
    Json.parse("false").value shouldBe JsonBoolean(false)
    Json.parse("\"hello\"").value shouldBe JsonString("hello")
    Json.parse("42").value shouldBe JsonNumber("42")
  }

  it should "parse an object preserving key order" in {
    val obj = Json.parse("""{"b":1,"a":2,"c":3}""").value.asObject.get
    obj.keys shouldBe Vector("b", "a", "c")
  }

  it should "parse nested structures" in {
    val v = Json.parse("""{"items":[1,2,{"x":true}],"name":"n"}""").value.asObject.get
    v.field("items").get.asArray.get.size shouldBe 3
    v.field("items").get.asArray.get(2).asObject.get.field("x").get shouldBe JsonBoolean(true)
  }

  it should "collapse duplicate keys last-wins keeping first position" in {
    val obj = Json.parse("""{"a":1,"b":2,"a":3}""").value.asObject.get
    obj.keys shouldBe Vector("a", "b")
    obj.field("a").get shouldBe JsonNumber("3")
  }

  it should "decode string escapes" in {
    Json.parse(""""a\"b\\c\n\t\/d"""").value shouldBe JsonString("a\"b\\c\n\t/d")
  }

  it should "decode BMP unicode escapes" in {
    Json.parse("\"\\u00e9\"").value shouldBe JsonString("\u00e9")
  }

  it should "decode surrogate pairs" in {
    // U+1F600 GRINNING FACE, supplied as a literal \uD83D\uDE00 escape
    Json.parse("\"\\uD83D\\uDE00\"").value shouldBe JsonString("\uD83D\uDE00")
  }

  it should "reject a lone high surrogate" in {
    Json.parse("\"\\uD83D\"") shouldBe a[Left[_, _]]
  }

  it should "reject a lone low surrogate" in {
    Json.parse("\"\\uDE00\"") shouldBe a[Left[_, _]]
  }

  it should "reject unescaped control characters in strings" in {
    Json.parse("\"a\u0001b\"") shouldBe a[Left[_, _]]
  }

  it should "reject invalid escape sequences" in {
    Json.parse(""""a\qb"""") shouldBe a[Left[_, _]]
  }

  it should "parse numbers with fraction and exponent" in {
    Json.parse("-3.14").value shouldBe JsonNumber("-3.14")
    Json.parse("1e10").value shouldBe JsonNumber("1e10")
    Json.parse("2.5E-3").value shouldBe JsonNumber("2.5E-3")
    Json.parse("0").value shouldBe JsonNumber("0")
  }

  it should "reject malformed numbers" in {
    Json.parse("01") shouldBe a[Left[_, _]]
    Json.parse("+1") shouldBe a[Left[_, _]]
    Json.parse(".5") shouldBe a[Left[_, _]]
    Json.parse("1.") shouldBe a[Left[_, _]]
    Json.parse("1e") shouldBe a[Left[_, _]]
    Json.parse("NaN") shouldBe a[Left[_, _]]
  }

  it should "reject trailing content" in {
    Json.parse("1 2") shouldBe a[Left[_, _]]
    Json.parse("{} {}") shouldBe a[Left[_, _]]
  }

  it should "reject unterminated input" in {
    Json.parse("""{"a":""") shouldBe a[Left[_, _]]
    Json.parse("""[1,2""") shouldBe a[Left[_, _]]
    Json.parse(""""abc""") shouldBe a[Left[_, _]]
  }

  it should "accept empty object and array" in {
    Json.parse("{}").value shouldBe JsonObject.empty
    Json.parse("[]").value shouldBe JsonArray.empty
  }

  it should "tolerate surrounding whitespace" in {
    Json.parse("  \n\t {\n\"a\" : 1 }\n ").value.asObject.get.field("a").get shouldBe JsonNumber("1")
  }

  it should "enforce the maximum nesting depth" in {
    val deep = "[" * 1000 + "]" * 1000
    Json.parse(deep) shouldBe a[Left[_, _]]
    Json.parse(deep).left.value should include("depth")
  }

  it should "allow nesting within the configured depth" in {
    val ok = "[" * 10 + "]" * 10
    Json.parse(ok) shouldBe a[Right[_, _]]
  }

  it should "reject input exceeding the max size" in {
    val cfg = JsonParser.Config(maxInputChars = 4)
    JsonParser.parse("12345", cfg) shouldBe a[Left[_, _]]
  }
}
