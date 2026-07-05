package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DeferRefValidationSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  val rootOnly = """{"definitions":[{"name":"Root","template":{"child":"$ref:Child"}}]}"""

  "strict parse" should "reject a cross-file ref it cannot resolve" in {
    parser.parseMultiTemplate(rootOnly, "com.example.a") shouldBe a[Left[_, _]]
  }

  "lenient parse" should "defer cross-file refs for later merge" in {
    parser.parseMultiTemplate(rootOnly, "com.example.a", validateRefs = false) shouldBe a[Right[_, _]]
  }

  "combine" should "validate refs across merged templates" in {
    val r = parser.parseMultiTemplate(rootOnly, "com.example.a", validateRefs = false).value
    val c =
      parser
        .parseMultiTemplate(
          """{"definitions":[{"name":"Child","template":{"id":"string"}}]}""",
          "com.example.b",
          validateRefs = false)
        .value
    MultiTemplate.combine(List(r, c)).value.validate() shouldBe empty
  }
}

class CollapseVariantsSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  val tmpl = """{"definitions":[{"name":"Num","template":{"$type":{"discriminator":"type",
    "variantNames":{"int":"Number","long":"Number","double":"Number"},
    "variants":{"int":{},"long":{},"double":{}}}}}]}"""

  "generator" should "collapse identical variants into one shared class" in {
    val t = parser.parseMultiTemplate(tmpl, "com.example").value
    val content =
      new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
        .generateMulti(t)
        .value
        .find(_.fileName == "Num.scala")
        .get
        .content
    "case class Number".r.findAllIn(content).size shouldBe 1
    content should not include "case class Int"
    content should not include "case class Double"
  }
}
