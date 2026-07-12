package dev.cjfravel.nomos.serialization

import dev.cjfravel.nomos.json.JsonString
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Adapters guarantee wire/model compatibility, so a missing adapter must fail closed (like CodecRegistry) rather than
 * silently passing the value through unchanged, and a registration must make decode and encode available together (no
 * window where only one is visible).
 */
class AdapterRegistrySpec extends AnyFlatSpec with Matchers {

  "AdapterRegistry" should "fail closed for an unregistered adapter" in {
    an[Exception] should be thrownBy AdapterRegistry.decode("nope-unregistered-xyz", "x")
    an[Exception] should be thrownBy AdapterRegistry.encode("nope-unregistered-xyz", "x")
  }

  it should "register decode and encode together" in {
    AdapterRegistry.clear()
    AdapterRegistry.register("rt-pair-adapter")(decode = _ + "-d", encode = _ + "-e")
    AdapterRegistry.isRegistered("rt-pair-adapter") shouldBe true
    AdapterRegistry.decode("rt-pair-adapter", "x") shouldBe "x-d"
    AdapterRegistry.encode("rt-pair-adapter", "x") shouldBe "x-e"
  }

  "Codecs.adapted" should "return Left for an unregistered adapter instead of passing through" in {
    AdapterRegistry.clear()
    Codecs.adapted("nope-unregistered-abc")(JsonString("x")) shouldBe a[Left[_, _]]
  }

  it should "decode and encode through a registered adapter" in {
    AdapterRegistry.clear()
    AdapterRegistry.register("case-adapter")(_.toLowerCase(java.util.Locale.ROOT), _.toUpperCase(java.util.Locale.ROOT))

    Codecs.adapted("case-adapter")(JsonString("WIRE")) shouldBe Right("wire")
    Codecs.adapted("case-adapter")(dev.cjfravel.nomos.json.JsonNumber("1")) shouldBe a[Left[_, _]]
    Codecs.adaptedEncode("case-adapter", "model") shouldBe JsonString("MODEL")

    AdapterRegistry.clear()
    AdapterRegistry.isRegistered("case-adapter") shouldBe false
  }
}
