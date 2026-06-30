package com.example

import com.example.models.account.Account
import com.example.ops.AccountOps._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HelperMethodSpec extends AnyFlatSpec with Matchers {

  "derived behavior via an extension object" should "read the generated type's fields" in {
    val acct = Account.fromJson("""{"accountId":"a1","active":true,"openedOn":"2024-01-02","tier":"free"}""").right.get
    acct.label shouldBe "a1/true"
  }
}
