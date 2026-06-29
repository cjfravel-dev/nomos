package com.example

import com.example.models.account.Account
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DateFieldSpec extends AnyFlatSpec with Matchers {

  "a date field" should "validate, deserialize to LocalDate, and round-trip as ISO" in {
    val json = """{"accountId":"a1","active":true,"openedOn":"2024-01-02","tier":"free"}"""
    Account.validate(json).isRight shouldBe true
    val acct = Account.fromJson(json).right.get
    acct.openedOn shouldBe java.time.LocalDate.of(2024, 1, 2)
    Account.toJson(acct) shouldBe json
  }

  it should "reject a malformed date" in {
    Account.validate("""{"accountId":"a1","active":true,"openedOn":"nope","tier":"free"}""").isLeft shouldBe true
  }
}
