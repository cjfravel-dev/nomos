package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * A field `default` is rendered into generated Scala. String/numeric/boolean literals are fine, but
 * an enum default was emitted as a `String` literal where the generated enum type is required, so
 * the code did not compile. Defaults now render per the field's type (enum -> EnumName.Value) and
 * are validated at parse time; unsupported/mismatched defaults fail with a clear message.
 */
class TypedDefaultSpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  private val parser = new TemplateParser()
  private val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
  private def parse(defn: String) = parser.parseMultiTemplate(s"""{"definitions":[$defn]}""", "com.example")

  "an enum default" should "render as EnumName.Value and compile" in {
    val t = parse("""{"name":"Order","template":{"status":{"type":"string","enum":["active","closed"],"as":"enumType","name":"Status","default":"active"}}}""").value
    val fd = t.definitions.head.templateType.asInstanceOf[ObjectType].fields("status")
    fd.default shouldBe Some("Status.Active")
    compileErrors(gen.generateMulti(t).value) shouldBe empty
  }

  "an enum default that is not one of the values" should "be a parse error" in {
    parse("""{"name":"Order","template":{"status":{"type":"string","enum":["active","closed"],"as":"enumType","name":"Status","default":"nope"}}}""") shouldBe a[Left[_, _]]
  }

  "a default on a date field" should "be rejected with a clear error" in {
    parse("""{"name":"E","template":{"d":{"type":"date","default":"2020-01-01"}}}""") shouldBe a[Left[_, _]]
  }

  "a string default on an int field" should "be rejected" in {
    parse("""{"name":"E","template":{"n":{"type":"int","default":"notanumber"}}}""") shouldBe a[Left[_, _]]
  }

  "string, numeric and boolean defaults" should "still render as literals" in {
    val t = parse("""{"name":"N","template":{"s":{"type":"string","default":"x"},"i":{"type":"int","default":3},"b":{"type":"boolean","default":true}}}""").value
    val f = t.definitions.head.templateType.asInstanceOf[ObjectType].fields
    f("s").default shouldBe Some("\"x\"")
    f("i").default shouldBe Some("3")
    f("b").default shouldBe Some("true")
  }

  "a long default outside Int range" should "render with an L suffix and compile" in {
    val t = parse("""{"name":"C","template":{"n":{"type":"long","default":9999999999}}}""").value
    t.definitions.head.templateType.asInstanceOf[ObjectType].fields("n").default shouldBe Some("9999999999L")
    compileErrors(gen.generateMulti(t).value) shouldBe empty
  }

  "a large number default" should "render as a valid Double literal and compile" in {
    val t = parse("""{"name":"C","template":{"n":{"type":"number","default":9999999999}}}""").value
    t.definitions.head.templateType.asInstanceOf[ObjectType].fields("n").default shouldBe Some("9999999999.0")
    compileErrors(gen.generateMulti(t).value) shouldBe empty
  }

  "a large decimal default" should "render as BigDecimal(...) and compile" in {
    val t = parse("""{"name":"C","template":{"n":{"type":"decimal","default":9999999999}}}""").value
    t.definitions.head.templateType.asInstanceOf[ObjectType].fields("n").default shouldBe Some("""BigDecimal("9999999999")""")
    compileErrors(gen.generateMulti(t).value) shouldBe empty
  }

  "an int default outside Int range" should "be rejected" in {
    parse("""{"name":"C","template":{"n":{"type":"int","default":9999999999}}}""") shouldBe a[Left[_, _]]
  }
}
