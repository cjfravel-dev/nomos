package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.validation.MultiValidator
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RefVariantSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()
  val tmpl =
    """
      |{"definitions":[
      |  {"name":"Circle","template":{"radius":"number"}},
      |  {"name":"Square","template":{"side":"number"}},
      |  {"name":"Shape","template":{"$type":{"discriminator":"kind",
      |    "variants":{"circle":"$ref:Circle","square":"$ref:Square"}}}}
      |]}
    """.stripMargin

  "parser" should "resolve $ref variant bodies to the referenced object fields" in {
    val shape = parser.parseMultiTemplate(tmpl, "com.example").value.definitions.find(_.name == "Shape").get
    val disc = shape.templateType.asInstanceOf[TypeDiscriminator]
    disc.variants("circle").fields.keySet should contain("radius")
    disc.variants("square").fields.keySet should contain("side")
  }

  "validator" should "validate a ref-variant union" in {
    val t = parser.parseMultiTemplate(tmpl, "com.example").value
    val v = new MultiValidator(t)
    v.validate("""{"kind":"circle","radius":4}""", "Shape") shouldBe a[Right[_, _]]
    v.validate("""{"kind":"circle"}""", "Shape") shouldBe a[Left[_, _]]
  }
}
