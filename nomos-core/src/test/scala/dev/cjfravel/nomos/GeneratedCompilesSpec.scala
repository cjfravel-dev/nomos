package dev.cjfravel.nomos

import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratedFile, GeneratorConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

import java.nio.file.Files
import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.reporters.StoreReporter

/**
 * Compiles generated output in-process with the Scala compiler. String-only assertions on
 * generated source have let type errors (e.g. an unconfigurable temporal `parse`) slip through;
 * these tests actually compile the emitted code against nomos-runtime.
 */
class GeneratedCompilesSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val parser = new TemplateParser()

  /** Compiles the generated files and returns any compiler error messages. */
  private def compileErrors(files: List[GeneratedFile]): Seq[String] = {
    val srcDir = Files.createTempDirectory("nomos-compile-src")
    val paths = files.map { f =>
      val p = srcDir.resolve(f.relativePath)
      Files.createDirectories(p.getParent)
      Files.write(p, f.content.getBytes("UTF-8"))
      p.toString
    }
    val settings = new Settings()
    settings.usejavacp.value = true
    settings.classpath.value = System.getProperty("java.class.path")
    settings.outdir.value = Files.createTempDirectory("nomos-compile-out").toString
    val reporter = new StoreReporter(settings)
    val global = new Global(settings, reporter)
    new global.Run().compile(paths)
    reporter.infos.collect { case i if i.severity == reporter.ERROR => s"${i.pos}: ${i.msg}" }.toSeq
  }

  private def generate(template: String, cfg: GeneratorConfig): List[GeneratedFile] =
    new CodeGenerator(cfg).generateMulti(parser.parseMultiTemplate(template, "com.example").value).value

  "generated code with dateType = java.util.Date" should "compile" in {
    val tmpl = """{"definitions":[{"name":"N","template":{"d":"date","ts":"datetime"}}]}"""
    val files = generate(tmpl, GeneratorConfig("com.example", "target/test-gen",
      dateType = "java.util.Date", dateTimeType = "java.util.Date"))
    compileErrors(files) shouldBe empty
  }

  "generated code with default java.time date types" should "compile" in {
    val tmpl = """{"definitions":[{"name":"N","template":{"d":"date","ts":"datetime"}}]}"""
    compileErrors(generate(tmpl, GeneratorConfig("com.example", "target/test-gen"))) shouldBe empty
  }

  "generated code across object, enum, map, optional, nullable, and array shapes" should "compile" in {
    val tmpl =
      """{"definitions":[{"name":"N","template":{
        |"id":"string",
        |"count":"int",
        |"big":"decimal",
        |"flag":"boolean",
        |"tags":["string"],
        |"meta":{"$additionalProperties":"string"},
        |"open":{"$additionalProperties":true},
        |"opt":{"$optional":"string"},
        |"nul":{"$optional":"int","nullable":true},
        |"status":{"type":"string","as":"enumType","name":"Status","enum":["active","inactive"]}
        |}}]}""".stripMargin
    compileErrors(generate(tmpl, GeneratorConfig("com.example", "target/test-gen"))) shouldBe empty
  }

  "generated code for a prefix discriminator" should "compile" in {
    val tmpl =
      """{"definitions":[{"name":"Col","template":{"$type":{
        |"discriminator":"type","variantMatch":"prefix",
        |"variants":{"Decimal":{"scale":"int"},"Varchar":{}}
        |}}}]}""".stripMargin
    compileErrors(generate(tmpl, GeneratorConfig("com.example", "target/test-gen"))) shouldBe empty
  }
}
