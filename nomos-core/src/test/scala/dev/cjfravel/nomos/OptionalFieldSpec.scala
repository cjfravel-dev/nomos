package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.generation._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

/**
 * Optional fields are always generated as Option[T] and compile. For null-based optional fields,
 * per-field `nullable: true` is used instead.
 */
class OptionalFieldSpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  private def multi = MultiTemplate("com.example",
    List(TemplateDefinition("U", ObjectType(ListMap("nickname" -> FieldDef(StringType(), optional = true))))))

  "an optional field" should "generate Option[T] and compile" in {
    val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
    val files = gen.generateMulti(multi).value
    files.map(_.content).mkString should include("nickname: Option[String]")
    compileErrors(files) shouldBe empty
  }
}
