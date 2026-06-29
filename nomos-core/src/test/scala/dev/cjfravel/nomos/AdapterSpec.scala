package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.TemplateSerializer
import dev.cjfravel.nomos.serialization.AdapterRegistry
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

class AdapterSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  def parse(json: String) = parser.parseMultiTemplate(s"""{"definitions":[$json]}""", "com.example")

  "parser" should "carry a per-field adapter name" in {
    val d = parse("""{"name":"N","template":{"createdAt":{"type":"string","adapter":"epochMillis"}}}""").value.definitions.head
    d.templateType.asInstanceOf[ObjectType].fields("createdAt").adapter shouldBe Some("epochMillis")
  }

  "serializer" should "round-trip a field adapter" in {
    TemplateSerializer.serializeFieldDef(FieldDef(StringType(), false, None, Some("epochMillis"))) should include("adapter = Some")
  }

  "AdapterRegistry" should "transform values on encode/decode" in {
    AdapterRegistry.register("epochMillis")(decode = _.toLong.toString, encode = _.toString)
    AdapterRegistry.decode("epochMillis", "1704067200000") shouldBe "1704067200000"
    AdapterRegistry.encode("epochMillis", "5") shouldBe "5"
    AdapterRegistry.isRegistered("epochMillis") shouldBe true
  }
}
