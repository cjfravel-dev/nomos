package dev.cjfravel.nomos.generation

import dev.cjfravel.nomos.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

class CodeGeneratorMultiSpec extends AnyFlatSpec with Matchers with EitherValues {

  val cfg = GeneratorConfig("com.example", "target/test-gen")
  val gen = new CodeGenerator(cfg)

  def multi(defs: TemplateDefinition*) = MultiTemplate("com.example", defs.toList)

  "generateMulti" should "emit NomosFormats plus one file per definition" in {
    val t = multi(
      TemplateDefinition("User", ObjectType(ListMap("id" -> FieldDef(StringType(), optional = false))), Some("models")),
      TemplateDefinition("Address", ObjectType(ListMap("zip" -> FieldDef(StringType(), optional = false))), Some("models"))
    )
    val files = gen.generateMulti(t).value
    files.map(_.fileName) should contain allOf ("NomosFormats.scala", "User.scala", "Address.scala")
    files.find(_.fileName == "NomosFormats.scala").get.content should include("embeddedTemplate")
  }

  it should "generate a sealed trait + variants for a discriminator with FQN validate" in {
    val disc = TypeDiscriminator("shapeType",
      ListMap(
        "circle" -> ObjectType(ListMap("radius" -> FieldDef(NumberType(), optional = false))),
        "rectangle" -> ObjectType(ListMap("w" -> FieldDef(NumberType(), optional = false)))
      ), ListMap.empty, true, Map.empty)
    val files = gen.generateMulti(multi(TemplateDefinition("Shape", disc, Some("shapes")))).value
    val shape = files.find(_.fileName == "Shape.scala").get.content
    shape should include("sealed trait Shape")
    shape should include("validator.validate(json, \"com.example.shapes.Shape\")")
  }

  it should "group variants by custom variantNames" in {
    val disc = TypeDiscriminator("t",
      ListMap("a" -> ObjectType(ListMap("x" -> FieldDef(NumberType(), optional = false)))),
      ListMap("c" -> FieldDef(StringType(), optional = false)), true, Map("a" -> "Alpha"))
    val files = gen.generateMulti(multi(TemplateDefinition("E", disc))).value
    files.find(_.fileName == "E.scala").get.content should include("Alpha")
  }

  it should "support recursive references and Array list type" in {
    val arrCfg = new CodeGenerator(GeneratorConfig("com.example", "o", listType = "Array"))
    val node = TemplateDefinition("TreeNode", ObjectType(ListMap(
      "id" -> FieldDef(StringType(), optional = false),
      "children" -> FieldDef(ArrayType(RecursiveRef("TreeNode")), optional = false)
    )))
    val content = arrCfg.generateMulti(multi(node)).value.find(_.fileName == "TreeNode.scala").get.content
    content should include("Array[TreeNode]")
  }

  it should "fail on an unresolved reference" in {
    val bad = TemplateDefinition("A", ObjectType(ListMap("b" -> FieldDef(ReferenceType("Missing"), optional = false))))
    gen.generateMulti(multi(bad)) shouldBe a[Left[_, _]]
  }
}
