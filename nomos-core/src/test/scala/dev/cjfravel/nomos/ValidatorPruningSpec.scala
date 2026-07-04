package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.validation.{MultiValidator, ValidatorRegistry, ValidatorContext, ValidationError}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import scala.collection.immutable.ListMap

/**
 * Phase two (custom validators) is skipped for a definition whose reachable subtree declares no
 * validators, so a validator-free template pays no second traversal. Reachability is subtree-scoped:
 * an unrelated definition's validators do not force the walk. Correctness (which validators run) is
 * unchanged and covered by ValidatorContextSpec.
 */
class ValidatorPruningSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  override def beforeEach(): Unit = ValidatorRegistry.clear()
  override def afterEach(): Unit = ValidatorRegistry.clear()

  private def obj(fields: (String, FieldDef)*) = ObjectType(ListMap(fields: _*))
  private def strField = FieldDef(StringType(), optional = false)

  "a template with no validators anywhere" should "not run phase two for the target" in {
    val t = MultiTemplate("com.example", List(
      TemplateDefinition("Parent", obj("child" -> FieldDef(ReferenceType("Child"), optional = false))),
      TemplateDefinition("Child", obj("value" -> strField))))
    new MultiValidator(t).reachesAnyValidator("Parent") shouldBe false
  }

  "a definition whose $ref'd child declares validators" should "run phase two" in {
    val t = MultiTemplate("com.example", List(
      TemplateDefinition("Parent", obj("child" -> FieldDef(ReferenceType("Child"), optional = false))),
      TemplateDefinition("Child", obj("value" -> strField), validators = List("v"))))
    new MultiValidator(t).reachesAnyValidator("Parent") shouldBe true
  }

  "an unrelated definition's validators" should "not force phase two for the target" in {
    val t = MultiTemplate("com.example", List(
      TemplateDefinition("Parent", obj("child" -> FieldDef(ReferenceType("Child"), optional = false))),
      TemplateDefinition("Child", obj("value" -> strField)),
      TemplateDefinition("Unrelated", obj("x" -> strField), validators = List("v"))))
    new MultiValidator(t).reachesAnyValidator("Parent") shouldBe false
    new MultiValidator(t).reachesAnyValidator("Unrelated") shouldBe true
  }

  "a definition with its own top-level validator" should "run phase two" in {
    val t = MultiTemplate("com.example", List(
      TemplateDefinition("Parent", obj("id" -> strField), validators = List("v"))))
    new MultiValidator(t).reachesAnyValidator("Parent") shouldBe true
  }

  "pruning" should "not change which validators run (recursive type, deep)" in {
    // Node -> next: Node (recursive), with a validator on Node. Reachability must terminate and be
    // true, and the validator still fires end to end.
    ValidatorRegistry.register("node.ok") { ctx =>
      if (ctx.node.asObject.flatMap(_.field("v")).flatMap(_.asString).contains("bad"))
        List(ValidationError(ctx.path, "bad node", "ok", "bad")) else Nil
    }
    val t = MultiTemplate("com.example", List(
      TemplateDefinition("Node", obj(
        "v" -> strField,
        "next" -> FieldDef(RecursiveRef("Node"), optional = true)), validators = List("node.ok"))))
    val v = new MultiValidator(t)
    v.reachesAnyValidator("Node") shouldBe true
    v.validate("""{"v":"a","next":{"v":"bad"}}""", "Node") shouldBe a[Left[_, _]]
    v.validate("""{"v":"a","next":{"v":"b"}}""", "Node") shouldBe a[Right[_, _]]
  }
}
