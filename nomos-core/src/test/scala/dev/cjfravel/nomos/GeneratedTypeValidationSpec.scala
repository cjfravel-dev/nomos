package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratedFile, GeneratorConfig}
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.validation.{ValidationError, ValidatorRegistry}
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GeneratedTypeValidationSpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  private val parser = new TemplateParser()

  private def generate(template: String, basePackage: String): List[GeneratedFile] =
    new CodeGenerator(GeneratorConfig(basePackage, "target/test-gen"))
      .generateMulti(parser.parseMultiTemplate(template, basePackage).value)
      .value

  "a $gen field" should "compose structural and custom validation with the caller path" in {
    ValidatorRegistry.register("producer.contact") { ctx =>
      if (ctx.node.asObject.flatMap(_.field("contact_id")).flatMap(_.asString).contains("blocked"))
        List(ValidationError(s"${ctx.path}.contact_id", "blocked contact", "allowed", "blocked"))
      else Nil
    }
    val producer =
      """{"visibility":"private[example]","definitions":[
        |{"name":"Producer","validators":["producer.contact"],"template":{
        |"contact_id":{"type":"string","minLength":3}}}]}""".stripMargin
    val consumer =
      """{"definitions":[{"name":"Record","template":{
        |"id":"string","producer":"$gen:com.example.producer.Producer"}}]}""".stripMargin
    val driver =
      """package com.example.consumer
        |object GeneratedValidationDriver {
        |  def run(): String = {
        |    val structuralJson = "{\"id\":\"r1\",\"producer\":{\"contact_id\":1}}"
        |    val customJson = "{\"id\":\"r1\",\"producer\":{\"contact_id\":\"blocked\"}}"
        |    val structural = Record.validate(structuralJson).left.toOption.get.head.path
        |    val custom = Record.validate(customJson).left.toOption.get.head.path
        |    structural + "|" + custom
        |  }
        |}
        |""".stripMargin

    val (producerErrors, producerClasses) = compile(generate(producer, "com.example.producer"))
    producerErrors shouldBe empty
    val consumerFiles =
      generate(consumer, "com.example.consumer") :::
        List(GeneratedFile("com/example/consumer/GeneratedValidationDriver.scala", driver))
    runDriver(consumerFiles, "com.example.consumer.GeneratedValidationDriver", Seq(producerClasses)) shouldBe
      "root.producer.contact_id|root.producer.contact_id"
  }

  it should "emit direct validation delegation without a runtime registry" in {
    val consumer =
      """{"definitions":[{"name":"Record","template":{
        |"producer":"$gen:com.other.Producer"}}]}""".stripMargin
    val formats = generate(consumer, "com.consumer").find(_.fileName == "NomosFormats.scala").get.content
    formats should include("com.other.Producer.validateStructure")
    formats should include("com.other.Producer.validateCustom")
  }
}
