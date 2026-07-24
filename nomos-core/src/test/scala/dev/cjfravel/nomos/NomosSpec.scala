package dev.cjfravel.nomos
import java.io.File

import dev.cjfravel.nomos.generation.GeneratorError
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NomosSpec extends AnyFlatSpec with Matchers with EitherValues {

  val templateJson =
    """
      |{
      |  "definitions": [
      |    { "name": "User", "subPackage": "models",
      |      "template": { "id": "string", "age": { "$optional": "number" }, "tags": ["string"] } }
      |  ]
      |}
    """.stripMargin

  val tmpDir = new File(System.getProperty("java.io.tmpdir"), "nomos-test-" + System.nanoTime())

  "Nomos.parseTemplate" should "parse with an explicit base package" in {
    val t = Nomos.parseTemplate(templateJson, "com.example").value
    t.basePackage shouldBe "com.example"
    t.fqn(t.definitions.head) shouldBe "com.example.models.User"
  }

  it should "return a ParseError for malformed JSON" in {
    Nomos.parseTemplate("{ not json", "com.example").left.value.message should not be empty
  }

  "Nomos.generateCode" should "write files into the given output dir" in {
    val t = Nomos.parseTemplate(templateJson, "com.example").value
    val report = Nomos.generateCode(t, tmpDir.getAbsolutePath).value
    report.failures shouldBe empty
    new File(tmpDir, "com/example/models/User.scala").exists() shouldBe true
    new File(tmpDir, "com/example/NomosFormats.scala").exists() shouldBe true
  }

  "Nomos.validate" should "accept valid JSON by fully-qualified name" in {
    val t = Nomos.parseTemplate(templateJson, "com.example").value
    Nomos.validate(t, """{"id":"a","tags":["x"]}""", "com.example.models.User") shouldBe a[Right[_, _]]
  }

  it should "reject JSON missing a required field" in {
    val t = Nomos.parseTemplate(templateJson, "com.example").value
    Nomos.validate(t, """{"tags":["x"]}""", "com.example.models.User") shouldBe a[Left[_, _]]
  }

  "Nomos.process" should "parse, generate and expose a validator" in {
    val result = Nomos.process(templateJson, "com.example", tmpDir.getAbsolutePath).value
    result.isSuccess shouldBe true
    result.generatedFiles should not be empty
    result.validator.validate("""{"id":"a","tags":[]}""", "com.example.models.User") shouldBe a[Right[_, _]]
  }

  it should "surface parse failures as NomosError" in {
    Nomos.process("{bad", "com.example").left.value.message should include("parse")
  }

  "Nomos.createValidator" should "build a validator from template JSON" in {
    val v = Nomos.createValidator(templateJson, "com.example").value
    v.validate("""{"id":"a","tags":[]}""", "User") shouldBe a[Right[_, _]]
  }

  "Nomos.parseTemplateDeferred" should "tag every definition with the given source path" in {
    val t = Nomos.parseTemplateDeferred(templateJson, "com.example", "src/models/user.json").value
    t.definitions should not be empty
    all(t.definitions.map(_.sourcePath)) shouldBe Some("src/models/user.json")
  }

  it should "leave the source path unset when none is provided" in {
    val t = Nomos.parseTemplateDeferred(templateJson, "com.example").value
    all(t.definitions.map(_.sourcePath)) shouldBe None
  }

  "Nomos.generateAll" should "merge multiple templates into one output" in {
    val other =
      """
        |{
        |  "definitions": [
        |    { "name": "Order", "subPackage": "models",
        |      "template": { "sku": "string" } }
        |  ]
        |}
      """.stripMargin
    val a = Nomos.parseTemplateDeferred(templateJson, "com.example").value
    val b = Nomos.parseTemplateDeferred(other, "com.example").value
    val templates = new java.util.ArrayList[dev.cjfravel.nomos.model.MultiTemplate]()
    templates.add(a)
    templates.add(b)
    val report = Nomos.generateAll(templates, tmpDir.getAbsolutePath).value
    report.failures shouldBe empty
    new File(tmpDir, "com/example/models/Order.scala").exists() shouldBe true
  }

  it should "surface a TemplateError when the templates conflict" in {
    val listA =
      """{ "dateType": "com.foo.DateX", "definitions": [ { "name": "A", "template": { "x": "string" } } ] }"""
    val listB =
      """{ "dateType": "com.foo.DateY", "definitions": [ { "name": "B", "template": { "y": "string" } } ] }"""
    val first = Nomos.parseTemplateDeferred(listA, "com.example").value
    val second = Nomos.parseTemplateDeferred(listB, "com.example").value
    val templates = new java.util.ArrayList[dev.cjfravel.nomos.model.MultiTemplate]()
    templates.add(first)
    templates.add(second)
    Nomos.generateAll(templates, tmpDir.getAbsolutePath).left.value shouldBe a[GeneratorError.TemplateError]
  }

  "NomosError.GenerationFailed" should "describe the underlying generator error" in {
    val err: NomosError = NomosError.GenerationFailed(GeneratorError.IOError("disk full"))
    err.message should include("generate")
    err.message should include("disk full")
  }

  "NomosResult.errors" should "be empty for a successful result" in {
    val result = Nomos.process(templateJson, "com.example", tmpDir.getAbsolutePath).value
    result.errors shouldBe empty
  }
}
