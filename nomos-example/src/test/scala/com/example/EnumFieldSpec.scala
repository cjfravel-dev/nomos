package com.example

import com.example.models.account.{Account, Tier}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EnumFieldSpec extends AnyFlatSpec with Matchers {

  "an enum field" should "validate, deserialize to the enum type, and round-trip as its string" in {
    val json = """{"accountId":"a1","active":true,"openedOn":"2024-01-02","tier":"pro"}"""
    Account.validate(json).isRight shouldBe true
    val acct = Account.fromJson(json).right.get
    acct.tier shouldBe Tier.Pro
    Account.toJson(acct) shouldBe json
  }

  it should "reject an unknown enum value" in {
    Account.validate("""{"accountId":"a1","active":true,"openedOn":"2024-01-02","tier":"enterprise"}""").isLeft shouldBe true
  }
}
