package dev.cjfravel.nomos

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.generation._
import dev.cjfravel.nomos.model._
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * toPascalCase splits on '-' and '_', so distinct source values can normalize to one identifier and emit duplicate case
 * objects/classes (#11); and a definition and an inline enum sharing a name in one package map to the same output file,
 * silently clobbering one (#32). Both are uncompilable or silently-wrong outcomes, so generation rejects them up front
 * with an actionable message.
 */
class NameCollisionSpec extends AnyFlatSpec with Matchers with EitherValues {

  val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
  def multi(defs: TemplateDefinition*) = MultiTemplate("com.example", defs.toList)

  "generateMulti" should "reject enum values that normalize to the same case-object name" in {
    val d =
      TemplateDefinition("E", ObjectType(ListMap("s" -> FieldDef(EnumType("S", List("a-b", "a_b")), optional = false))))
    val err = gen.generateMulti(multi(d)).left.value
    err shouldBe a[GeneratorError.TemplateError]
    err.message should (include("a-b") and include("a_b"))
  }

  it should "reject variant keys that normalize to the same class name" in {
    val disc =
      TypeDiscriminator(
        "kind",
        ListMap(
          "a-b" -> ObjectType(ListMap("x" -> FieldDef(StringType(), optional = false))),
          "a_b" -> ObjectType(ListMap("y" -> FieldDef(StringType(), optional = false)))),
        ListMap.empty,
        includeInOutput = true)
    gen.generateMulti(multi(TemplateDefinition("U", disc))).left.value shouldBe a[GeneratorError.TemplateError]
  }

  it should "reject a definition and an inline enum that map to the same output file" in {
    val t =
      MultiTemplate(
        "com.example",
        List(
          TemplateDefinition("Color", ObjectType(ListMap("hex" -> FieldDef(StringType(), optional = false)))),
          TemplateDefinition(
            "Widget",
            ObjectType(ListMap("shade" -> FieldDef(EnumType("Color", List("red", "blue")), optional = false))))))
    val err = gen.generateMulti(t).left.value
    err shouldBe a[GeneratorError.TemplateError]
    err.message should include("Color")
  }

  it should "still generate distinct enum values without collision" in {
    val d =
      TemplateDefinition(
        "E",
        ObjectType(ListMap("s" -> FieldDef(EnumType("S", List("active", "inactive")), optional = false))))
    gen.generateMulti(multi(d)) shouldBe a[Right[_, _]]
  }

  it should "still allow intentional variantNames grouping (same class name for several keys)" in {
    val disc =
      TypeDiscriminator(
        "kind",
        ListMap(
          "circle" -> ObjectType(ListMap("radius" -> FieldDef(IntType(), optional = false))),
          "round" -> ObjectType(ListMap("radius" -> FieldDef(IntType(), optional = false)))),
        ListMap.empty,
        includeInOutput = true,
        variantNames = Map("circle" -> "Round", "round" -> "Round"))
    gen.generateMulti(multi(TemplateDefinition("Shape", disc))) shouldBe a[Right[_, _]]
  }
}
