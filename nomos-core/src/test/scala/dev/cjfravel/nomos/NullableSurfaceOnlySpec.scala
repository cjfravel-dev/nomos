package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation._
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NullableSurfaceOnlySpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  private val parser = new TemplateParser()
  private val template =
    """{"definitions":[{"name":"Record","template":{
      |"region":{"$optional":"string","nullable":"surfaceOnly"}}}]}""".stripMargin

  "nullable surfaceOnly" should "parse as a nullable surface that rejects explicit null" in {
    val field =
      parser
        .parseMultiTemplate(template, "com.example")
        .value
        .definitions
        .head
        .templateType
        .asInstanceOf[ObjectType]
        .fields("region")

    field.optional shouldBe true
    field.nullable shouldBe true
    field.rejectExplicitNull shouldBe true
    field.acceptsNull shouldBe false
  }

  it should "generate a raw null-defaulted field and reject explicit null in validation and decoding" in {
    val generated =
      new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
        .generateMulti(parser.parseMultiTemplate(template, "com.example").value)
        .value
    val record = generated.find(_.fileName == "Record.scala").get.content
    record should include("region: String = null")
    record should include("Codecs.optionalNonNull")

    val driver =
      """package com.example
        |object NullableSurfaceOnlyDriver {
        |  def run(): String = {
        |    val absent = Record.fromJson("{}").right.get.region == null
        |    val present = Record.fromJson("{\"region\":\"west\"}").right.get.region
        |    val decodeRejects = Record.fromJson("{\"region\":null}").isLeft
        |    val validationPath = Record.validate("{\"region\":null}").left.get.head.path
        |    s"$absent|$present|$decodeRejects|$validationPath"
        |  }
        |}
        |""".stripMargin

    runDriver(
      GeneratedFile("com/example/NullableSurfaceOnlyDriver.scala", driver) :: generated,
      "com.example.NullableSurfaceOnlyDriver") shouldBe "true|west|true|root.region"
  }

  it should "reject unsupported nullable values" in {
    val invalid =
      """{"definitions":[{"name":"Record","template":{
        |"region":{"$optional":"string","nullable":"sometimes"}}}]}""".stripMargin
    parser.parseMultiTemplate(invalid, "com.example").left.value.toString should include("surfaceOnly")
  }

  it should "be retained in embedded templates" in {
    val field = FieldDef(StringType(), optional = true, nullable = true, rejectExplicitNull = true)
    TemplateSerializer.serializeFieldDef(field) should include("rejectExplicitNull = true")
  }
}
