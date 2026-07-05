package dev.cjfravel.nomos

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.generation._
import dev.cjfravel.nomos.model._
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Generation must fail closed when files cannot be written (a green build over stale/missing sources is worse than a
 * hard error), and generated files must be written as UTF-8 so output is reproducible and non-ASCII content is not
 * corrupted on non-UTF-8 default platforms.
 */
class WriteRobustnessSpec extends AnyFlatSpec with Matchers with EitherValues {

  private def template(name: String) =
    MultiTemplate(
      "com.example",
      List(TemplateDefinition(name, ObjectType(ListMap("id" -> FieldDef(StringType(), optional = false))))))

  "generateCode" should "return Left when generated files cannot be written" in {
    // A regular file as the output dir makes every write fail (no dirs can be created under it).
    val blocker = File.createTempFile("nomos-blocker", ".tmp")
    try Nomos.generateCode(template("X"), blocker.getPath) shouldBe a[Left[_, _]]
    finally blocker.delete()
  }

  "GeneratedFile.writeTo" should "write non-ASCII content as UTF-8" in {
    val dir = Files.createTempDirectory("nomos-utf8-gf").toFile
    val content = "// é façade ☃ π"
    GeneratedFile("p/U.scala", content).writeTo(dir).isRight shouldBe true
    Files.readAllBytes(new File(dir, "p/U.scala").toPath) shouldBe content.getBytes(UTF_8)
  }

  "FileWriter" should "write non-ASCII content as UTF-8" in {
    val dir = Files.createTempDirectory("nomos-utf8-fw").toFile
    val content = "café ☕ — naïve"
    new FileWriter().writeFile(GeneratedFile("p/F.scala", content), dir).isRight shouldBe true
    Files.readAllBytes(new File(dir, "p/F.scala").toPath) shouldBe content.getBytes(UTF_8)
  }
}
