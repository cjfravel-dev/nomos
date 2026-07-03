package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.generation._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

/**
 * Two fields in the same package can each declare an inline enum with the same name. The generator
 * emits one file per enum, keyed by output path. When two same-named enums in a package carry
 * different value sets they are genuinely different types, so silently keeping one produces a
 * decoder that rejects the other's valid JSON. These specs pin that such a clash is a hard error,
 * while identical enums still collapse to one file and same-named enums in distinct packages stand.
 */
class EnumNameCollisionSpec extends AnyFlatSpec with Matchers with EitherValues {

  val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))

  def defWithEnum(name: String, field: String, enumName: String, values: List[String], sub: Option[String] = None) =
    TemplateDefinition(name,
      ObjectType(ListMap(field -> FieldDef(EnumType(enumName, values), optional = false))),
      subPackage = sub)

  "generateMulti" should "reject two same-named enums in one package with different values" in {
    val t = MultiTemplate("com.example", List(
      defWithEnum("A", "status", "Status", List("active", "inactive")),
      defWithEnum("B", "status", "Status", List("open", "closed"))
    ))
    val err = gen.generateMulti(t).left.value
    err shouldBe a[GeneratorError.TemplateError]
    err.message should include("Status")
  }

  it should "collapse two identical same-named enums in one package to a single file" in {
    val t = MultiTemplate("com.example", List(
      defWithEnum("A", "status", "Status", List("active", "inactive")),
      defWithEnum("B", "state", "Status", List("active", "inactive"))
    ))
    val files = gen.generateMulti(t).value
    files.count(_.fileName == "Status.scala") shouldBe 1
  }

  it should "allow same-named enums with different values in different packages" in {
    val t = MultiTemplate("com.example", List(
      defWithEnum("A", "status", "Status", List("active", "inactive"), sub = Some("a")),
      defWithEnum("B", "status", "Status", List("open", "closed"), sub = Some("b"))
    ))
    val files = gen.generateMulti(t).value
    files.count(_.fileName == "Status.scala") shouldBe 2
  }
}
