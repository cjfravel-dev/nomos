package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.generation._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

/**
 * With useOptionTypes=false an optional field rendered as its raw type, but decode still bound an
 * Option and encode still called .map, so the generated code did not compile. That mode is rejected
 * at generation (per-field `nullable: true` provides null-based optional fields), and the supported
 * useOptionTypes=true path is compiled here to prove optional fields work.
 */
class UseOptionTypesSpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  private def multi = MultiTemplate("com.example",
    List(TemplateDefinition("U", ObjectType(ListMap("nickname" -> FieldDef(StringType(), optional = true))))))

  "generateMulti" should "reject useOptionTypes=false" in {
    val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen", useOptionTypes = false))
    val err = gen.generateMulti(multi).left.value
    err shouldBe a[GeneratorError.TemplateError]
    err.message should include("useOptionTypes")
  }

  "an optional field with useOptionTypes=true" should "compile" in {
    val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
    compileErrors(gen.generateMulti(multi).value) shouldBe empty
  }
}
