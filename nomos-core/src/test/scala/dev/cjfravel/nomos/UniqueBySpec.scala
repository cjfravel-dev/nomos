package dev.cjfravel.nomos

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.generation.TemplateSerializer
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.{ParseError, TemplateParser}
import dev.cjfravel.nomos.validation.{MultiValidator, ValidationError}
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UniqueBySpec extends AnyFlatSpec with Matchers with EitherValues {

  private val parser = new TemplateParser()

  private def parse(json: String): Either[ParseError, MultiTemplate] =
    parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  private def arrayOf(json: String): ArrayType =
    parse(json).value.definitions.head.templateType
      .asInstanceOf[ObjectType]
      .fields("items")
      .fieldType
      .asInstanceOf[ArrayType]

  private def validator(constraints: List[Constraint]): MultiValidator =
    MultiValidator(
      MultiTemplate(
        "com.example",
        List(TemplateDefinition(
          "N",
          ObjectType(ListMap("items" -> FieldDef(ArrayType(
            ObjectType(ListMap("id" -> FieldDef(StringType()), "label" -> FieldDef(StringType()))),
            constraints))))))))

  private def errors(result: Either[List[ValidationError], _]): List[ValidationError] = result.left.value

  "parser" should "parse uniqueBy from a single field name" in {
    arrayOf(
      """{"name":"N","template":{"items":{"type":"array","items":"string","uniqueBy":"id"}}}""").constraints should
      contain(UniqueBy(List("id")))
  }

  it should "parse uniqueBy from a list of field names" in {
    val template = """{"name":"N","template":{"items":{"type":"array","items":"string","uniqueBy":["g","name"]}}}"""
    arrayOf(template).constraints should contain(UniqueBy(List("g", "name")))
  }

  it should "ignore an empty or non-string uniqueBy" in {
    arrayOf(
      """{"name":"N","template":{"items":{"type":"array","items":"string","uniqueBy":[]}}}""").constraints shouldBe
      empty
    arrayOf("""{"name":"N","template":{"items":{"type":"array","items":"string","uniqueBy":7}}}""").constraints shouldBe
      empty
  }

  "validator" should "reject elements sharing a uniqueBy field even when other fields differ" in {
    val v = validator(List(UniqueBy(List("id"))))
    v.validate("""{"items":[{"id":"a","label":"x"},{"id":"b","label":"x"}]}""", "N") shouldBe a[Right[_, _]]
    val failure = errors(v.validate("""{"items":[{"id":"a","label":"x"},{"id":"a","label":"y"}]}""", "N"))
    failure.map(_.path) shouldBe List("root.items[1]")
    failure.head.message should include("uniqueBy: id")
    failure.head.actual should include("\"a\"")
  }

  it should "accept duplicates that uniqueItems alone would allow" in {
    val v = validator(List(UniqueItems(true)))
    v.validate("""{"items":[{"id":"a","label":"x"},{"id":"a","label":"y"}]}""", "N") shouldBe a[Right[_, _]]
  }

  it should "treat several field names as a composite key" in {
    val v = validator(List(UniqueBy(List("id", "label"))))
    v.validate("""{"items":[{"id":"a","label":"x"},{"id":"a","label":"y"}]}""", "N") shouldBe a[Right[_, _]]
    errors(v.validate("""{"items":[{"id":"a","label":"x"},{"id":"a","label":"x"}]}""", "N")).head.path shouldBe
      "root.items[1]"
  }

  it should "report every duplicate after the first" in {
    val v = validator(List(UniqueBy(List("id"))))
    val failure =
      errors(v.validate("""{"items":[{"id":"a","label":"x"},{"id":"a","label":"y"},{"id":"a","label":"z"}]}""", "N"))
    failure.map(_.path) shouldBe List("root.items[1]", "root.items[2]")
  }

  it should "skip elements that are not objects or that omit the key field" in {
    val elementFree =
      MultiValidator(
        MultiTemplate(
          "com.example",
          List(
            TemplateDefinition(
              "N",
              ObjectType(ListMap("items" -> FieldDef(ArrayType(StringType(), List(UniqueBy(List("id")))))))))))
    elementFree.validate("""{"items":["a","a"]}""", "N") shouldBe a[Right[_, _]]

    val optionalKey =
      MultiValidator(
        MultiTemplate(
          "com.example",
          List(
            TemplateDefinition(
              "N",
              ObjectType(ListMap("items" -> FieldDef(ArrayType(
                ObjectType(ListMap("id" -> FieldDef(StringType(), optional = true))),
                List(UniqueBy(List("id")))))))))))
    optionalKey.validate("""{"items":[{},{}]}""", "N") shouldBe a[Right[_, _]]
  }

  "serializer" should "round-trip uniqueBy" in {
    TemplateSerializer.serializeConstraint(UniqueBy(List("group", "name"))) shouldBe
      """UniqueBy(List("group", "name"))"""
  }
}
