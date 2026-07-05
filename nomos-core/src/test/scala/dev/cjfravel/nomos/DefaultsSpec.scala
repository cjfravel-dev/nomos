package dev.cjfravel.nomos

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.generation._
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.{ParseError, TemplateParser}
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DefaultsSpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  val parser = new TemplateParser()
  def parse(json: String): Either[ParseError, MultiTemplate] =
    parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "parser" should "parse boolean and string defaults" in {
    val d =
      parse(
        """{"name":"N","template":{"verbose":{"type":"boolean","default":false},""" +
          """"name":{"type":"string","default":"report"}}}""").value.definitions.head
    val f = d.templateType.asInstanceOf[ObjectType].fields
    f("verbose").default shouldBe Some("false")
    f("name").default shouldBe Some("\"report\"")
  }

  def multi: MultiTemplate =
    MultiTemplate(
      "com.example",
      List(
        TemplateDefinition(
          "N",
          ObjectType(
            ListMap(
              "verbose" -> FieldDef(BooleanType(), false, Some("false")),
              "name" -> FieldDef(StringType(), false))))))

  "generator" should "emit case-class default args" in {
    val content =
      new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
        .generateMulti(multi)
        .value
        .find(_.fileName == "N.scala")
        .get
        .content
    content should include("verbose: Boolean = false")
  }

  "serializer" should "round-trip a default" in {
    TemplateSerializer.serializeFieldDef(FieldDef(BooleanType(), false, Some("false"))) should include("default = Some")
  }

  // A field with a default is not required in the JSON: decode defaults it when absent, so the
  // validator must not report it missing (previously it only checked `optional`).
  "validator" should "not report a defaulted field as missing when it is absent" in {
    val t =
      parse("""{"name":"N","template":{"id":"string","exclude_from_ad":{"type":"boolean","default":false}}}""").value
    val v = new MultiValidator(t)
    v.validate("""{"id":"a"}""", "com.example.N") shouldBe a[Right[_, _]] // absent default: OK
    v.validate("""{"exclude_from_ad":true}""", "com.example.N").isLeft shouldBe true // id still required
  }

  it should "not report a defaulted common or variant field as missing in a discriminated union" in {
    val t =
      parse("""{"name":"U","template":{"$type":{"discriminator":"type",
        |"commonFields":{"enabled":{"type":"boolean","default":true}},
        |"variants":{"a":{"limit":{"type":"int","default":10}}}}}}""".stripMargin).value
    val v = new MultiValidator(t)
    // both the common field (enabled) and the variant field (limit) are defaulted, so absent is OK
    v.validate("""{"type":"a"}""", "com.example.U") shouldBe a[Right[_, _]]
  }

  it should "still report a genuinely required field as missing" in {
    val t = parse("""{"name":"N","template":{"id":"string","name":"string"}}""").value
    new MultiValidator(t).validate("""{"id":"a"}""", "com.example.N").isLeft shouldBe true
  }

  "generated code with a defaulted field" should "decode the default when absent and validate" in {
    val tmpl =
      """{"definitions":[{"name":"N","template":{"id":"string",""" +
        """"exclude_from_ad":{"type":"boolean","default":false}}}]}"""
    val driver =
      """package com.example
        |object DefDriver {
        |  def run(): String = {
        |    val n = N.fromJson("{\"id\":\"a\"}").toOption.get   // absent default field
        |    val valid = N.validate("{\"id\":\"a\"}").isRight
        |    s"${n.exclude_from_ad}|$valid|${N.toJson(n)}"
        |  }
        |}
        |""".stripMargin
    val files =
      GeneratedFile("com/example/DefDriver.scala", driver) ::
        new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
          .generateMulti(parser.parseMultiTemplate(tmpl, "com.example").value)
          .value
    runDriver(files, "com.example.DefDriver") shouldBe """false|true|{"id":"a","exclude_from_ad":false}"""
  }
}
