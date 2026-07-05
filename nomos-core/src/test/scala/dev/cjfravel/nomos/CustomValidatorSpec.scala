package dev.cjfravel.nomos

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.generation.TemplateSerializer
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.validation.{MultiValidator, ValidationError, ValidatorRegistry}
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CustomValidatorSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()

  "parser" should "parse definition-level validators list" in {
    val json =
      """{"definitions":[{"name":"Reservation","validators":["dates.startBeforeEnd"],"template":{"startDate":"string","endDate":"string"}}]}"""
    parser.parseMultiTemplate(json, "com.example").value.definitions.head.validators shouldBe List(
      "dates.startBeforeEnd")
  }

  it should "default validators to empty" in {
    parser
      .parseMultiTemplate("""{"definitions":[{"name":"N","template":{"id":"string"}}]}""", "com.example")
      .value
      .definitions
      .head
      .validators shouldBe Nil
  }

  def multi =
    MultiTemplate(
      "com.example",
      List(
        TemplateDefinition(
          "R",
          ObjectType(ListMap("startDate" -> FieldDef(StringType(), false), "endDate" -> FieldDef(StringType(), false))),
          validators = List("dates.startBeforeEnd"))))

  "MultiValidator" should "run registered named validators after schema validation" in {
    ValidatorRegistry.register("dates.startBeforeEnd") { ctx =>
      val fields = ctx.node.asObject.map(_.fieldMap).getOrElse(Map.empty)
      val start = fields.get("startDate").flatMap(_.asString)
      val end = fields.get("endDate").flatMap(_.asString)
      if (start.zip(end).exists { case (s, e) => s <= e }) Nil
      else List(ValidationError("endDate", "must be >= startDate", "ordered", "unordered"))
    }
    val v = new MultiValidator(multi)
    v.validate("""{"startDate":"2024-01-01","endDate":"2024-01-05"}""", "R") shouldBe a[Right[_, _]]
    v.validate("""{"startDate":"2024-01-09","endDate":"2024-01-05"}""", "R") shouldBe a[Left[_, _]]
  }

  it should "skip custom validators when schema is already invalid" in {
    val v = new MultiValidator(multi)
    v.validate("""{"startDate":1}""", "R") shouldBe a[Left[_, _]]
  }

  "serializer" should "round-trip definition validators" in {
    TemplateSerializer.serializeDefinition(multi.definitions.head) should include(
      "validators = List(\"dates.startBeforeEnd\")")
  }
}
