package com.example

import com.example.models.column.Column
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PrefixVariantSpec extends AnyFlatSpec with Matchers {

  "a prefix discriminator" should "validate and deserialize a parameterized value" in {
    val json = """{"type":"Decimal(28,8)","scale":8}"""
    Column.validate(json).isRight shouldBe true
    Column.fromJson(json).isRight shouldBe true
  }

  it should "preserve the parameterized discriminator value on round-trip" in {
    // A prefix variant keeps the on-the-wire discriminator (e.g. "Decimal(28,8)") as a field, so
    // the exact value must survive decode -> encode rather than collapsing to the key "Decimal".
    val json = """{"type":"Decimal(28,8)","scale":8}"""
    val col = Column.fromJson(json).toOption.get
    col.`type` shouldBe "Decimal(28,8)"
    Column.toJson(col) shouldBe json
  }

  it should "deserialize an exact value too" in {
    Column.fromJson("""{"type":"Varchar"}""").isRight shouldBe true
  }

  it should "reject an unknown discriminator value" in {
    Column.fromJson("""{"type":"Mystery(1)"}""").isLeft shouldBe true
  }
}
