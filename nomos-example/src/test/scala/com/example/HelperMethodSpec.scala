package com.example

import com.example.models.account.Account
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HelperMethodSpec extends AnyFlatSpec with Matchers {

  "a declared instance method" should "compile and access fields" in {
    val acct = Account.fromJson("""{"accountId":"a1","active":true,"openedOn":"2024-01-02","tier":"free"}""").right.get
    acct.label shouldBe "a1/true"
  }
}
