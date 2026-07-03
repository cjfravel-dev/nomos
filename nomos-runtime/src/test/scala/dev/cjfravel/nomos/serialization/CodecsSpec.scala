package dev.cjfravel.nomos.serialization

import dev.cjfravel.nomos.json._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Generated `decode` routes numeric fields through these combinators, so a hostile lexeme must
 * yield a `Left` (the `Decoder` contract) rather than throwing or producing a non-finite value
 * that cannot be re-encoded.
 */
class CodecsSpec extends AnyFlatSpec with Matchers {

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
}
