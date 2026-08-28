package com.example

import com.example.models.selector.Selector
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PresenceGroupAndUniqueBySpec extends AnyFlatSpec with Matchers {

  private def json(body: String): String = s"""{"name":"n","entries":[],$body}"""

  "a $oneOf presence group" should "accept exactly one of the grouped keys" in {
    val selector = Selector.fromJson(json(""""inlineValue":"x"""")).right.get
    selector.inlineValue shouldBe Some("x")
    selector.valueRef shouldBe None
    Selector.validate(json(""""valueRef":"other"""")).isRight shouldBe true
  }

  it should "reject none present and more than one present" in {
    Selector.validate("""{"name":"n","entries":[]}""").isLeft shouldBe true
    Selector.validate(json(""""inlineValue":"x","valueRef":"y"""")).isLeft shouldBe true
  }

  "a uniqueBy array constraint" should "reject elements sharing the key while uniqueItems would not" in {
    val duplicate =
      """{"name":"n","inlineValue":"x","entries":[{"key":"a","label":"one"},{"key":"a","label":"two"}]}"""
    Selector.validate(duplicate).isLeft shouldBe true

    val unique =
      """{"name":"n","inlineValue":"x","entries":[{"key":"a","label":"one"},{"key":"b","label":"two"}]}"""
    Selector.validate(unique).isRight shouldBe true
    Selector.fromJson(unique).right.get.entries.map(_.key) shouldBe List("a", "b")
  }
}
