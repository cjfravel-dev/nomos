package dev.cjfravel.nomos.validation

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MultiValidatorSpec extends AnyFlatSpec with Matchers {

  val template =
    MultiTemplate(
      "com.example",
      List(
        TemplateDefinition(
          "User",
          ObjectType(
            ListMap(
              "id" -> FieldDef(StringType(), optional = false),
              "address" -> FieldDef(ReferenceType("Address"), optional = false))),
          Some("models")),
        TemplateDefinition(
          "Address",
          ObjectType(ListMap("zip" -> FieldDef(StringType(List(Pattern("^[0-9]{5}$"))), optional = false))),
          Some("models")),
        TemplateDefinition(
          "Shape",
          TypeDiscriminator(
            "kind",
            ListMap(
              "circle" -> ObjectType(ListMap("r" -> FieldDef(NumberType(List(Min(0))), optional = false))),
              "square" -> ObjectType(ListMap("s" -> FieldDef(NumberType(), optional = false)))),
            ListMap.empty,
            true,
            Map.empty))))
  val v = new MultiValidator(template)

  "MultiValidator" should "resolve references and validate nested objects" in {
    v.validate("""{"id":"a","address":{"zip":"12345"}}""", "User") shouldBe a[Right[_, _]]
    v.validate("""{"id":"a","address":{"zip":"x"}}""", "User") shouldBe a[Left[_, _]]
  }

  it should "validate a discriminator variant" in {
    v.validate("""{"kind":"circle","r":5}""", "Shape") shouldBe a[Right[_, _]]
    v.validate("""{"kind":"circle","r":-1}""", "Shape") shouldBe a[Left[_, _]]
  }

  it should "report an unknown definition" in {
    v.validate("""{}""", "com.example.Nope") shouldBe a[Left[_, _]]
  }

  it should "resolve by fully-qualified name" in {
    v.validate("""{"id":"a","address":{"zip":"12345"}}""", "com.example.models.User") shouldBe a[Right[_, _]]
  }

  it should "fail on malformed json" in {
    v.validate("not json", "User") shouldBe a[Left[_, _]]
  }

  def fmt(format: String) =
    new MultiValidator(
      MultiTemplate(
        "com.example",
        List(
          TemplateDefinition(
            "F",
            ObjectType(ListMap("x" -> FieldDef(StringType(List(Format(format))), optional = false)))))))

  it should "validate all string formats" in {
    fmt("url").validate("""{"x":"https://a.com"}""", "F") shouldBe a[Right[_, _]]
    fmt("url").validate("""{"x":"nope"}""", "F") shouldBe a[Left[_, _]]
    fmt("uuid").validate("""{"x":"12345678-1234-1234-1234-123456789012"}""", "F") shouldBe a[Right[_, _]]
    fmt("uuid").validate("""{"x":"bad"}""", "F") shouldBe a[Left[_, _]]
    fmt("iso8601").validate("""{"x":"2020-01-01T00:00:00Z"}""", "F") shouldBe a[Right[_, _]]
    fmt("alphaNoWhitespace").validate("""{"x":"ab cd"}""", "F") shouldBe a[Left[_, _]]
    fmt("majorAndMinor").validate("""{"x":"1.2"}""", "F") shouldBe a[Right[_, _]]
  }

  it should "enforce number multipleOf and array element types" in {
    val nv =
      new MultiValidator(
        MultiTemplate(
          "com.example",
          List(
            TemplateDefinition(
              "N",
              ObjectType(ListMap(
                "n" -> FieldDef(NumberType(List(MultipleOf(2))), optional = false),
                "items" -> FieldDef(ArrayType(NumberType()), optional = false)))))))
    nv.validate("""{"n":4,"items":[1,2]}""", "N") shouldBe a[Right[_, _]]
    nv.validate("""{"n":3,"items":["x"]}""", "N") shouldBe a[Left[_, _]]
  }

  it should "reject unexpected extra fields" in {
    v.validate("""{"id":"a","address":{"zip":"12345"},"extra":1}""", "User") shouldBe a[Left[_, _]]
  }

  it should "validate recursive references" in {
    val tree =
      new MultiValidator(
        MultiTemplate(
          "com.example",
          List(TemplateDefinition(
            "TreeNode",
            ObjectType(ListMap(
              "id" -> FieldDef(StringType(), optional = false),
              "children" -> FieldDef(ArrayType(RecursiveRef("TreeNode")), optional = false)))))))
    tree.validate("""{"id":"r","children":[{"id":"c","children":[]}]}""", "TreeNode") shouldBe a[Right[_, _]]
  }

  it should "reject unknown discriminator values and bad common fields" in {
    val sv =
      new MultiValidator(
        MultiTemplate(
          "com.example",
          List(TemplateDefinition(
            "S",
            TypeDiscriminator(
              "k",
              ListMap("a" -> ObjectType(ListMap("x" -> FieldDef(NumberType(), optional = false)))),
              ListMap("ts" -> FieldDef(StringType(), optional = false)),
              true,
              Map.empty)))))
    sv.validate("""{"k":"a","ts":"t","x":1}""", "S") shouldBe a[Right[_, _]]
    sv.validate("""{"k":"nope","x":1}""", "S") shouldBe a[Left[_, _]]
    sv.validate("""{"k":"a","x":1}""", "S") shouldBe a[Left[_, _]]
  }
}
