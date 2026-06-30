package com.example

import com.example.models.user.User
import dev.cjfravel.nomos.json.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RoundTripSpec extends AnyFlatSpec with Matchers {

  "generated code" should "round-trip JSON byte-for-byte through fromJson/toJson" in {
    val original = """{"id":"123","username":"john","email":"j@x.com","age":30.0,"roles":["admin","user"]}"""
    val user = User.fromJson(original).right.get
    User.toJson(user) shouldBe original
  }

  it should "preserve key order and produce structurally-equal output" in {
    val original = """{"id":"a","username":"b","email":"c@d.com","age":1.0,"roles":[]}"""
    val out = User.toJson(User.fromJson(original).right.get)
    Json.parse(out).right.get shouldBe Json.parse(original).right.get
  }

  it should "validate then round-trip without loss" in {
    val json = """{"id":"x","username":"u","email":"e@e.com","age":5.0,"roles":["r"]}"""
    User.validate(json).isRight shouldBe true
    User.toJson(User.fromJson(json).right.get) shouldBe json
  }
}
