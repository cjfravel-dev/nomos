package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import java.io.File
import scala.collection.immutable.ListMap

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
}
