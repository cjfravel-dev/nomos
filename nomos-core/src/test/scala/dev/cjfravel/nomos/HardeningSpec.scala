package dev.cjfravel.nomos

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.generation._
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Covers beta-hardening fixes: identifier validation, generated-source escaping, safe error propagation for ambiguous
 * discriminators and malformed templates, numeric range validation, and output-directory containment.
 */
class HardeningSpec extends AnyFlatSpec with Matchers with EitherValues {

  val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
  val parser = new TemplateParser()
  def multi(defs: TemplateDefinition*): MultiTemplate = MultiTemplate("com.example", defs.toList)

  "escapeStringLiteral" should "escape quotes, backslashes, and control characters" in {
    ScalaCodeBuilder.escapeStringLiteral("he\"llo\\\n\t") shouldBe "he\\\"llo\\\\\\n\\t"
  }

  "isSimpleIdentifier" should "accept identifiers and reject non-identifiers" in {
    ScalaCodeBuilder.isSimpleIdentifier("userName_2") shouldBe true
    ScalaCodeBuilder.isSimpleIdentifier("first-name") shouldBe false
    ScalaCodeBuilder.isSimpleIdentifier("2x") shouldBe false
    ScalaCodeBuilder.isSimpleIdentifier("../etc") shouldBe false
    ScalaCodeBuilder.isSimpleIdentifier("") shouldBe false
  }

  "generateMulti" should "reject includeDiscriminator=false instead of emitting a non-round-tripping union" in {
    val disc =
      TypeDiscriminator(
        "kind",
        ListMap(
          "a" -> ObjectType(ListMap("x" -> FieldDef(StringType(), optional = false))),
          "b" -> ObjectType(ListMap("y" -> FieldDef(StringType(), optional = false)))),
        ListMap.empty,
        includeInOutput = false)
    val err = gen.generateMulti(multi(TemplateDefinition("Thing", disc))).left.value
    err shouldBe a[GeneratorError.TemplateError]
    err.message should include("includeDiscriminator")
  }

  it should "reject an enum name that is not a valid identifier (closes path traversal)" in {
    val evil =
      TemplateDefinition(
        "E",
        ObjectType(ListMap("tier" -> FieldDef(EnumType("../../../../tmp/pwned", List("a", "b")), optional = false))))
    val err = gen.generateMulti(multi(evil)).left.value
    err shouldBe a[GeneratorError.TemplateError]
    err.message should include("identifier")
  }

  it should "reject a field name that is not a valid identifier" in {
    val bad = TemplateDefinition("F", ObjectType(ListMap("first-name" -> FieldDef(StringType(), optional = false))))
    gen.generateMulti(multi(bad)).left.value shouldBe a[GeneratorError.TemplateError]
  }

  it should "escape a discriminator variant key that contains a quote" in {
    val disc =
      TypeDiscriminator(
        "t",
        ListMap("a\"b" -> ObjectType(ListMap("x" -> FieldDef(StringType(), optional = false)))),
        ListMap.empty,
        includeInOutput = true,
        variantNames = Map("a\"b" -> "Alpha"))
    val content =
      gen.generateMulti(multi(TemplateDefinition("E", disc))).value.find(_.fileName == "E.scala").get.content
    content should include("""a\"b""")
  }

  "renderDefault" should "escape a string default so generated source stays well-formed" in {
    val t =
      parser
        .parseMultiTemplate(
          """{"definitions":[{"name":"D","template":{"greeting":{"type":"string","default":"he\"llo"}}}]}""",
          "com.example")
        .value
    val fd = t.definitions.head.templateType.asInstanceOf[ObjectType].fields("greeting")
    fd.default shouldBe Some("\"he\\\"llo\"")
  }

  "parser" should "report an error for an invalid $additionalProperties type instead of silently forbidding" in {
    val result =
      parser.parseMultiTemplate(
        """{"definitions":[{"name":"D","template":{"x":"string","$additionalProperties":"notatype"}}]}""",
        "com.example")
    result shouldBe a[Left[_, _]]
  }

  "MultiValidator" should "reject whole numbers outside the Int range" in {
    val t =
      MultiTemplate(
        "com.example",
        List(TemplateDefinition("N", ObjectType(ListMap("count" -> FieldDef(IntType(), optional = false))))))
    val v = new MultiValidator(t)
    v.validate("""{"count": 9999999999}""", "N") shouldBe a[Left[_, _]]
    v.validate("""{"count": 42}""", "N") shouldBe a[Right[_, _]]
  }

  it should "reject whole numbers outside the Long range" in {
    val t =
      MultiTemplate(
        "com.example",
        List(TemplateDefinition("N", ObjectType(ListMap("count" -> FieldDef(LongType(), optional = false))))))
    val v = new MultiValidator(t)
    v.validate("""{"count": 99999999999999999999999}""", "N") shouldBe a[Left[_, _]]
    v.validate("""{"count": 42}""", "N") shouldBe a[Right[_, _]]
  }

  "FileWriter" should "refuse to write outside the output directory" in {
    val fw = new FileWriter()
    val dir = new java.io.File("target/test-fw-contain")
    fw.writeFile(GeneratedFile("../escaped.scala", "object X"), dir) shouldBe a[Left[_, _]]
  }

  // Regression guards: collectNameErrors must not reject templates that generated valid code before.

  it should "accept an external type with generic parameters" in {
    val d =
      TemplateDefinition(
        "N",
        ObjectType(ListMap("rule" -> FieldDef(ExternalType("com.example.Wrapper[String]"), optional = false))))
    val content = gen.generateMulti(multi(d)).value.find(_.fileName == "N.scala").get.content
    content should include("rule: com.example.Wrapper[String]")
  }

  it should "treat an explicit empty subPackage as no sub-package" in {
    val d =
      TemplateDefinition(
        "N",
        ObjectType(ListMap("x" -> FieldDef(StringType(), optional = false))),
        subPackage = Some(""))
    gen.generateMulti(multi(d)) shouldBe a[Right[_, _]]
  }

  "an inline nested object used as a field type" should "be rejected with a clear message, not emit ???" in {
    val tmpl =
      """{"definitions":[{"name":"Person","template":{"name":"string",""" +
        """"address":{"street":"string","city":"string"}}}]}"""
    val t = parser.parseMultiTemplate(tmpl, "com.example").value
    val err = gen.generateMulti(t).left.value.toString
    err should include("inline nested objects are not supported")
    err should include("address")
  }

  it should "still allow an empty object with additionalProperties (open map)" in {
    val tmpl = """{"definitions":[{"name":"Bag","template":{"meta":{"$additionalProperties":"string"}}}]}"""
    val t = parser.parseMultiTemplate(tmpl, "com.example").value
    gen.generateMulti(t) shouldBe a[Right[_, _]]
  }
}
