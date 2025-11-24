package dev.cjfravel.chisel.parser

import dev.cjfravel.chisel.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

class TemplateParserSpec extends AnyFlatSpec with Matchers with EitherValues {

  val parser = new TemplateParser()

  "TemplateParser" should "parse a simple template with basic types" in {
    val json =
      """
        |{
        |  "name": "User",
        |  "template": {
        |    "id": "string",
        |    "age": "number",
        |    "isActive": "boolean"
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Right[_, _]]

    val template = result.value
    template.name shouldBe "User"
    template.subPackage shouldBe None

    template.templateType shouldBe a[ObjectType]
    val objType = template.templateType.asInstanceOf[ObjectType]
    objType.fields should have size 3
    objType.fields("id").fieldType shouldBe StringType()
    objType.fields("age").fieldType shouldBe NumberType()
    objType.fields("isActive").fieldType shouldBe BooleanType()
  }

  it should "parse template with subPackage" in {
    val json =
      """
        |{
        |  "name": "User",
        |  "subPackage": "models.user",
        |  "template": {
        |    "id": "string"
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Right[_, _]]

    val template = result.value
    template.subPackage shouldBe Some("models.user")
  }

  it should "parse template with description and version" in {
    val json =
      """
        |{
        |  "name": "User",
        |  "description": "User account model",
        |  "version": "1.0.0",
        |  "template": {
        |    "id": "string"
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Right[_, _]]

    val template = result.value
    template.description shouldBe Some("User account model")
    template.version shouldBe Some("1.0.0")
  }

  it should "parse array types" in {
    val json =
      """
        |{
        |  "name": "Post",
        |  "template": {
        |    "tags": ["string"],
        |    "scores": ["number"]
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Right[_, _]]

    val objType = result.value.templateType.asInstanceOf[ObjectType]
    objType.fields("tags").fieldType shouldBe ArrayType(StringType())
    objType.fields("scores").fieldType shouldBe ArrayType(NumberType())
  }

  it should "parse optional fields" in {
    val json =
      """
        |{
        |  "name": "User",
        |  "template": {
        |    "username": "string",
        |    "bio": {
        |      "$optional": "string"
        |    }
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Right[_, _]]

    val objType = result.value.templateType.asInstanceOf[ObjectType]
    objType.fields("username").optional shouldBe false
    objType.fields("bio").optional shouldBe true
    objType.fields("bio").fieldType shouldBe StringType()
  }

  it should "parse recursive references" in {
    val json =
      """
        |{
        |  "name": "TreeNode",
        |  "template": {
        |    "value": "number",
        |    "children": ["$ref:TreeNode"]
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Right[_, _]]

    val objType = result.value.templateType.asInstanceOf[ObjectType]
    objType.fields("children").fieldType shouldBe ArrayType(RecursiveRef("TreeNode"))
  }

  it should "parse string constraints" in {
    val json =
      """
        |{
        |  "name": "User",
        |  "template": {
        |    "username": {
        |      "type": "string",
        |      "minLength": 3,
        |      "maxLength": 20,
        |      "pattern": "^[a-zA-Z0-9_]+$"
        |    },
        |    "email": {
        |      "type": "string",
        |      "format": "email"
        |    }
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Right[_, _]]

    val objType = result.value.templateType.asInstanceOf[ObjectType]
    val usernameType = objType.fields("username").fieldType.asInstanceOf[StringType]
    usernameType.constraints should contain(MinLength(3))
    usernameType.constraints should contain(MaxLength(20))
    usernameType.constraints should contain(Pattern("^[a-zA-Z0-9_]+$"))

    val emailType = objType.fields("email").fieldType.asInstanceOf[StringType]
    emailType.constraints should contain(Format("email"))
  }

  it should "parse number constraints" in {
    val json =
      """
        |{
        |  "name": "Product",
        |  "template": {
        |    "price": {
        |      "type": "number",
        |      "min": 0,
        |      "max": 10000
        |    },
        |    "quantity": {
        |      "type": "number",
        |      "multipleOf": 1
        |    }
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Right[_, _]]

    val objType = result.value.templateType.asInstanceOf[ObjectType]
    val priceType = objType.fields("price").fieldType.asInstanceOf[NumberType]
    priceType.constraints should contain(Min(0))
    priceType.constraints should contain(Max(10000))

    val quantityType = objType.fields("quantity").fieldType.asInstanceOf[NumberType]
    quantityType.constraints should contain(MultipleOf(1))
  }

  it should "parse type discriminator" in {
    val json =
      """
        |{
        |  "name": "Shape",
        |  "template": {
        |    "$type": {
        |      "discriminator": "shapeType",
        |      "variants": {
        |        "circle": {
        |          "radius": "number"
        |        },
        |        "rectangle": {
        |          "width": "number",
        |          "height": "number"
        |        }
        |      }
        |    }
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Right[_, _]]

    val discType = result.value.templateType.asInstanceOf[TypeDiscriminator]
    discType.fieldName shouldBe "shapeType"
    discType.variants should have size 2
    discType.variants.contains("circle") shouldBe true
    discType.variants.contains("rectangle") shouldBe true
    
    val circleType = discType.variants("circle")
    circleType.fields should have size 1
    circleType.fields("radius").fieldType shouldBe NumberType()

    val rectType = discType.variants("rectangle")
    rectType.fields should have size 2
  }

  it should "parse type discriminator with common fields" in {
    val json =
      """
        |{
        |  "name": "Event",
        |  "template": {
        |    "$type": {
        |      "discriminator": "eventType",
        |      "commonFields": {
        |        "timestamp": "string",
        |        "userId": "string"
        |      },
        |      "variants": {
        |        "click": {
        |          "x": "number",
        |          "y": "number"
        |        },
        |        "scroll": {
        |          "delta": "number"
        |        }
        |      }
        |    }
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Right[_, _]]

    val discType = result.value.templateType.asInstanceOf[TypeDiscriminator]
    discType.commonFields should have size 2
    discType.commonFields("timestamp").fieldType shouldBe StringType()
    discType.commonFields("userId").fieldType shouldBe StringType()
  }

  it should "parse type discriminator with includeDiscriminator flag" in {
    val json =
      """
        |{
        |  "name": "Shape",
        |  "template": {
        |    "$type": {
        |      "discriminator": "type",
        |      "includeDiscriminator": false,
        |      "variants": {
        |        "circle": {
        |          "radius": "number"
        |        }
        |      }
        |    }
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Right[_, _]]

    val discType = result.value.templateType.asInstanceOf[TypeDiscriminator]
    discType.includeInOutput shouldBe false
  }

  it should "default includeDiscriminator to true" in {
    val json =
      """
        |{
        |  "name": "Shape",
        |  "template": {
        |    "$type": {
        |      "discriminator": "type",
        |      "variants": {
        |        "circle": {
        |          "radius": "number"
        |        }
        |      }
        |    }
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Right[_, _]]

    val discType = result.value.templateType.asInstanceOf[TypeDiscriminator]
    discType.includeInOutput shouldBe true
  }

  it should "parse nested objects" in {
    val json =
      """
        |{
        |  "name": "User",
        |  "template": {
        |    "name": "string",
        |    "address": {
        |      "street": "string",
        |      "city": "string",
        |      "zipCode": "string"
        |    }
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Right[_, _]]

    val objType = result.value.templateType.asInstanceOf[ObjectType]
    objType.fields("address").fieldType shouldBe a[ObjectType]
    
    val addressType = objType.fields("address").fieldType.asInstanceOf[ObjectType]
    addressType.fields should have size 3
    addressType.fields("street").fieldType shouldBe StringType()
  }

  it should "fail on invalid JSON" in {
    val json = """{"invalid json"""

    val result = parser.parseTemplate(json)
    result shouldBe a[Left[_, _]]
    result.left.value shouldBe a[ParseError.JsonSyntaxError]
  }

  it should "fail on missing name field" in {
    val json =
      """
        |{
        |  "template": {
        |    "id": "string"
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Left[_, _]]
    result.left.value shouldBe a[ParseError.MissingField]
  }

  it should "fail on missing template field" in {
    val json =
      """
        |{
        |  "name": "User"
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Left[_, _]]
    result.left.value shouldBe a[ParseError.MissingField]
  }

  it should "fail on invalid type name" in {
    val json =
      """
        |{
        |  "name": "User",
        |  "template": {
        |    "id": "invalid_type"
        |  }
        |}
      """.stripMargin

    val result = parser.parseTemplate(json)
    result shouldBe a[Left[_, _]]
    
    // Error can be either InvalidType or MultipleErrors containing InvalidType
    result.left.value match {
      case ParseError.InvalidType(_, _, _) => succeed
      case ParseError.MultipleErrors(errors) =>
        errors.exists(_.isInstanceOf[ParseError.InvalidType]) shouldBe true
      case other => fail(s"Expected InvalidType or MultipleErrors, got $other")
    }
  }

  it should "use convenience parseString method" in {
    val json =
      """
        |{
        |  "name": "Simple",
        |  "template": {
        |    "id": "string"
        |  }
        |}
      """.stripMargin

    val result = TemplateParser.parseString(json)
    result shouldBe a[Right[_, _]]
    result.value.name shouldBe "Simple"
  }
}