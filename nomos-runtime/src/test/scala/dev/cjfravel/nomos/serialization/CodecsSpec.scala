package dev.cjfravel.nomos.serialization

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.json._
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Generated `decode` routes numeric fields through these combinators, so a hostile lexeme must yield a `Left` (the
 * `Decoder` contract) rather than throwing or producing a non-finite value that cannot be re-encoded.
 */
class CodecsSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Codecs.bigDecimal" should "decode a normal decimal" in {
    Codecs.bigDecimal(JsonNumber("3.5")) shouldBe Right(BigDecimal("3.5"))
  }

  it should "return Left on an out-of-range exponent instead of throwing" in {
    Codecs.bigDecimal(JsonNumber("1e2147483648")) shouldBe a[Left[_, _]]
  }

  it should "reject a non-number" in {
    Codecs.bigDecimal(JsonString("x")) shouldBe a[Left[_, _]]
  }

  "Codecs.double" should "decode a normal double" in {
    Codecs.double(JsonNumber("3.14")) shouldBe Right(3.14)
  }

  it should "return Left on a magnitude that overflows Double instead of decoding Infinity" in {
    Codecs.double(JsonNumber("1e309")) shouldBe a[Left[_, _]]
  }

  "Codecs.boxedDouble" should "also reject a non-finite magnitude" in {
    Codecs.boxedDouble(JsonNumber("-1e309")) shouldBe a[Left[_, _]]
  }

  "scalar codecs" should "decode matching JSON values" in {
    Codecs.string(JsonString("x")) shouldBe Right("x")
    Codecs.int(JsonNumber("42")) shouldBe Right(42)
    Codecs.long(JsonNumber(Long.MaxValue.toString)) shouldBe Right(Long.MaxValue)
    Codecs.boolean(JsonBoolean(true)) shouldBe Right(true)
    Codecs.any(JsonNull) shouldBe Right(JsonNull)
    Codecs.boxedInt(JsonNumber("1")) shouldBe Right(java.lang.Integer.valueOf(1))
    Codecs.boxedLong(JsonNumber("2")) shouldBe Right(java.lang.Long.valueOf(2))
    Codecs.boxedBoolean(JsonBoolean(false)) shouldBe Right(java.lang.Boolean.FALSE)
    Codecs.boxedBoolean(JsonString("false")) shouldBe a[Left[_, _]]
  }

  it should "reject mismatched and out-of-range JSON values" in {
    Codecs.string(JsonNumber("1")).left.value should include("expected string")
    Codecs.int(JsonNumber("1.5")).left.value should include("expected int")
    Codecs.int(JsonNumber("2147483648")).left.value should include("expected int")
    Codecs.int(JsonString("1")).left.value should include("expected int")
    Codecs.long(JsonNumber("9223372036854775808")).left.value should include("expected long")
    Codecs.long(JsonString("1")).left.value should include("expected long")
    Codecs.double(JsonString("1")).left.value should include("expected number")
    Codecs.boolean(JsonString("true")).left.value should include("expected boolean")
  }

  "Codecs.temporal" should "decode valid strings and reject invalid input" in {
    val decoder = Codecs.temporal("date", java.time.LocalDate.parse)
    decoder(JsonString("2026-07-11")) shouldBe Right(java.time.LocalDate.of(2026, 7, 11))
    decoder(JsonString("not-a-date")).left.value shouldBe "invalid date: not-a-date"
    decoder(JsonNumber("1")).left.value should include("expected date string")
  }

  "Codecs.list" should "decode every element and identify the first failing index" in {
    val decoder = Codecs.list(Codecs.int)
    decoder(JsonArray.of(JsonNumber("1"), JsonNumber("2"))) shouldBe Right(List(1, 2))
    decoder(JsonArray.of(JsonNumber("1"), JsonString("bad"), JsonString("later"))).left.value should startWith("[1]:")
    decoder(JsonObject.empty).left.value should include("expected array")
  }

  "Codecs.map" should "preserve key order and identify the first failing key" in {
    val decoder = Codecs.map(Codecs.int)
    val json = JsonObject.fromFields(Vector("z" -> JsonNumber("1"), "a" -> JsonNumber("2")))
    decoder(json).value.toList shouldBe List("z" -> 1, "a" -> 2)
    decoder(JsonObject("good" -> JsonNumber("1"), "bad" -> JsonString("x"))).left.value should startWith("bad:")
    decoder(JsonArray.empty).left.value should include("expected object")
  }

  "Java map helpers" should "round-trip insertion-ordered entries" in {
    val javaMap = Codecs.toJavaMap(ListMap("z" -> 1, "a" -> 2))
    javaMap.getClass shouldBe classOf[java.util.LinkedHashMap[_, _]]
    Codecs.javaMapEntries(javaMap).toList shouldBe List("z" -> 1, "a" -> 2)
  }

  "field codecs" should "decode required, optional, and nullable fields" in {
    val obj = JsonObject("value" -> JsonString("x"), "nullValue" -> JsonNull)
    Codecs.required(obj, "value", Codecs.string) shouldBe Right("x")
    Codecs.required(obj, "missing", Codecs.string).left.value should include("missing required field")
    Codecs.required(obj, "value", Codecs.int).left.value should startWith("value:")

    Codecs.optional(obj, "missing", Codecs.string) shouldBe Right(None)
    Codecs.optional(obj, "nullValue", Codecs.string) shouldBe Right(None)
    Codecs.optional(obj, "value", Codecs.string) shouldBe Right(Some("x"))
    Codecs.optional(obj, "value", Codecs.int).left.value should startWith("value:")

    Codecs.nullable[String](obj, "missing", Codecs.string) shouldBe Right(null)
    Codecs.nullable[String](obj, "nullValue", Codecs.string) shouldBe Right(null)
    Codecs.nullable[String](obj, "value", Codecs.string) shouldBe Right("x")
    Codecs.nullable[String](obj, "value", _ => Left("bad")).left.value shouldBe "value: bad"
  }
}
