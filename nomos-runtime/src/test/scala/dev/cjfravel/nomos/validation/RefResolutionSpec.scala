package dev.cjfravel.nomos.validation

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Same simple name in different sub-packages is supported, so runtime `$ref` resolution must use the referrer's package
 * context (mirroring the generator's imports) instead of a simple-name map that collapses to whichever definition is
 * last. An unresolved `RecursiveRef` must error like an unresolved `ReferenceType`, not silently pass.
 */
class RefResolutionSpec extends AnyFlatSpec with Matchers {

  // p1.A.child -> $ref:B, with a p1.B (x: String) and a p2.B (x: Int); p2.B is last, so a
  // simple-name last-wins map would resolve to p2.B and validate against the wrong type.
  private val template =
    MultiTemplate(
      "com.example",
      List(
        TemplateDefinition(
          "A",
          ObjectType(ListMap("child" -> FieldDef(ReferenceType("B"), optional = false))),
          Some("p1")),
        TemplateDefinition("B", ObjectType(ListMap("x" -> FieldDef(StringType(), optional = false))), Some("p1")),
        TemplateDefinition("B", ObjectType(ListMap("x" -> FieldDef(IntType(), optional = false))), Some("p2"))))
  private val v = new MultiValidator(template)

  "MultiValidator" should "resolve a $ref using the referrer's package, not simple-name last-wins" in {
    v.validate("""{"child":{"x":"hi"}}""", "com.example.p1.A") shouldBe a[Right[_, _]]
  }

  it should "reject a payload valid only for the wrong same-named definition" in {
    v.validate("""{"child":{"x":5}}""", "com.example.p1.A") shouldBe a[Left[_, _]]
  }

  it should "report an unresolved recursive reference instead of silently passing" in {
    val t =
      MultiTemplate(
        "com.example",
        List(TemplateDefinition("N", ObjectType(ListMap("next" -> FieldDef(RecursiveRef("Ghost"), optional = false))))))
    new MultiValidator(t).validate("""{"next":{}}""", "N") shouldBe a[Left[_, _]]
  }

  it should "still validate a well-formed self-recursive structure" in {
    val tree =
      MultiTemplate(
        "com.example",
        List(
          TemplateDefinition(
            "TreeNode",
            ObjectType(
              ListMap(
                "value" -> FieldDef(StringType(), optional = false),
                "children" -> FieldDef(ArrayType(RecursiveRef("TreeNode")), optional = false))))))
    new MultiValidator(tree)
      .validate("""{"value":"a","children":[{"value":"b","children":[]}]}""", "TreeNode") shouldBe a[Right[_, _]]
  }

  "MultiTemplate.validate" should "flag an unresolved recursive reference at build time" in {
    val t =
      MultiTemplate(
        "com.example",
        List(TemplateDefinition("N", ObjectType(ListMap("next" -> FieldDef(RecursiveRef("Ghost"), optional = false))))))
    t.validate() should not be empty
  }
}
