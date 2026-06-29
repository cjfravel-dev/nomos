package com.example

import com.example.models.limits.Limits
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BoxedNullableSpec extends AnyFlatSpec with Matchers {

  "a nullable numeric field" should "deserialize a present value and default to null when absent" in {
    Limits.fromJson("""{"name":"a","maxFiles":5}""").right.get.maxFiles shouldBe (5: java.lang.Integer)
    Limits.fromJson("""{"name":"a"}""").right.get.maxFiles shouldBe null
  }
}
