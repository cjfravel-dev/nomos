package dev.cjfravel.nomos

import dev.cjfravel.nomos.model.TypeDiscriminator
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.validation.MultiValidator
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratedFile, GeneratorConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

import java.nio.file.Files
import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.reporters.StoreReporter

/**
 * A `fallbackVariant` turns an unrecognized discriminator value into a catch-all case class that
 * preserves the raw object and re-emits it verbatim on encode (forward compatibility), instead of
 * failing. These tests compile AND execute the generated decode/encode to prove the round-trip.
 */
class FallbackVariantSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val parser = new TemplateParser()

  /** Compiles the given sources to a fresh out dir, returning (errors, outDir). */
  private def compile(files: List[GeneratedFile]): (Seq[String], String) = {
    val srcDir = Files.createTempDirectory("nomos-fb-src")
    val paths = files.map { f =>
      val p = srcDir.resolve(f.relativePath)
      Files.createDirectories(p.getParent)
      Files.write(p, f.content.getBytes("UTF-8"))
      p.toString
    }
    val outDir = Files.createTempDirectory("nomos-fb-out").toString
    val settings = new Settings()
    settings.usejavacp.value = true
    settings.classpath.value = System.getProperty("java.class.path")
    settings.outdir.value = outDir
    val reporter = new StoreReporter(settings)
    val global = new Global(settings, reporter)
    new global.Run().compile(paths)
    val errs = reporter.infos.collect { case i if i.severity == reporter.ERROR => s"${i.pos}: ${i.msg}" }.toSeq
    (errs, outDir)
  }

  /** Compiles sources + a driver, then invokes `<driverFqn>.run()` and returns its String result. */
  private def runDriver(files: List[GeneratedFile], driverFqn: String): String = {
    val (errs, outDir) = compile(files)
    errs shouldBe empty
    val loader = new java.net.URLClassLoader(Array(new java.io.File(outDir).toURI.toURL), getClass.getClassLoader)
    loader.loadClass(driverFqn).getMethod("run").invoke(null).asInstanceOf[String]
  }

  private def generate(template: String): List[GeneratedFile] =
    new CodeGenerator(GeneratorConfig("com.example", "target/test-gen"))
      .generateMulti(parser.parseMultiTemplate(template, "com.example").value).value

  private val shapeTmpl =
    """{"definitions":[{"name":"Shape","template":{"$type":{
      |"discriminator":"kind","fallbackVariant":"UnknownShape","variants":{"circle":{"radius":"int"}}}}}]}""".stripMargin

  "parser" should "capture fallbackVariant" in {
    val disc = parser.parseMultiTemplate(shapeTmpl, "com.example").value
      .definitions.head.templateType.asInstanceOf[TypeDiscriminator]
    disc.fallbackVariant shouldBe Some("UnknownShape")
  }

  "an unknown variant (plain discriminator)" should "decode into the fallback and re-emit the raw payload" in {
    val driver =
      """package com.example
        |import dev.cjfravel.nomos.json._
        |object ShapeDriver {
        |  def run(): String = {
        |    val unknown = Json.parse("{\"kind\":\"hexagon\",\"sides\":6}").toOption.get
        |    Shape.decode(unknown) match {
        |      case Right(u: UnknownShape) => "ok:" + u.kind + ":" + Json.write(Shape.encode(u))
        |      case other => "unexpected:" + other
        |    }
        |  }
        |}
        |""".stripMargin
    val files = GeneratedFile("com/example/ShapeDriver.scala", driver) :: generate(shapeTmpl)
    runDriver(files, "com.example.ShapeDriver") shouldBe """ok:hexagon:{"kind":"hexagon","sides":6}"""
  }

  "an unknown variant (variantNames + common fields)" should "decode the common field and round-trip the raw payload" in {
    val tmpl =
      """{"definitions":[{"name":"Condition","template":{"$type":{
        |"discriminator":"type","fallbackVariant":"UnknownCondition","commonFields":{"id":"string"},
        |"variants":{"threshold":{"limit":"int"}},"variantNames":{"threshold":"ThresholdCondition"}}}}]}""".stripMargin
    val driver =
      """package com.example
        |import dev.cjfravel.nomos.json._
        |object CondDriver {
        |  def run(): String = {
        |    val unknown = Json.parse("{\"type\":\"velocity\",\"id\":\"c1\",\"speed\":9}").toOption.get
        |    Condition.decode(unknown) match {
        |      case Right(u: UnknownCondition) => "ok:" + u.id + ":" + Json.write(Condition.encode(u))
        |      case other => "unexpected:" + other
        |    }
        |  }
        |}
        |""".stripMargin
    val files = GeneratedFile("com/example/CondDriver.scala", driver) :: generate(tmpl)
    runDriver(files, "com.example.CondDriver") shouldBe """ok:c1:{"type":"velocity","id":"c1","speed":9}"""
  }

  "validation" should "accept an unknown variant when a fallback is configured and reject it otherwise" in {
    val withoutFb =
      """{"definitions":[{"name":"Shape","template":{"$type":{
        |"discriminator":"kind","variants":{"circle":{"radius":"int"}}}}}]}""".stripMargin
    val payload = dev.cjfravel.nomos.json.Json.parse("""{"kind":"hexagon","sides":6}""").value

    new MultiValidator(parser.parseMultiTemplate(shapeTmpl, "com.example").value)
      .validateJson(payload, "com.example.Shape") shouldBe Symbol("right")
    new MultiValidator(parser.parseMultiTemplate(withoutFb, "com.example").value)
      .validateJson(payload, "com.example.Shape") shouldBe Symbol("left")
  }
}
