package dev.cjfravel.chisel.generation

import dev.cjfravel.chisel.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

class CodeGeneratorSpec extends AnyFlatSpec with Matchers with EitherValues {

  val config = GeneratorConfig(
    basePackage = "com.example",
    outputDir = "target/test-generated"
  )

  val generator = new CodeGenerator(config)

  "CodeGenerator" should "generate a simple case class from ObjectType" in {
    val template = Template(
      name = "User",
      subPackage = Some("models"),
      templateType = ObjectType(ListMap(
        "name" -> FieldDef(StringType(), optional = false),
        "age" -> FieldDef(NumberType(), optional = false),
        "email" -> FieldDef(StringType(), optional = false)
      ))
    )

    val result = generator.generate(template)
    result shouldBe a[Right[_, _]]

    val files = result.right.value
    files should have length 1

    val file = files.head
    file.fileName shouldBe "User.scala"
    file.content should include("package com.example.models")
    file.content should include("case class User(")
    file.content should include("name: String")
    file.content should include("age: Double")
    file.content should include("email: String")
  }

  it should "generate optional fields with Option type" in {
    val template = Template(
      name = "Profile",
      subPackage = None,
      templateType = ObjectType(ListMap(
        "username" -> FieldDef(StringType(), optional = false),
        "bio" -> FieldDef(StringType(), optional = true)
      ))
    )

    val result = generator.generate(template)
    result shouldBe a[Right[_, _]]

    val files = result.right.value
    files.head.content should include("username: String")
    files.head.content should include("bio: Option[String]")
  }

  it should "handle array types" in {
    val template = Template(
      name = "Post",
      subPackage = None,
      templateType = ObjectType(ListMap(
        "title" -> FieldDef(StringType(), optional = false),
        "tags" -> FieldDef(ArrayType(StringType()), optional = false)
      ))
    )

    val result = generator.generate(template)
    result shouldBe a[Right[_, _]]

    val files = result.right.value
    files.head.content should include("tags: List[String]")
  }

  it should "generate sealed trait with variants for TypeDiscriminator" in {
    val template = Template(
      name = "Shape",
      subPackage = None,
      templateType = TypeDiscriminator(
        fieldName = "type",
        variants = ListMap(
          "circle" -> ObjectType(ListMap(
            "radius" -> FieldDef(NumberType(), optional = false)
          )),
          "rectangle" -> ObjectType(ListMap(
            "width" -> FieldDef(NumberType(), optional = false),
            "height" -> FieldDef(NumberType(), optional = false)
          ))
        ),
        includeInOutput = true
      )
    )

    val result = generator.generate(template)
    result shouldBe a[Right[_, _]]

    val files = result.right.value
    files should have length 1

    val content = files.head.content
    content should include("sealed trait Shape")
    content should include("case class Circle(")
    content should include("`type`: String")
    content should include("radius: Double")
    content should include(") extends Shape")
    content should include("case class Rectangle(")
    content should include("width: Double")
    content should include("height: Double")
  }

  it should "include common fields in all variants" in {
    val template = Template(
      name = "Event",
      subPackage = None,
      templateType = TypeDiscriminator(
        fieldName = "eventType",
        variants = ListMap(
          "click" -> ObjectType(ListMap(
            "x" -> FieldDef(NumberType(), optional = false),
            "y" -> FieldDef(NumberType(), optional = false)
          )),
          "scroll" -> ObjectType(ListMap(
            "delta" -> FieldDef(NumberType(), optional = false)
          ))
        ),
        commonFields = ListMap(
          "timestamp" -> FieldDef(StringType(), optional = false)
        ),
        includeInOutput = true
      )
    )

    val result = generator.generate(template)
    result shouldBe a[Right[_, _]]

    val files = result.right.value
    val content = files.head.content
    content should include("timestamp: String")
  }

