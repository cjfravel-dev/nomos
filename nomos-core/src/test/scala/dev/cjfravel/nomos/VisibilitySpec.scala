package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratedFile, GeneratorConfig}
import dev.cjfravel.nomos.parser.TemplateParser
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The top-level `visibility` option prepends a Scala access modifier to every generated top-level definition (case
 * classes, sealed traits, enums, companion/NomosFormats objects), so a module can lock its surface to an enclosing
 * package. The modifier is validated (not truly verbatim) to keep it from breaking out of the definition position in
 * generated source.
 */
class VisibilitySpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  private val parser = new TemplateParser()

  private def generate(template: String): List[GeneratedFile] =
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(parser.parseMultiTemplate(template, "com.example").value)
      .value

  "visibility" should "prefix every generated top-level definition" in {
    val tmpl =
      """{"visibility":"private[example]","definitions":[
      |{"name":"User","template":{"id":"string","age":"int"}}]}""".stripMargin
    val files = generate(tmpl)
    val user = files.find(_.fileName == "User.scala").get.content
    user should include("private[example] case class User(")
    user should include("private[example] object User {")
    files.find(_.fileName == "NomosFormats.scala").get.content should include("private[example] object NomosFormats {")
  }

  "package-private generated types" should "compile and be usable from within the enclosing package" in {
    val tmpl =
      """{"visibility":"private[example]","definitions":[
      |{"name":"User","template":{"id":"string","age":"int"}}]}""".stripMargin
    val driver =
      """package com.example
        |object VisDriver {
        |  def run(): String = {
        |    val u = User("u1", 7)                 // in-package construction of a private type
        |    val json = User.toJson(u)
        |    val back = User.fromJson(json).toOption.get
        |    s"${u.id}|$json|${back == u}"
        |  }
        |}
        |""".stripMargin
    val files = GeneratedFile("com/example/VisDriver.scala", driver) :: generate(tmpl)
    runDriver(files, "com.example.VisDriver") shouldBe """u1|{"id":"u1","age":7}|true"""
  }

  "visibility on a discriminated union with a discriminatorEnum" should
    "apply to the trait, variants, enum, and companions and compile" in {
      val tmpl =
        """{"visibility":"private[example]","definitions":[
      |{"name":"Shape","template":{"$type":{"discriminator":"kind","discriminatorEnum":"ShapeType",
      |"variants":{"circle":{"radius":"int"}}}}}]}""".stripMargin
      val files = generate(tmpl)
      val shape = files.find(_.fileName == "Shape.scala").get.content
      shape should include("private[example] sealed trait Shape")
      shape should include("private[example] case class Circle(")
      shape should include("private[example] object Shape {")
      val enumFile = files.find(_.fileName == "ShapeType.scala").get.content
      enumFile should include("private[example] sealed trait ShapeType")
      enumFile should include("private[example] object ShapeType {")
      compileErrors(files) shouldBe empty
    }

  "an unsafe visibility modifier" should "be rejected (cannot inject into generated source)" in {
    val bad =
      """{"visibility":"private[x] } object Evil { def hack = 1 ; ","definitions":[
      |{"name":"User","template":{"id":"string"}}]}""".stripMargin
    parser.parseMultiTemplate(bad, "com.example").left.value.toString should include("Invalid visibility modifier")
  }

  it should "accept ordinary access modifiers" in {
    Seq("private", "protected", "private[this]", "private[example]", "private[com.example]").foreach { v =>
      val tmpl = s"""{"visibility":"$v","definitions":[{"name":"User","template":{"id":"string"}}]}"""
      parser.parseMultiTemplate(tmpl, "com.example") shouldBe a[Right[_, _]]
    }
  }
}
