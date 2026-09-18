package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import dev.cjfravel.nomos.model.TypeDiscriminator
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RegexDiscriminatorSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val parser = new TemplateParser()
  private val template =
    """{"definitions":[{"name":"Col","template":{"$type":{"discriminator":"type","variantMatch":"regex",
      |"variantNames":{"^Decimal(\\((\\d{1,2}),(\\d{1,2})\\))?$":"DecimalCol","^String$":"StringCol"},
      |"variants":{"^Decimal(\\((\\d{1,2}),(\\d{1,2})\\))?$":{"scale":"int"},"^String$":{}}}}}]}""".stripMargin

  "parser" should "capture variantMatch=regex" in {
    val disc =
      parser
        .parseMultiTemplate(template, "com.example")
        .value
        .definitions
        .head
        .templateType
        .asInstanceOf[TypeDiscriminator]
    disc.variantMatch shouldBe "regex"
  }

  it should "require variantNames for regex matching" in {
    val result =
      parser.parseMultiTemplate(
        """{"definitions":[{"name":"Col","template":{"$type":{"discriminator":"type","variantMatch":"regex",
        |"variants":{"^Decimal$":{}}}}}]}""".stripMargin,
        "com.example")

    result.left.value.message should include("variantNames is required")
  }

  it should "reject invalid variant regular expressions" in {
    val result =
      parser.parseMultiTemplate(
        """{"definitions":[{"name":"Col","template":{"$type":{"discriminator":"type","variantMatch":"regex",
        |"variantNames":{"[":"DecimalCol"},"variants":{"[":{}}}}}]}""".stripMargin,
        "com.example")

    result.left.value.message should include("invalid regular expression")
  }

  "validator" should "match the entire discriminator value" in {
    val validator = new MultiValidator(parser.parseMultiTemplate(template, "com.example").value)

    validator.validate("""{"type":"Decimal","scale":0}""", "Col") shouldBe a[Right[_, _]]
    validator.validate("""{"type":"Decimal(28,8)","scale":8}""", "Col") shouldBe a[Right[_, _]]
    validator.validate("""{"type":"String"}""", "Col") shouldBe a[Right[_, _]]
    List("Decimal(28,8", "Decimal()", "Decimal(nonsense)", "DecimalSomethingElse").foreach { value =>
      validator.validate(s"""{"type":"$value","scale":8}""", "Col") shouldBe a[Left[_, _]]
    }
  }

  it should "use the first matching pattern and preserve fallback behavior" in {
    val ordered =
      """{"definitions":[{"name":"Choice","template":{"$type":{"discriminator":"kind","variantMatch":"regex",
        |"fallbackVariant":"UnknownChoice","variantNames":{"^a.*$":"Broad","^abc$":"Exact"},
        |"variants":{"^a.*$":{"broad":"string"},"^abc$":{"exact":"string"}}}}}]}""".stripMargin
    val validator = new MultiValidator(parser.parseMultiTemplate(ordered, "com.example").value)

    validator.validate("""{"kind":"abc","broad":"x"}""", "Choice") shouldBe a[Right[_, _]]
    validator.validate("""{"kind":"abc","exact":"x"}""", "Choice") shouldBe a[Left[_, _]]
    validator.validate("""{"kind":"zzz","anything":true}""", "Choice") shouldBe a[Right[_, _]]
  }

  "code generator" should "emit full regex dispatch and preserve the matched value" in {
    val content =
      new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
        .generateMulti(parser.parseMultiTemplate(template, "com.example").value)
        .value
        .find(_.fileName == "Col.scala")
        .get
        .content

    content should include("d2.matches(\"^Decimal")
    content should include("override val `type`: String")
    content should include("val scale: Int")
    content should include("yield DecimalCol(d, scale)")
  }
}
