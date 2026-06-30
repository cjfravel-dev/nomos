package com.example

import com.example.models.limits.Limits
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OpenMapFieldSpec extends AnyFlatSpec with Matchers {

  "a typed open-map field" should "decode, expose entries, and round-trip" in {
    val json = """{"name":"a","attributes":{"k1":"v1","k2":"v2"}}"""
    val limits = Limits.fromJson(json).right.get
    limits.attributes shouldBe Map("k1" -> "v1", "k2" -> "v2")
    Limits.toJson(limits) shouldBe json
  }
}
