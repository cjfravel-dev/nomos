package dev.cjfravel.nomos.serialization

import dev.cjfravel.nomos.json._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, EitherValues}

class CodecRegistrySpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach with EitherValues {

  private val stringCodec =
    new JsonCodec[String] {
      def encode(value: String): JsonValue = JsonString(value.reverse)
      def decode(json: JsonValue): Either[String, String] =
        json.asString.map(_.reverse).toRight("expected encoded string")
    }

  override def beforeEach(): Unit = CodecRegistry.clear()
  override def afterEach(): Unit = CodecRegistry.clear()

  "CodecRegistry" should "register, decode, and encode a typed codec" in {
    CodecRegistry.register[String]("example.Name")(stringCodec)

    CodecRegistry.isRegistered("example.Name") shouldBe true
    CodecRegistry.decode[String]("example.Name", JsonString("cba")) shouldBe Right("abc")
    CodecRegistry.decode[String]("example.Name", JsonNumber("1")) shouldBe Left("expected encoded string")
    CodecRegistry.encode("example.Name", "abc") shouldBe JsonString("cba")
  }

  it should "replace a registration atomically" in {
    CodecRegistry.register[String]("example.Name")(stringCodec)
    CodecRegistry.register[String]("example.Name")(new JsonCodec[String] {
      def encode(value: String): JsonValue = JsonString(value.toUpperCase(java.util.Locale.ROOT))
      def decode(json: JsonValue): Either[String, String] = Right("replacement")
    })

    CodecRegistry.decode[String]("example.Name", JsonNull) shouldBe Right("replacement")
    CodecRegistry.encode("example.Name", "abc") shouldBe JsonString("ABC")
  }

  it should "fail closed with actionable guidance for an unknown codec" in {
    val decodeError = CodecRegistry.decode[String]("example.Missing", JsonNull).left.value
    decodeError should include("No JSON codec registered")
    decodeError should include("$gen:example.Missing")

    val encodeError = the[IllegalStateException] thrownBy CodecRegistry.encode("example.Missing", "x")
    encodeError.getMessage should include("CodecRegistry.register")
  }

  it should "clear every registration" in {
    CodecRegistry.register[String]("one")(stringCodec)
    CodecRegistry.register[String]("two")(stringCodec)
    CodecRegistry.clear()
    CodecRegistry.isRegistered("one") shouldBe false
    CodecRegistry.isRegistered("two") shouldBe false
  }
}
