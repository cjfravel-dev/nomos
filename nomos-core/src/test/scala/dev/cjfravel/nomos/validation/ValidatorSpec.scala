package dev.cjfravel.nomos.validation

import dev.cjfravel.nomos.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

class ValidatorSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Validator" should "validate a simple valid JSON" in {
    val template = Template.simple(
      "User",
      ObjectType(ListMap(
        "name" -> FieldDef(StringType(), optional = false),
        "age" -> FieldDef(NumberType(), optional = false)
      ))
    )

    val validator = new Validator(template)
    val json = """{"name": "Alice", "age": 30}"""

    val result = validator.validate(json)
    result shouldBe a[Right[_, _]]
  }

  it should "fail on missing required field" in {
    val template = Template.simple(
      "User",
      ObjectType(ListMap(
        "name" -> FieldDef(StringType(), optional = false),
        "age" -> FieldDef(NumberType(), optional = false)
      ))
    )

    val validator = new Validator(template)
    val json = """{"name": "Alice"}"""

    val result = validator.validate(json)
    result shouldBe a[Left[_, _]]
    
    val errors = result.left.value
    errors should have length 1
    errors.head.path shouldBe "root"
    errors.head.message should include("age")
  }

  it should "accept optional fields when missing" in {
    val template = Template.simple(
      "User",
      ObjectType(ListMap(
        "name" -> FieldDef(StringType(), optional = false),
        "bio" -> FieldDef(StringType(), optional = true)
      ))
    )

    val validator = new Validator(template)
    val json = """{"name": "Alice"}"""

    val result = validator.validate(json)
    result shouldBe a[Right[_, _]]
  }

  it should "validate optional fields when present" in {
    val template = Template.simple(
      "User",
      ObjectType(ListMap(
        "name" -> FieldDef(StringType(), optional = false),
        "bio" -> FieldDef(StringType(), optional = true)
      ))
    )

    val validator = new Validator(template)
    val json = """{"name": "Alice", "bio": "Hello"}"""

    val result = validator.validate(json)
    result shouldBe a[Right[_, _]]
  }

  it should "fail on type mismatch" in {
    val template = Template.simple(
      "User",
      ObjectType(ListMap(
        "name" -> FieldDef(StringType(), optional = false),
        "age" -> FieldDef(NumberType(), optional = false)
      ))
    )

    val validator = new Validator(template)
    val json = """{"name": "Alice", "age": "thirty"}"""

    val result = validator.validate(json)
    result shouldBe a[Left[_, _]]
    
    val errors = result.left.value
    errors.exists(_.path == "root.age") shouldBe true
  }

  it should "validate string constraints - minLength" in {
    val template = Template.simple(
      "User",
      ObjectType(ListMap(
        "username" -> FieldDef(StringType(List(MinLength(3))), optional = false)
      ))
    )

    val validator = new Validator(template)
    
    // Valid
    validator.validate("""{"username": "abc"}""") shouldBe a[Right[_, _]]
    validator.validate("""{"username": "abcd"}""") shouldBe a[Right[_, _]]
    
    // Invalid
    val result = validator.validate("""{"username": "ab"}""")
    result shouldBe a[Left[_, _]]
    result.left.value.head.message should include("minLength")
  }

  it should "validate string constraints - maxLength" in {
    val template = Template.simple(
      "User",
      ObjectType(ListMap(
        "username" -> FieldDef(StringType(List(MaxLength(10))), optional = false)
      ))
    )

    val validator = new Validator(template)
    
    // Valid
    validator.validate("""{"username": "short"}""") shouldBe a[Right[_, _]]
    
    // Invalid
    val result = validator.validate("""{"username": "verylongusername"}""")
    result shouldBe a[Left[_, _]]
    result.left.value.head.message should include("maxLength")
  }

  it should "validate string constraints - pattern" in {
    val template = Template.simple(
      "User",
      ObjectType(ListMap(
        "username" -> FieldDef(StringType(List(Pattern("^[a-z]+$"))), optional = false)
      ))
    )

    val validator = new Validator(template)
    
    // Valid
    validator.validate("""{"username": "alice"}""") shouldBe a[Right[_, _]]
    
    // Invalid
    val result = validator.validate("""{"username": "Alice123"}""")
    result shouldBe a[Left[_, _]]
    result.left.value.head.message should include("pattern")
  }

  it should "validate string format - email" in {
    val template = Template.simple(
      "User",
      ObjectType(ListMap(
        "email" -> FieldDef(StringType(List(Format("email"))), optional = false)
      ))
    )

    val validator = new Validator(template)
    
    // Valid
    validator.validate("""{"email": "user@example.com"}""") shouldBe a[Right[_, _]]
    
    // Invalid
    val result = validator.validate("""{"email": "not-an-email"}""")
    result shouldBe a[Left[_, _]]
    result.left.value.head.message should include("email")
  }

  it should "validate number constraints - min" in {
    val template = Template.simple(
      "Product",
      ObjectType(ListMap(
        "price" -> FieldDef(NumberType(List(Min(0))), optional = false)
      ))
    )

    val validator = new Validator(template)
    
    // Valid
    validator.validate("""{"price": 0}""") shouldBe a[Right[_, _]]
    validator.validate("""{"price": 10}""") shouldBe a[Right[_, _]]
    
    // Invalid
    val result = validator.validate("""{"price": -5}""")
    result shouldBe a[Left[_, _]]
    result.left.value.head.message should include("min")
  }

  it should "validate number constraints - max" in {
    val template = Template.simple(
      "Age",
      ObjectType(ListMap(
        "value" -> FieldDef(NumberType(List(Max(150))), optional = false)
      ))
    )

    val validator = new Validator(template)
    
    // Valid
    validator.validate("""{"value": 30}""") shouldBe a[Right[_, _]]
    validator.validate("""{"value": 150}""") shouldBe a[Right[_, _]]
    
    // Invalid
    val result = validator.validate("""{"value": 200}""")
    result shouldBe a[Left[_, _]]
    result.left.value.head.message should include("max")
  }

  it should "validate arrays" in {
    val template = Template.simple(
      "Post",
      ObjectType(ListMap(
        "tags" -> FieldDef(ArrayType(StringType()), optional = false)
      ))
    )

    val validator = new Validator(template)
    
    // Valid
    validator.validate("""{"tags": ["scala", "chisel"]}""") shouldBe a[Right[_, _]]
    validator.validate("""{"tags": []}""") shouldBe a[Right[_, _]]
    
    // Invalid - wrong element type
    val result = validator.validate("""{"tags": ["scala", 123]}""")
    result shouldBe a[Left[_, _]]
    result.left.value.exists(_.path == "root.tags[1]") shouldBe true
  }

  it should "validate nested objects" in {
    val template = Template.simple(
      "User",
      ObjectType(ListMap(
        "name" -> FieldDef(StringType(), optional = false),
        "address" -> FieldDef(ObjectType(ListMap(
          "street" -> FieldDef(StringType(), optional = false),
          "city" -> FieldDef(StringType(), optional = false)
        )), optional = false)
      ))
    )

    val validator = new Validator(template)
    
    // Valid
    val validJson = """{"name": "Alice", "address": {"street": "123 Main St", "city": "NYC"}}"""
    validator.validate(validJson) shouldBe a[Right[_, _]]
    
    // Invalid - missing nested field
    val invalidJson = """{"name": "Alice", "address": {"street": "123 Main St"}}"""
    val result = validator.validate(invalidJson)
    result shouldBe a[Left[_, _]]
    result.left.value.exists(_.path == "root.address") shouldBe true
  }

  it should "validate type discriminator" in {
    val template = Template.simple(
      "Shape",
      TypeDiscriminator(
        fieldName = "type",
        variants = ListMap(
          "circle" -> ObjectType(ListMap(
            "radius" -> FieldDef(NumberType(), optional = false)
          )),
          "rectangle" -> ObjectType(ListMap(
            "width" -> FieldDef(NumberType(), optional = false),
            "height" -> FieldDef(NumberType(), optional = false)
          ))
        )
      )
    )

    val validator = new Validator(template)
    
    // Valid circle
    validator.validate("""{"type": "circle", "radius": 5}""") shouldBe a[Right[_, _]]
    
    // Valid rectangle
    validator.validate("""{"type": "rectangle", "width": 10, "height": 20}""") shouldBe a[Right[_, _]]
    
    // Invalid discriminator value
    val result1 = validator.validate("""{"type": "triangle", "base": 10}""")
    result1 shouldBe a[Left[_, _]]
    result1.left.value.head.message should include("Invalid discriminator")
    
    // Missing discriminator field
    val result2 = validator.validate("""{"radius": 5}""")
    result2 shouldBe a[Left[_, _]]
    result2.left.value.head.message should include("type")
  }

  it should "validate discriminator with common fields" in {
    val template = Template.simple(
      "Event",
      TypeDiscriminator(
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
        )
      )
    )

    val validator = new Validator(template)
    
    // Valid - all fields present
    val validJson = """{"eventType": "click", "timestamp": "2024-01-01", "x": 10, "y": 20}"""
    validator.validate(validJson) shouldBe a[Right[_, _]]
    
    // Invalid - missing common field
    val invalidJson = """{"eventType": "click", "x": 10, "y": 20}"""
    val result = validator.validate(invalidJson)
    result shouldBe a[Left[_, _]]
    result.left.value.exists(e => e.message.contains("timestamp")) shouldBe true
  }

  it should "collect multiple errors" in {
    val template = Template.simple(
      "User",
      ObjectType(ListMap(
        "name" -> FieldDef(StringType(), optional = false),
        "age" -> FieldDef(NumberType(), optional = false),
        "email" -> FieldDef(StringType(), optional = false)
      ))
    )

    val validator = new Validator(template)
    val json = """{"name": 123}"""

    val result = validator.validate(json)
    result shouldBe a[Left[_, _]]
    
    val errors = result.left.value
    errors.length should be >= 2  // type mismatch for name + missing age and email
  }

  it should "provide detailed error paths" in {
    val template = Template.simple(
      "Data",
      ObjectType(ListMap(
        "users" -> FieldDef(ArrayType(ObjectType(ListMap(
          "name" -> FieldDef(StringType(), optional = false)
        ))), optional = false)
      ))
    )

    val validator = new Validator(template)
    val json = """{"users": [{"name": "Alice"}, {"name": 123}]}"""

    val result = validator.validate(json)
    result shouldBe a[Left[_, _]]
    
    val errors = result.left.value
    errors.exists(_.path == "root.users[1].name") shouldBe true
  }

  it should "validate boolean types" in {
    val template = Template.simple(
      "Config",
      ObjectType(ListMap(
        "enabled" -> FieldDef(BooleanType(), optional = false)
      ))
    )

    val validator = new Validator(template)
    
    // Valid
    validator.validate("""{"enabled": true}""") shouldBe a[Right[_, _]]
    validator.validate("""{"enabled": false}""") shouldBe a[Right[_, _]]
    
    // Invalid
    val result = validator.validate("""{"enabled": "yes"}""")
    result shouldBe a[Left[_, _]]
    result.left.value.head.message should include("Type mismatch")
  }

  it should "handle invalid JSON syntax" in {
    val template = Template.simple(
      "User",
      ObjectType(ListMap("name" -> FieldDef(StringType(), optional = false)))
    )

    val validator = new Validator(template)
    val json = """invalid json"""  // Completely invalid JSON

    val result = validator.validate(json)
    result shouldBe a[Left[_, _]]
    result.left.value.head.message should include("Invalid JSON")
  }
}