package dev.cjfravel.nomos.serialization

import dev.cjfravel.nomos.json.JsonString
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Adapters guarantee wire/model compatibility, so a missing adapter must fail closed (like
 * CodecRegistry) rather than silently passing the value through unchanged, and a registration must
 * make decode and encode available together (no window where only one is visible).
 */
class AdapterRegistrySpec extends AnyFlatSpec with Matchers {

  "AdapterRegistry" should "fail closed for an unregistered adapter" in {
    an[Exception] should be thrownBy AdapterRegistry.decode("nope-unregistered-xyz", "x")
    an[Exception] should be thrownBy AdapterRegistry.encode("nope-unregistered-xyz", "x")
  }

  it should "register decode and encode together" in {
    AdapterRegistry.register("rt-pair-adapter")(decode = _ + "-d", encode = _ + "-e")
    AdapterRegistry.isRegistered("rt-pair-adapter") shouldBe true
    AdapterRegistry.decode("rt-pair-adapter", "x") shouldBe "x-d"
    AdapterRegistry.encode("rt-pair-adapter", "x") shouldBe "x-e"
  }

  "Codecs.adapted" should "return Left for an unregistered adapter instead of passing through" in {
    Codecs.adapted("nope-unregistered-abc")(JsonString("x")) shouldBe a[Left[_, _]]
  }
}
