package dev.cjfravel.nomos

import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

class PrefixDispatchSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def gen(t: String) = {
    val tpl = parser.parseMultiTemplate(t, "com.example").value
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen")).generateMulti(tpl).value.find(_.fileName == "Col.scala").get.content
  }

  "prefix discriminator codegen" should "emit startsWith dispatch in fromJson" in {
    val content = gen("""{"definitions":[{"name":"Col","template":{"$type":{"discriminator":"type","variantMatch":"prefix",
      "variants":{"Decimal":{"scale":"int"},"String":{}}}}}]}""")
    content should include("startsWith(\"Decimal\")")
    content should not include "case \"Decimal\" =>"
  }

  "exact discriminator codegen" should "still emit exact dispatch" in {
    val content = gen("""{"definitions":[{"name":"Col","template":{"$type":{"discriminator":"type",
      "variants":{"Decimal":{"scale":"int"},"String":{}}}}}]}""")
    content should include("case \"Decimal\" =>")
    content should not include "startsWith"
  }

  "prefix discriminator with variantNames" should "emit startsWith dispatch" in {
    val content = gen("""{"definitions":[{"name":"Col","template":{"$type":{"discriminator":"type","variantMatch":"prefix",
      "variantNames":{"Decimal":"DecimalCol","String":"StringCol"},
      "variants":{"Decimal":{"scale":"int"},"String":{}}}}}]}""")
    content should include("startsWith(\"Decimal\")")
  }
}
