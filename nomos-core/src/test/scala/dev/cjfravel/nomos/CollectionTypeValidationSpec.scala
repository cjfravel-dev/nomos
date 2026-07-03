package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

/**
 * Field types honor the project-wide listType/mapType, but the generator only emits decoders for
 * List/Array (lists) and Map (maps). Any other value produces a case-class field whose type does
 * not match the decoder's result, so the generated code does not compile. These specs pin that
 * unsupported values are rejected up front with an actionable message, and supported values pass.
 */
class CollectionTypeValidationSpec extends AnyFlatSpec with Matchers with EitherValues {

  val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
  val parser = new TemplateParser()

  def arrayDef =
    TemplateDefinition("N", ObjectType(ListMap("xs" -> FieldDef(ArrayType(StringType()), optional = false))))
  def mapDef =
    TemplateDefinition("N", ObjectType(ListMap("m" -> FieldDef(MapType(StringType()), optional = false))))

  "generateMulti" should "reject an unsupported listType with an actionable message" in {
    val t = MultiTemplate("com.example", List(arrayDef), listType = "Vector")
    val err = gen.generateMulti(t).left.value
    err shouldBe a[GeneratorError.TemplateError]
    err.message should include("listType")
    err.message should include("Vector")
    err.message should include("List")
    err.message should include("Array")
  }

  it should "reject an unsupported mapType with an actionable message" in {
    val t = MultiTemplate("com.example", List(mapDef), mapType = "java.util.Map")
    val err = gen.generateMulti(t).left.value
    err shouldBe a[GeneratorError.TemplateError]
    err.message should include("mapType")
    err.message should include("java.util.Map")
    err.message should include("Map")
  }

  it should "accept the supported list types (List, Array)" in {
    gen.generateMulti(MultiTemplate("com.example", List(arrayDef), listType = "List")) shouldBe a[Right[_, _]]
    gen.generateMulti(MultiTemplate("com.example", List(arrayDef), listType = "Array")) shouldBe a[Right[_, _]]
  }

  it should "accept the supported map type (Map)" in {
    gen.generateMulti(MultiTemplate("com.example", List(mapDef), mapType = "Map")) shouldBe a[Right[_, _]]
  }

  "parseMultiTemplate" should "reject a template that sets an unsupported listType" in {
    parser.parseMultiTemplate(
      """{"listType":"Seq","definitions":[{"name":"N","template":{"xs":["string"]}}]}""",
      "com.example") shouldBe a[Left[_, _]]
  }

  it should "reject a template that sets an unsupported mapType" in {
    parser.parseMultiTemplate(
      """{"mapType":"scala.collection.mutable.Map","definitions":[{"name":"N","template":{"m":{"$map":"string"}}}]}""",
      "com.example") shouldBe a[Left[_, _]]
  }
}