  it should "detect ambiguous variants when discriminator is not included" in {
    val template = Template(
      name = "AmbiguousShape",
      subPackage = None,
      templateType = TypeDiscriminator(
        fieldName = "type",
        variants = ListMap(
          "circle" -> ObjectType(ListMap(
            "radius" -> FieldDef(NumberType(), optional = false)
          )),
          "sphere" -> ObjectType(ListMap(
            "radius" -> FieldDef(NumberType(), optional = false)
          ))
        ),
        includeInOutput = false
      )
    )

    val result = generator.generate(template)
    result shouldBe a[Left[_, _]]

    val error = result.left.value
    error shouldBe a[GeneratorError.AmbiguityError]
    error.message should include("indistinguishable")
    error.message should include("discriminator field")
  }

  it should "not detect ambiguity when variants have different fields" in {
    val template = Template(
      name = "Shape",
      subPackage = None,
      templateType = TypeDiscriminator(
        fieldName = "type",
        variants = ListMap(
          "circle" -> ObjectType(ListMap(
            "radius" -> FieldDef(NumberType(), optional = false)
          )),
          "rectangle" -> ObjectType(ListMap(
            "width" -> FieldDef(NumberType(), optional = false),
            "height" -> FieldDef(NumberType(), optional = false)
          ))
        ),
        includeInOutput = false
      )
    )

    val result = generator.generate(template)
    result shouldBe a[Right[_, _]]
  }

  it should "escape Scala keywords in field names" in {
    val template = Template(
      name = "Config",
      subPackage = None,
      templateType = ObjectType(ListMap(
        "type" -> FieldDef(StringType(), optional = false),
        "match" -> FieldDef(StringType(), optional = false),
        "class" -> FieldDef(StringType(), optional = false)
      ))
    )

    val result = generator.generate(template)
    result shouldBe a[Right[_, _]]

    val files = result.right.value
    val content = files.head.content
    content should include("`type`: String")
    content should include("`match`: String")
    content should include("`class`: String")
  }

  it should "handle recursive types" in {
    val template = Template(
      name = "TreeNode",
      subPackage = None,
      templateType = ObjectType(ListMap(
        "value" -> FieldDef(NumberType(), optional = false),
        "children" -> FieldDef(ArrayType(RecursiveRef("TreeNode")), optional = false)
      ))
    )

    val result = generator.generate(template)
    result shouldBe a[Right[_, _]]

    val files = result.right.value
    val content = files.head.content
    content should include("children: List[TreeNode]")
  }

  it should "validate template names" in {
    val invalidTemplate = Template(
      name = "invalid-name",
      subPackage = None,
      templateType = ObjectType(ListMap.empty)
    )

    val result = generator.generate(invalidTemplate)
    result shouldBe a[Left[_, _]]
  }

  it should "require template name to start with uppercase" in {
    val invalidTemplate = Template(
      name = "user",
      subPackage = None,
      templateType = ObjectType(ListMap.empty)
    )

    val result = generator.generate(invalidTemplate)
    result shouldBe a[Left[_, _]]
    result.left.value.message should include("uppercase")
  }

  it should "use subPackage when provided" in {
    val template = Template(
      name = "User",
      subPackage = Some("models.user"),
      templateType = ObjectType(ListMap(
        "id" -> FieldDef(StringType(), optional = false)
      ))
    )

    val result = generator.generate(template)
    result shouldBe a[Right[_, _]]

    val files = result.right.value
    files.head.content should include("package com.example.models.user")
    files.head.relativePath should include("com/example/models/user/User.scala")
  }

  it should "use base package when subPackage is None" in {
    val template = Template(
      name = "User",
      subPackage = None,
      templateType = ObjectType(ListMap(
        "id" -> FieldDef(StringType(), optional = false)
      ))
    )

    val result = generator.generate(template)
    result shouldBe a[Right[_, _]]

    val files = result.right.value
    files.head.content should include("package com.example")
    files.head.relativePath should include("com/example/User.scala")
  }
}