package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.TemplateSerializer
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

class UnionTypeSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String) = parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "parser" should "parse a map value union" in {
    val d = parse("""{"name":"N","template":{"settings":{"$map":["string",["string"]]}}}""").value.definitions.head
    d.templateType.asInstanceOf[ObjectType].fields("settings").fieldType shouldBe MapType(UnionType(List(StringType(), ArrayType(StringType()))))
  }

  "validator" should "accept any member of the union and reject others" in {
    val t = parse("""{"name":"N","template":{"settings":{"$map":["string",["string"]]}}}""").value
    val v = new MultiValidator(t)
    v.validate("""{"settings":{"a":"x,y","b":["x","y"]}}""", "N") shouldBe a[Right[_, _]]
    v.validate("""{"settings":{"a":5}}""", "N") shouldBe a[Left[_, _]]
  }

  "serializer" should "round-trip a union" in {
    TemplateSerializer.serializeTemplateType(UnionType(List(StringType(), NumberType()))) shouldBe "UnionType(List(StringType(List()), NumberType(List())))"
  }
}
