package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig, TemplateSerializer}
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

class DateTypeSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String) = parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "parser" should "parse date and datetime types" in {
    val d = parse("""{"name":"N","template":{"d":"date","ts":"datetime"}}""").value.definitions.head
    val f = d.templateType.asInstanceOf[ObjectType].fields
    f("d").fieldType shouldBe DateType()
    f("ts").fieldType shouldBe DateTimeType()
  }

  "generator" should "map to java.time types" in {
    val t = parse("""{"name":"N","template":{"d":"date","ts":"datetime"}}""").value
    val content = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(t).value.find(_.fileName == "N.scala").get.content
    content should include("d: java.time.LocalDate")
    content should include("ts: java.time.LocalDateTime")
  }

  "generated codec" should "decode and encode dates via java.time without a JSON library" in {
    val t = parse("""{"name":"N","template":{"d":"date"}}""").value
    val content = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(t).value.find(_.fileName == "N.scala").get.content
    content should include("""Codecs.temporal[java.time.LocalDate]("date"""")
    content should include("JsonString(obj.d.toString)")
  }

  "validator" should "accept ISO dates and reject malformed ones" in {
    val t = MultiTemplate("com.example", List(TemplateDefinition("N", ObjectType(ListMap(
      "d" -> FieldDef(DateType(), false), "ts" -> FieldDef(DateTimeType(), false))))))
    val v = new MultiValidator(t)
    v.validate("""{"d":"2024-01-02","ts":"2024-01-02T03:04:05"}""", "N") shouldBe a[Right[_, _]]
    v.validate("""{"d":"not-a-date","ts":"2024-01-02T03:04:05"}""", "N") shouldBe a[Left[_, _]]
  }

  "datetime validation" should "accept UTC instants, offsets, and fractional seconds" in {
    val t = MultiTemplate("com.example", List(TemplateDefinition("N", ObjectType(ListMap(
      "ts" -> FieldDef(DateTimeType(), false))))))
    val v = new MultiValidator(t)
    // Trailing Z (the dominant real-world form), incl. fractional seconds, and explicit offsets.
    v.validate("""{"ts":"2025-11-11T11:11:11Z"}""", "N") shouldBe a[Right[_, _]]
    v.validate("""{"ts":"2023-03-29T05:24:36.645000Z"}""", "N") shouldBe a[Right[_, _]]
    v.validate("""{"ts":"2024-01-02T03:04:05+02:00"}""", "N") shouldBe a[Right[_, _]]
    // Naive local date-times still validate.
    v.validate("""{"ts":"2024-01-02T03:04:05"}""", "N") shouldBe a[Right[_, _]]
    // Genuinely malformed values are still rejected.
    v.validate("""{"ts":"nope"}""", "N") shouldBe a[Left[_, _]]
  }

  "serializer" should "round-trip date types" in {
    TemplateSerializer.serializeTemplateType(DateType()) shouldBe "DateType()"
    TemplateSerializer.serializeTemplateType(DateTimeType()) shouldBe "DateTimeType()"
  }
}
