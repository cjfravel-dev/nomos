package dev.cjfravel.nomos

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.generation._
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.parser.TemplateParser
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * `$map` fields render as `scala.collection.immutable.Map` by default, but the project-wide `mapType` setting can
 * select `java.util.Map` for a Java-interop-friendly public surface. The concrete field type and its decode/encode
 * wiring change; validation is unchanged.
 */
class MapTypeSpec extends AnyFlatSpec with Matchers with EitherValues with CompileHarness {

  private val parser = new TemplateParser()

  private def mapDef =
    TemplateDefinition("N", ObjectType(ListMap("m" -> FieldDef(MapType(StringType()), optional = false))))

  "the default map field" should "render as Map[String, V] and round-trip" in {
    val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
    val files = gen.generateMulti(MultiTemplate("com.example", List(mapDef))).value
    files.map(_.content).mkString should include("m: Map[String, String]")
    val driver =
      GeneratedFile(
        "com/example/Drv.scala",
        """package com.example
        |object Drv {
        |  def run(): String = N.fromJson("{\"m\":{\"a\":\"1\",\"b\":\"2\"}}") match {
        |    case Right(n) => val m: Map[String, String] = n.m; s"${m("a")},${N.toJson(n).contains("\"a\"")}"
        |    case Left(e) => "err:" + e
        |  }
        |}
        |""".stripMargin)
    runDriver(driver :: files, "com.example.Drv") shouldBe "1,true"
  }

  "mapType = java.util.Map" should "render java.util.Map[String, V] and round-trip via decode/encode" in {
    val gen = new CodeGenerator(GeneratorConfig("com.example", "target/test-gen", mapType = "java.util.Map"))
    val files = gen.generateMulti(MultiTemplate("com.example", List(mapDef), mapType = "java.util.Map")).value
    files.map(_.content).mkString should include("m: java.util.Map[String, String]")
    val driver =
      GeneratedFile(
        "com/example/Drv.scala",
        """package com.example
        |object Drv {
        |  def run(): String = N.fromJson("{\"m\":{\"a\":\"1\",\"b\":\"2\"}}") match {
        |    case Right(n) => val m: java.util.Map[String, String] = n.m;
        |      s"${m.get("a")},${m.get("b")},${N.toJson(n).contains("\"b\"")}"
        |    case Left(e) => "err:" + e
        |  }
        |}
        |""".stripMargin)
    runDriver(driver :: files, "com.example.Drv") shouldBe "1,2,true"
  }

  "parseMultiTemplate" should "accept a top-level mapType of java.util.Map" in {
    val t =
      parser
        .parseMultiTemplate(
          """{"mapType":"java.util.Map","definitions":[{"name":"N","template":{"m":{"$map":"string"}}}]}""",
          "com.example")
        .value
    t.mapType shouldBe "java.util.Map"
  }

  it should "default mapType to Map" in {
    parser
      .parseMultiTemplate("""{"definitions":[{"name":"N","template":{"m":{"$map":"string"}}}]}""", "com.example")
      .value
      .mapType shouldBe "Map"
  }

  it should "reject an unsupported mapType with an actionable message" in {
    parser.parseMultiTemplate(
      """{"mapType":"java.util.HashMap","definitions":[{"name":"N","template":{"m":{"$map":"string"}}}]}""",
      "com.example") shouldBe a[Left[_, _]]
  }

  "serializeMultiTemplate" should "embed the configured mapType" in {
    val s =
      TemplateSerializer.serializeMultiTemplate(MultiTemplate("com.example", List(mapDef), mapType = "java.util.Map"))
    s should include("""mapType = "java.util.Map"""")
  }
}
