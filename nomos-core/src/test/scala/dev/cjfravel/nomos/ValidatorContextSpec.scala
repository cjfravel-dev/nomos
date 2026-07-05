package dev.cjfravel.nomos

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratedFile, GeneratorConfig}
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.validation._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues}

/**
 * Custom validators receive a ValidatorContext (the node at their definition's level, the whole document root, and the
 * JSON path), and they run at every level a definition appears — including definitions reached through a $ref — not
 * only the top-level definition being validated.
 */
class ValidatorContextSpec
    extends AnyFlatSpec
    with Matchers
    with EitherValues
    with OptionValues
    with BeforeAndAfterEach
    with CompileHarness {

  override def beforeEach(): Unit = ValidatorRegistry.clear()
  override def afterEach(): Unit = ValidatorRegistry.clear()

  // Parent { child: $ref:Child }; Child has a custom validator.
  private def parentChild =
    MultiTemplate(
      "com.example",
      List(
        TemplateDefinition(
          "Parent",
          ObjectType(ListMap("child" -> FieldDef(ReferenceType("Child"), optional = false)))),
        TemplateDefinition(
          "Child",
          ObjectType(ListMap("value" -> FieldDef(StringType(), optional = false))),
          validators = List("child.valueOk"))))

  "a top-level validator" should "receive a context whose node equals the root and path is 'root'" in {
    var seen: Option[ValidatorContext] = None
    ValidatorRegistry.register("cap") { ctx => seen = Some(ctx); Nil }
    val m =
      MultiTemplate(
        "com.example",
        List(
          TemplateDefinition(
            "R",
            ObjectType(ListMap("id" -> FieldDef(StringType(), false))),
            validators = List("cap"))))
    new MultiValidator(m).validate("""{"id":"x"}""", "R") shouldBe a[Right[_, _]]
    val ctx = seen.value
    ctx.path shouldBe "root"
    ctx.node shouldBe ctx.root
    ctx.node.asObject.flatMap(_.field("id")).flatMap(_.asString) shouldBe Some("x")
  }

  "a validator on a $ref'd definition" should "run at the nested level with node/root/path" in {
    var seen: Option[ValidatorContext] = None
    ValidatorRegistry.register("child.valueOk") { ctx =>
      seen = Some(ctx)
      if (ctx.node.asObject.flatMap(_.field("value")).flatMap(_.asString).contains("ok")) Nil
      else List(ValidationError(ctx.path, "value must be 'ok'", "ok", "other"))
    }
    val v = new MultiValidator(parentChild)

    // Nested validator fires and fails for a bad child value (schema is otherwise valid).
    v.validate("""{"child":{"value":"bad"}}""", "Parent") shouldBe a[Left[_, _]]
    v.validate("""{"child":{"value":"ok"}}""", "Parent") shouldBe a[Right[_, _]]

    val ctx = seen.value
    ctx.path shouldBe "root.child"
    ctx.node.asObject.flatMap(_.field("value")).flatMap(_.asString) shouldBe Some("ok")
    // root is the whole document, so the nested validator can traverse up to siblings/root.
    ctx.root.asObject.map(_.keys).getOrElse(Vector.empty) should contain("child")
  }

  "a nested validator error" should "be reported through the top-level validate result" in {
    ValidatorRegistry.register("child.valueOk") { ctx =>
      if (ctx.node.asObject.flatMap(_.field("value")).flatMap(_.asString).contains("ok")) Nil
      else List(ValidationError(ctx.path, "value must be 'ok'", "ok", "other"))
    }
    val errs = new MultiValidator(parentChild).validate("""{"child":{"value":"bad"}}""", "Parent").left.value
    errs.exists(_.message.contains("value must be 'ok'")) shouldBe true
  }

  "generated code" should "run a $ref'd definition's custom validator end-to-end" in {
    ValidatorRegistry.register("child.valueOk") { ctx =>
      if (ctx.node.asObject.flatMap(_.field("value")).flatMap(_.asString).contains("ok")) Nil
      else List(ValidationError(ctx.path, "value must be 'ok'", "ok", "other"))
    }
    val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
    val driver =
      GeneratedFile(
        "com/example/Drv.scala",
        """package com.example
        |object Drv {
        |  def run(): String = Parent.validate("""".stripMargin +
          """{\"child\":{\"value\":\"bad\"}}""" +
          """") match { case Right(_) => "valid"; case Left(_) => "invalid" }
          |}
          |""".stripMargin)
    val files = driver :: gen.generateMulti(parentChild).value
    runDriver(files, "com.example.Drv") shouldBe "invalid"
  }

  "re-entrant validation" should "not disturb the outer call's root" in {
    // A validator that re-enters validation on the SAME MultiValidator instance must not clear the
    // document root for validators that run later in the outer call. Re-enter a different,
    // validator-free definition so the callback doesn't re-trigger itself.
    val reentrant =
      MultiTemplate(
        "com.example",
        List(
          TemplateDefinition(
            "Parent",
            ObjectType(ListMap("child" -> FieldDef(ReferenceType("Child"), optional = false)))),
          TemplateDefinition(
            "Child",
            ObjectType(ListMap("value" -> FieldDef(StringType(), optional = false))),
            validators = List("child.checkRoot")),
          TemplateDefinition("Other", ObjectType(ListMap("id" -> FieldDef(StringType(), optional = false))))))
    val v = new MultiValidator(reentrant)
    var sawNullRoot = false
    ValidatorRegistry.register("child.checkRoot") { ctx =>
      sawNullRoot ||= ctx.root == null
      v.validate("""{"id":"x"}""", "Other") // re-enter a validator-free definition
      sawNullRoot ||= ctx.root == null
      Nil
    }
    v.validate("""{"child":{"value":"ok"}}""", "Parent") shouldBe a[Right[_, _]]
    sawNullRoot shouldBe false
  }

  "a validator-bearing reference cycle" should "fail with an error, not StackOverflow" in {
    // Both definitions carry a validator and form a no-input ref cycle. Phase-one structural
    // validation must catch the cycle via the depth guard before any validator runs.
    ValidatorRegistry.register("noop")(_ => Nil)
    val cyclic =
      MultiTemplate(
        "com.example",
        List(
          TemplateDefinition("A", ReferenceType("B"), validators = List("noop")),
          TemplateDefinition("B", ReferenceType("A"), validators = List("noop"))))
    new MultiValidator(cyclic).validate("""{"x":1}""", "A") shouldBe a[Left[_, _]]
  }

  "a nested validator" should "not run when a sibling subtree is structurally invalid" in {
    var ran = false
    ValidatorRegistry.register("child.valueOk") { _ => ran = true; Nil }
    // Parent also needs a required sibling; make it invalid so the document schema fails overall.
    val withSibling =
      MultiTemplate(
        "com.example",
        List(
          TemplateDefinition(
            "Parent",
            ObjectType(
              ListMap(
                "child" -> FieldDef(ReferenceType("Child"), optional = false),
                "count" -> FieldDef(IntType(), optional = false)))),
          TemplateDefinition(
            "Child",
            ObjectType(ListMap("value" -> FieldDef(StringType(), optional = false))),
            validators = List("child.valueOk"))))
    // child is structurally fine, but count is the wrong type -> whole doc invalid.
    new MultiValidator(withSibling)
      .validate("""{"child":{"value":"x"},"count":"nope"}""", "Parent") shouldBe a[Left[_, _]]
    ran shouldBe false
  }

  "a $ref'd validator inside an array" should "run for each element with an indexed path" in {
    var paths = List.empty[String]
    ValidatorRegistry.register("child.valueOk") { ctx => paths = ctx.path :: paths; Nil }
    val withArray =
      MultiTemplate(
        "com.example",
        List(
          TemplateDefinition(
            "Parent",
            ObjectType(ListMap("children" -> FieldDef(ArrayType(ReferenceType("Child")), optional = false)))),
          TemplateDefinition(
            "Child",
            ObjectType(ListMap("value" -> FieldDef(StringType(), optional = false))),
            validators = List("child.valueOk"))))
    new MultiValidator(withArray)
      .validate("""{"children":[{"value":"a"},{"value":"b"}]}""", "Parent") shouldBe a[Right[_, _]]
    paths.reverse shouldBe List("root.children[0]", "root.children[1]")
  }
}
