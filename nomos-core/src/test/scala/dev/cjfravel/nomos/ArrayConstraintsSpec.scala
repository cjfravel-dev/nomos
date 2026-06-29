package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.TemplateSerializer
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

class ArrayConstraintsSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String) = parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "parser" should "parse array min/max/unique item constraints" in {
    val d = parse("""{"name":"N","template":{"scores":{"type":"array","items":"number","minItems":1,"maxItems":3,"uniqueItems":true}}}""").value.definitions.head
    val arr = d.templateType.asInstanceOf[ObjectType].fields("scores").fieldType.asInstanceOf[ArrayType]
    arr.constraints should contain allOf (MinItems(1), MaxItems(3), UniqueItems(true))
  }

  def multi = MultiTemplate("com.example", List(TemplateDefinition("N", ObjectType(ListMap(
    "scores" -> FieldDef(ArrayType(NumberType(), List(MinItems(1), MaxItems(3), UniqueItems(true))), false))))))

  "validator" should "enforce item counts and uniqueness" in {
    val v = new MultiValidator(multi)
    v.validate("""{"scores":[1,2,3]}""", "N") shouldBe a[Right[_, _]]
    v.validate("""{"scores":[]}""", "N") shouldBe a[Left[_, _]]
    v.validate("""{"scores":[1,2,3,4]}""", "N") shouldBe a[Left[_, _]]
    v.validate("""{"scores":[1,1]}""", "N") shouldBe a[Left[_, _]]
  }

  "serializer" should "round-trip array constraints" in {
    TemplateSerializer.serializeTemplateType(ArrayType(StringType(), List(MinItems(1)))) shouldBe "ArrayType(StringType(List()), List(MinItems(1)))"
  }
}
