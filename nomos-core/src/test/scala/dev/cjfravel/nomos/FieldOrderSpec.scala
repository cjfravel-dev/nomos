package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import dev.cjfravel.nomos.parser.TemplateParser
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FieldOrderSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()

  "generated case class" should "preserve template field declaration order" in {
    val t =
      parser
        .parseMultiTemplate(
          """{"definitions":[{"name":"N","template":{"zebra":"string","alpha":"string","middle":"number","beta":"boolean"}}]}""",
          "com.example")
        .value
    val content =
      new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
        .generateMulti(t)
        .value
        .find(_.fileName == "N.scala")
        .get
        .content
    val idxZebra = content.indexOf("zebra:")
    val idxAlpha = content.indexOf("alpha:")
    val idxMiddle = content.indexOf("middle:")
    val idxBeta = content.indexOf("beta:")
    idxZebra should be < idxAlpha
    idxAlpha should be < idxMiddle
    idxMiddle should be < idxBeta
  }
}
