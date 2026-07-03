package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

class CrossTemplateRefSpec extends AnyFlatSpec with Matchers with EitherValues {

  val rootT = MultiTemplate("com.example.a", List(TemplateDefinition("Root",
    ObjectType(ListMap("child" -> FieldDef(ReferenceType("Child"), false))))))
  val childT = MultiTemplate("com.example.b", List(TemplateDefinition("Child",
    ObjectType(ListMap("id" -> FieldDef(StringType(), false))))))

  "combine" should "merge definitions into a shared space preserving packages" in {
    val merged = MultiTemplate.combine(List(rootT, childT)).value
    merged.getDefinition("Root").map(merged.fqn(_)) shouldBe Some("com.example.a.Root")
    merged.getDefinition("Child").map(merged.fqn(_)) shouldBe Some("com.example.b.Child")
  }

  "generation" should "resolve a cross-template ref and import it" in {
    val merged = MultiTemplate.combine(List(rootT, childT)).value
    val files = new CodeGenerator(GeneratorConfig("", "target/test-gen")).generateMulti(merged).value
    val root = files.find(_.fileName == "Root.scala").get
    root.content should include("package com.example.a")
    root.content should include("import com.example.b.Child")
    root.content should include("child: Child")
    files.find(_.fileName == "NomosFormats.scala").get.content should include("package com.example")
  }

  "combine of one template" should "keep its base package for NomosFormats" in {
    val merged = MultiTemplate.combine(List(MultiTemplate("com.example.models",
      List(TemplateDefinition("User", ObjectType(ListMap("id" -> FieldDef(StringType(), false))), Some("user")))))).value
    merged.basePackage shouldBe "com.example.models"
    merged.fqn(merged.definitions.head) shouldBe "com.example.models.user.User"
  }
}
