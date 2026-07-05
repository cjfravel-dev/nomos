package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import dev.cjfravel.nomos.parser.TemplateParser
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Identical simple definition names are allowed in distinct sub-packages of one module (matching normal Scala/Java
 * package semantics); only collisions within a single package, or genuinely ambiguous simple-name references, are
 * rejected.
 */
class CrossPackageNamesSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val parser = new TemplateParser()

  private val sameNameTwoPackages =
    """{"definitions":[
      |{"name":"UpstreamMapping","subPackage":"lineage","template":{"src":"string"}},
      |{"name":"UpstreamMapping","subPackage":"sla","template":{"target":"int"}},
      |{"name":"LineageHolder","subPackage":"lineage","template":{"m":"$ref:UpstreamMapping"}},
      |{"name":"SlaHolder","subPackage":"sla","template":{"m":"$ref:UpstreamMapping"}}
      |]}""".stripMargin

  "parser" should "accept the same simple name in two different sub-packages" in {
    parser.parseMultiTemplate(sameNameTwoPackages, "com.example").value.definitions should have size 4
  }

  it should "still reject a duplicate name within the same package" in {
    val tmpl =
      """{"definitions":[
        |{"name":"Dup","subPackage":"a","template":{"x":"string"}},
        |{"name":"Dup","subPackage":"a","template":{"y":"string"}}
        |]}""".stripMargin
    val err = parser.parseMultiTemplate(tmpl, "com.example").left.value.toString
    err should include("Duplicate definition name: Dup")
  }

  it should "reject an ambiguous simple-name reference from an unrelated package" in {
    val tmpl =
      """{"definitions":[
        |{"name":"UpstreamMapping","subPackage":"lineage","template":{"src":"string"}},
        |{"name":"UpstreamMapping","subPackage":"sla","template":{"target":"int"}},
        |{"name":"Other","subPackage":"other","template":{"m":"$ref:UpstreamMapping"}}
        |]}""".stripMargin
    val err = parser.parseMultiTemplate(tmpl, "com.example").left.value.toString
    err should include("ambiguous type 'UpstreamMapping'")
  }

  "generator" should "place each same-named type in its own package and import none for self-references" in {
    val t = parser.parseMultiTemplate(sameNameTwoPackages, "com.example").value
    val files = new CodeGenerator(GeneratorConfig("", "target/test-gen")).generateMulti(t).value
    val lineageHolder = files.find(_.fileName == "LineageHolder.scala").get.content
    val slaHolder = files.find(_.fileName == "SlaHolder.scala").get.content

    lineageHolder should include("package com.example.lineage")
    lineageHolder should not include "import com.example.sla.UpstreamMapping"
    slaHolder should include("package com.example.sla")
    slaHolder should not include "import com.example.lineage.UpstreamMapping"
  }
}
