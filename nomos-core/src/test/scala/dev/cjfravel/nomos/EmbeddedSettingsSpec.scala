package dev.cjfravel.nomos

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.generation._
import dev.cjfravel.nomos.model._
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The embedded template drives runtime validation, so it must carry every project-wide setting that affects validation.
 * These specs pin that the full settings are embedded and survive the round-trip into the generated NomosFormats.
 */
class EmbeddedSettingsSpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  private val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))

  "serializeMultiTemplate" should "embed dateType, dateTimeType and visibility" in {
    val t =
      MultiTemplate(
        "com.example",
        List(TemplateDefinition("E", ObjectType(ListMap("id" -> FieldDef(StringType(), optional = false))))),
        dateType = "java.time.Instant",
        dateTimeType = "java.time.OffsetDateTime",
        visibility = Some("private[demo]"))
    val s = TemplateSerializer.serializeMultiTemplate(t)
    s should include("""dateType = "java.time.Instant"""")
    s should include("""dateTimeType = "java.time.OffsetDateTime"""")
    s should include("""visibility = Some("private[demo]")""")
  }

  "the embedded template" should "reconstruct the configured dateTimeType at runtime" in {
    val t =
      MultiTemplate(
        "com.example",
        List(TemplateDefinition("E", ObjectType(ListMap("ts" -> FieldDef(DateTimeType(), optional = false))))),
        dateTimeType = "java.time.OffsetDateTime")
    val driver =
      """package com.example
        |object EmbChk { def run(): String = NomosFormats.embeddedTemplate.dateTimeType }
        |""".stripMargin
    val files = GeneratedFile("com/example/EmbChk.scala", driver) :: gen.generateMulti(t).value
    runDriver(files, "com.example.EmbChk") shouldBe "java.time.OffsetDateTime"
  }
}
