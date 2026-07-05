package dev.cjfravel.nomos

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

import dev.cjfravel.nomos.generation.{FileWriter, GeneratedFile}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Regenerating byte-identical files on every build updates their mtime and defeats incremental compilation. FileWriter
 * skips writing when the target is already byte-identical, preserving mtime; changed content is still written.
 */
class FileWriterIncrementalSpec extends AnyFlatSpec with Matchers {

  "FileWriter" should "not rewrite a byte-identical file (preserving mtime)" in {
    val dir = Files.createTempDirectory("nomos-incr").toFile
    val fw = new FileWriter()
    val gf = GeneratedFile("p/A.scala", "object A")

    fw.writeFile(gf, dir).isRight shouldBe true
    val f = new File(dir, "p/A.scala")
    f.setLastModified(1000000000000L)
    val mtimeBefore = f.lastModified()

    // Identical content -> skipped -> mtime preserved.
    fw.writeFile(gf, dir).isRight shouldBe true
    f.lastModified() shouldBe mtimeBefore

    // Changed content -> written.
    fw.writeFile(GeneratedFile("p/A.scala", "object A2"), dir).isRight shouldBe true
    new String(Files.readAllBytes(f.toPath), UTF_8) shouldBe "object A2"
  }
}
