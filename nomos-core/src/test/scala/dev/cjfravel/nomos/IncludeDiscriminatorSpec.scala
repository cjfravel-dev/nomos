package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation._
import dev.cjfravel.nomos.parser.TemplateParser
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * With `includeDiscriminator: false` the discriminator was dropped from the model and from encode output, yet decode
 * still required it — so `fromJson(toJson(x))` could not decode a union's own output. That mode is rejected at
 * generation with a clear message; the supported (included) discriminator round-trips its own output, which `runDriver`
 * compiles and executes to prove.
 */
class IncludeDiscriminatorSpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  private val parser = new TemplateParser()
  private val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))

  private def generate(template: String): List[GeneratedFile] =
    gen.generateMulti(parser.parseMultiTemplate(template, "com.example").value).value

  private val shapeTmpl =
    """{"definitions":[{"name":"Shape","template":{"$type":{
      |"discriminator":"kind","variants":{"circle":{"radius":"int"},"square":{"side":"int"}}}}}]}""".stripMargin

  "generateMulti" should "reject a discriminator with includeDiscriminator=false" in {
    val t =
      parser
        .parseMultiTemplate(
          """{"definitions":[{"name":"Shape","template":{"$type":{
        |"discriminator":"kind","includeDiscriminator":false,
        |"variants":{"circle":{"radius":"int"},"square":{"side":"int"}}}}}]}""".stripMargin,
          "com.example")
        .value
    val err = gen.generateMulti(t).left.value
    err shouldBe a[GeneratorError.TemplateError]
    err.message should include("includeDiscriminator")
  }

  "an includeDiscriminator=true union" should "decode its own encoded output (fromJson(toJson))" in {
    val driver =
      """package com.example
        |object ShapeRt {
        |  def run(): String = {
        |    val c = Circle(5)
        |    Shape.fromJson(Shape.toJson(c)) match {
        |      case Right(c2) => "ok:" + (c2 == c)
        |      case Left(e)   => "fail:" + e
        |    }
        |  }
        |}
        |""".stripMargin
    val files = GeneratedFile("com/example/ShapeRt.scala", driver) :: generate(shapeTmpl)
    runDriver(files, "com.example.ShapeRt") shouldBe "ok:true"
  }
}
