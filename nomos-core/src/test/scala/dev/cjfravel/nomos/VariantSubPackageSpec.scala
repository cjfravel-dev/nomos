package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class VariantSubPackageSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  val tmpl =
    """{"definitions":[{"name":"Shape","subPackage":"shapes","template":{"$type":{"discriminator":"kind",
       "variantSubPackage":"kinds","variants":{"circle":{"radius":"number"},"square":{"side":"number"}}}}}]}"""

  "parser" should "capture variantSubPackage" in {
    val disc =
      parser.parseMultiTemplate(tmpl, "com.example").value.definitions.head.templateType.asInstanceOf[TypeDiscriminator]
    disc.variantSubPackage shouldBe Some("kinds")
  }

  "generator" should "place variant case classes in the sub-package" in {
    val t = parser.parseMultiTemplate(tmpl, "com.example").value
    val content =
      new CodeGenerator(GeneratorConfig("", "target/test-gen"))
        .generateMulti(t)
        .value
        .find(_.fileName == "Shape.scala")
        .get
        .content
    content should include("sealed trait Shape")
    content should include("package kinds {")
    content should include("import _root_.com.example.shapes.kinds._")
    content should include("case class Circle")
  }

  "generator" should "also honor variantSubPackage in the variantNames path" in {
    val vn =
      """{"definitions":[{"name":"Shape","subPackage":"shapes","template":{"$type":{"discriminator":"kind",
         "variantSubPackage":"kinds","variantNames":{"circle":"RoundShape"},
         "variants":{"circle":{"radius":"number"}}}}}]}"""
    val t = parser.parseMultiTemplate(vn, "com.example").value
    val content =
      new CodeGenerator(GeneratorConfig("", "target/test-gen"))
        .generateMulti(t)
        .value
        .find(_.fileName == "Shape.scala")
        .get
        .content
    content should include("package kinds {")
    content should include("case class RoundShape")
    // the relocated variant must be imported back into the companion
    content should include("import _root_.com.example.shapes.kinds._")
  }
}
