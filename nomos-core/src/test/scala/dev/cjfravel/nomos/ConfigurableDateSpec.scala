package dev.cjfravel.nomos

import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

class ConfigurableDateSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def content(t: String, cfg: GeneratorConfig) =
    new CodeGenerator(cfg).generateMulti(parser.parseMultiTemplate(t, "com.example").value).value
      .find(_.fileName == "N.scala").get.content

  "default date types" should "be java.time" in {
    val c = content("""{"definitions":[{"name":"N","template":{"d":"date","ts":"datetime"}}]}""",
      GeneratorConfig("com.example", "target/test-gen"))
    c should include("d: java.time.LocalDate")
    c should include("ts: java.time.LocalDateTime")
  }

  "parser" should "read dateType/dateTimeType from the template" in {
    val t = parser.parseMultiTemplate(
      """{"dateType":"java.util.Date","dateTimeType":"java.util.Date","definitions":[{"name":"N","template":{"d":"date"}}]}""",
      "com.example").value
    t.dateType shouldBe "java.util.Date"
    t.dateTimeType shouldBe "java.util.Date"
  }

  "configured date type" should "be used in generated fields" in {
    val c = content("""{"definitions":[{"name":"N","template":{"d":"date","ts":"datetime"}}]}""",
      GeneratorConfig("com.example", "target/test-gen", dateType = "java.util.Date", dateTimeType = "java.util.Date"))
    c should include("d: java.util.Date")
    c should include("ts: java.util.Date")
  }
}
