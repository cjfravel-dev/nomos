package dev.cjfravel.nomos.generation

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TemplateSerializerSpec extends AnyFlatSpec with Matchers {

  "serializeMultiTemplate" should "emit basePackage but not outputDir or mainClass" in {
    val t =
      MultiTemplate(
        "com.example",
        List(TemplateDefinition("User", ObjectType(ListMap("id" -> FieldDef(StringType(), optional = false))))))
    val out = TemplateSerializer.serializeMultiTemplate(t)
    out should include("basePackage = \"com.example\"")
    out should not include "outputDir"
    out should not include "mainClass"
  }

  "serializeConstraint" should "cover every constraint" in {
    TemplateSerializer.serializeConstraint(MinLength(1)) shouldBe "MinLength(1)"
    TemplateSerializer.serializeConstraint(MaxLength(5)) shouldBe "MaxLength(5)"
    TemplateSerializer.serializeConstraint(Pattern("a")) should include("Pattern")
    TemplateSerializer.serializeConstraint(Format("email")) should include("Format")
    TemplateSerializer.serializeConstraint(Min(0)) should include("Min")
    TemplateSerializer.serializeConstraint(Max(9)) should include("Max")
    TemplateSerializer.serializeConstraint(MultipleOf(2)) should include("MultipleOf")
    TemplateSerializer.serializeConstraint(MinItems(1)) should include("MinItems")
    TemplateSerializer.serializeConstraint(MaxItems(3)) should include("MaxItems")
    TemplateSerializer.serializeConstraint(UniqueItems(true)) should include("UniqueItems")
  }

  "serializeTemplateType" should "cover every type incl. discriminator and refs" in {
    TemplateSerializer.serializeTemplateType(BooleanType()) shouldBe "BooleanType()"
    TemplateSerializer.serializeTemplateType(ArrayType(StringType())) should include("ArrayType")
    TemplateSerializer.serializeTemplateType(ReferenceType("X")) should include("ReferenceType")
    TemplateSerializer.serializeTemplateType(RecursiveRef("X")) should include("RecursiveRef")
    val disc =
      TypeDiscriminator(
        "t",
        ListMap("a" -> ObjectType(ListMap("x" -> FieldDef(NumberType(), optional = false)))),
        ListMap("c" -> FieldDef(StringType(), optional = true)),
        true,
        Map("a" -> "A"))
    val s = TemplateSerializer.serializeTemplateType(disc)
    s should include("TypeDiscriminator")
    s should include("variantNames")
  }

  it should "escape special characters" in {
    TemplateSerializer.serializeTemplateType(StringType(List(Pattern("a\"b")))) should include("\\\"")
  }
}
