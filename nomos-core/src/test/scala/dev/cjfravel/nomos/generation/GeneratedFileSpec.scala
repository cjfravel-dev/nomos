package dev.cjfravel.nomos.generation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import java.io.File

class GeneratedFileSpec extends AnyFlatSpec with Matchers with EitherValues {

  "GeneratedFile" should "derive relativePath from package + type" in {
    val f = GeneratedFile("com.example.models", "User", "case class User()")
    f.relativePath shouldBe "com/example/models/User.scala"
    f.fileName shouldBe "User.scala"
  }

  it should "write content to disk, creating parent dirs" in {
    val dir = new File(System.getProperty("java.io.tmpdir"), "nomos-gf-" + System.nanoTime())
    val f = GeneratedFile("com.example", "User", "object User")
    val written = f.writeTo(dir).value
    written.exists() shouldBe true
    scala.io.Source.fromFile(written).mkString shouldBe "object User"
  }

  "GeneratorConfig" should "validate package and output dir" in {
    GeneratorConfig("com.example", "out").validate() shouldBe empty
    GeneratorConfig("", "out").validate() should not be empty
    GeneratorConfig("Bad.Pkg", "out").validate() should not be empty
    GeneratorConfig("com.example", "").validate() should not be empty
  }

  it should "build package directories and factory variants" in {
    val cfg = GeneratorConfig("com.example", "out")
    cfg.packageDirectory("com.example.models").getPath should include("com")
    GeneratorConfig.default("com.x").outputDir shouldBe "src/main/scala"
    GeneratorConfig.withArrayType("com.x", "o").listType shouldBe "Array"
  }
}
