package dev.cjfravel.nomos

import java.io.File

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FromJsonStylePluginPathSpec extends AnyFlatSpec with Matchers with EitherValues {

  val tmplJson = """{"fromJsonStyle":"throwing","definitions":[{"name":"U","template":{"id":"string"}}]}"""
  val tmpDir = new File(System.getProperty("java.io.tmpdir"), "nomos-style-" + System.nanoTime())

  "the plugin path (parseTemplateDeferred + generateAll)" should "honor throwing fromJsonStyle" in {
    val t = Nomos.parseTemplateDeferred(tmplJson, "com.example").value
    t.fromJsonStyle shouldBe "throwing"
    val templates = new java.util.ArrayList[dev.cjfravel.nomos.model.MultiTemplate]()
    templates.add(t)
    Nomos.generateAll(templates, tmpDir.getAbsolutePath).value
    val content = scala.io.Source.fromFile(new File(tmpDir, "com/example/U.scala")).mkString
    content should include("def fromJson(json: String): U =")
    content should not include "def fromJson(json: String): Either"
  }

  it should "honor throwing style even when a non-styled template is processed first" in {
    val plain =
      Nomos.parseTemplateDeferred("""{"definitions":[{"name":"A","template":{"id":"string"}}]}""", "com.example").value
    val styled = Nomos.parseTemplateDeferred(tmplJson, "com.example").value
    val templates = new java.util.ArrayList[dev.cjfravel.nomos.model.MultiTemplate]()
    templates.add(plain)
    templates.add(styled)
    val dir = new File(System.getProperty("java.io.tmpdir"), "nomos-style2-" + System.nanoTime())
    Nomos.generateAll(templates, dir.getAbsolutePath).value
    val content = scala.io.Source.fromFile(new File(dir, "com/example/U.scala")).mkString
    content should include("def fromJson(json: String): U =")
    content should not include "def fromJson(json: String): Either"
  }

  it should "carry visibility through generateAll (combine) even when a non-visibility template is first" in {
    val plain =
      Nomos.parseTemplateDeferred("""{"definitions":[{"name":"A","template":{"id":"string"}}]}""", "com.example").value
    val vis =
      Nomos
        .parseTemplateDeferred(
          """{"visibility":"private[example]","definitions":[{"name":"V","template":{"id":"string"}}]}""",
          "com.example")
        .value
    val templates = new java.util.ArrayList[dev.cjfravel.nomos.model.MultiTemplate]()
    templates.add(plain)
    templates.add(vis)
    val dir = new File(System.getProperty("java.io.tmpdir"), "nomos-vis-" + System.nanoTime())
    Nomos.generateAll(templates, dir.getAbsolutePath).value
    val content = scala.io.Source.fromFile(new File(dir, "com/example/V.scala")).mkString
    content should include("private[example] case class V(")
    content should include("private[example] object V {")
  }
}
