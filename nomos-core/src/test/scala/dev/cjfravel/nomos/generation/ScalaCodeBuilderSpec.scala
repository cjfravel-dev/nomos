package dev.cjfravel.nomos.generation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScalaCodeBuilderSpec extends AnyFlatSpec with Matchers {

  "ScalaCodeBuilder" should "indent, dedent and build lines" in {
    val b = ScalaCodeBuilder()
    b.line("object X {").indent().line("val a = 1").dedent().line("}")
    val out = b.build()
    out should include("  val a = 1")
    out should include("object X {")
  }

  it should "honor a custom indentation width" in {
    val b = new ScalaCodeBuilder(indentSize = 4)
    b.line("object X {").indent().line("val a = 1").dedent().line("}")
    b.build() should include("\n    val a = 1\n")
  }

  it should "emit case classes with and without parent" in {
    val b = ScalaCodeBuilder()
    b.caseClass("User", List(("id", "String"), ("age", "Int")), Some("Base"))
    b.build() should include("case class User(")
    b.build() should include("extends Base")
  }

  it should "emit override fields and sealed traits" in {
    val b = ScalaCodeBuilder()
    b.sealedTraitWithFields("Shape", List(("kind", "String")))
    b.caseClassWithOverride("Circle", List(("kind", "String", true), ("r", "Double", false)), Some("Shape"))
    val out = b.build()
    out should include("sealed trait Shape")
    out should include("override")
  }

  it should "escape keywords and convert cases" in {
    ScalaCodeBuilder.escapeKeyword("type") shouldBe "`type`"
    ScalaCodeBuilder.escapeKeyword("name") shouldBe "name"
    ScalaCodeBuilder.toCamelCase("user_name") shouldBe "userName"
    ScalaCodeBuilder.toPascalCase("user_name") shouldBe "UserName"
  }
}
