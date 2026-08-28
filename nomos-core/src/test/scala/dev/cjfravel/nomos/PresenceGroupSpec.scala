package dev.cjfravel.nomos

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.generation.TemplateSerializer
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.{ParseError, TemplateParser}
import dev.cjfravel.nomos.validation.{MultiValidator, ValidationError}
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PresenceGroupSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val parser = new TemplateParser()

  private def parse(template: String): Either[ParseError, MultiTemplate] =
    parser.parseMultiTemplate(s"""{"definitions":[{"name":"N","template":$template}]}""", "com.example")

  private def objectType(template: String): ObjectType =
    parse(template).value.definitions.head.templateType.asInstanceOf[ObjectType]

  private def validator(groups: List[PresenceGroup]): MultiValidator =
    MultiValidator(
      MultiTemplate(
        "com.example",
        List(
          TemplateDefinition(
            "N",
            ObjectType(
              ListMap(
                "inline_value" -> FieldDef(StringType(), optional = true),
                "value_ref" -> FieldDef(StringType(), optional = true)),
              ForbidExtra,
              groups)))))

  private def errors(result: Either[List[ValidationError], _]): List[ValidationError] = result.left.value

  private val bothOptional =
    """{"$oneOf":["inline_value","value_ref"],""" +
      """"inline_value":{"$optional":"string"},"value_ref":{"$optional":"string"}}"""

  "parser" should "parse a $oneOf group without treating it as a field" in {
    val obj = objectType(bothOptional)
    obj.fields.keys.toList shouldBe List("inline_value", "value_ref")
    obj.presence shouldBe List(PresenceGroup(List("inline_value", "value_ref"), ExactlyOne))
  }

  it should "parse an $atLeastOne group and an $optional group" in {
    objectType("""{"$atLeastOne":["a","b"],"a":{"$optional":"string"},"b":{"$optional":"string"}}""").presence shouldBe
      List(PresenceGroup(List("a", "b"), AtLeastOne))
    objectType(
      """{"$oneOf":{"$optional":["a","b"]},"a":{"$optional":"string"},"b":{"$optional":"string"}}""").presence shouldBe
      List(PresenceGroup(List("a", "b"), ExactlyOne, optional = true))
  }

  it should "parse both group kinds on one object" in {
    objectType(
      """{"$oneOf":["a","b"],"$atLeastOne":["b","c"],""" +
        """"a":{"$optional":"string"},"b":{"$optional":"string"},"c":{"$optional":"string"}}""").presence shouldBe
      List(PresenceGroup(List("a", "b"), ExactlyOne), PresenceGroup(List("b", "c"), AtLeastOne))
  }

  it should "reject a group that is not an array of field names" in {
    parse("""{"$oneOf":"a","a":{"$optional":"string"}}""").left.value.message should include("$oneOf")
    parse("""{"$oneOf":["a",1],"a":{"$optional":"string"}}""").left.value.message should include(
      "an array of sibling field names")
  }

  it should "reject a group with fewer than two keys" in {
    parse("""{"$oneOf":["a"],"a":{"$optional":"string"}}""").left.value.message should include("at least two keys")
  }

  it should "reject duplicate, undeclared, and required keys" in {
    parse("""{"$oneOf":["a","a"],"a":{"$optional":"string"}}""").left.value.message should include("duplicate keys: a")
    parse("""{"$oneOf":["a","z"],"a":{"$optional":"string"}}""").left.value.message should include(
      "undeclared sibling keys: z")
    parse("""{"$oneOf":["a","b"],"a":{"$optional":"string"},"b":"string"}""").left.value.message should include(
      "must be optional, but these are required: b")
  }

  it should "reject a group on discriminator commonFields" in {
    val template =
      """{"$type":{"discriminator":"kind","commonFields":{"$oneOf":["a","b"],
        |"a":{"$optional":"string"},"b":{"$optional":"string"}},
        |"variants":{"one":{}}}}""".stripMargin
    parse(template).left.value.message should include("declare the presence group on each variant")
  }

  "validator" should "require exactly one key of a $oneOf group" in {
    val v = validator(List(PresenceGroup(List("inline_value", "value_ref"), ExactlyOne)))
    v.validate("""{"inline_value":"x"}""", "N") shouldBe a[Right[_, _]]
    v.validate("""{"value_ref":"x"}""", "N") shouldBe a[Right[_, _]]

    val none = errors(v.validate("{}", "N")).head
    none.path shouldBe "root"
    none.expected shouldBe "$oneOf: inline_value, value_ref"
    none.actual shouldBe "none present"

    errors(v.validate("""{"inline_value":"x","value_ref":"y"}""", "N")).head.actual shouldBe
      "2 present: inline_value, value_ref"
  }

  it should "treat a present null as absent" in {
    val v = validator(List(PresenceGroup(List("inline_value", "value_ref"), ExactlyOne)))
    v.validate("""{"inline_value":"x","value_ref":null}""", "N") shouldBe a[Right[_, _]]
    errors(v.validate("""{"inline_value":null,"value_ref":null}""", "N")).head.actual shouldBe "none present"
  }

  it should "allow several keys of an $atLeastOne group but still require one" in {
    val v = validator(List(PresenceGroup(List("inline_value", "value_ref"), AtLeastOne)))
    v.validate("""{"inline_value":"x","value_ref":"y"}""", "N") shouldBe a[Right[_, _]]
    errors(v.validate("{}", "N")).head.expected shouldBe "$atLeastOne: inline_value, value_ref"
  }

  it should "allow none present for an optional group but still cap $oneOf at one" in {
    val v = validator(List(PresenceGroup(List("inline_value", "value_ref"), ExactlyOne, optional = true)))
    v.validate("{}", "N") shouldBe a[Right[_, _]]
    errors(v.validate("""{"inline_value":"x","value_ref":"y"}""", "N")).head.message should include("$oneOf")

    val atLeast = validator(List(PresenceGroup(List("inline_value", "value_ref"), AtLeastOne, optional = true)))
    atLeast.validate("{}", "N") shouldBe a[Right[_, _]]
  }

  it should "enforce a group declared on a discriminator variant" in {
    val template =
      """{"definitions":[{"name":"N","template":{"$type":{"discriminator":"kind","variants":{
        |"ref":{"$oneOf":["inline_value","value_ref"],
        |"inline_value":{"$optional":"string"},"value_ref":{"$optional":"string"}}}}}}]}""".stripMargin
    val v = MultiValidator(parser.parseMultiTemplate(template, "com.example").value)
    v.validate("""{"kind":"ref","inline_value":"x"}""", "N") shouldBe a[Right[_, _]]
    errors(v.validate("""{"kind":"ref"}""", "N")).head.expected shouldBe "$oneOf: inline_value, value_ref"
  }

  "serializer" should "round-trip presence groups" in {
    TemplateSerializer.serializePresenceGroup(PresenceGroup(List("a", "b"), ExactlyOne)) shouldBe
      """PresenceGroup(List("a", "b"), ExactlyOne, false)"""
    TemplateSerializer.serializeTemplateType(
      ObjectType(
        ListMap("a" -> FieldDef(StringType(), optional = true), "b" -> FieldDef(StringType(), optional = true)),
        ForbidExtra,
        List(PresenceGroup(List("a", "b"), AtLeastOne, optional = true)))) should include(
      """List(PresenceGroup(List("a", "b"), AtLeastOne, true))""")
  }
}
