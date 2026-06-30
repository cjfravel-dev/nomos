# Shape Type Discriminator Example

This example demonstrates discriminated unions with common fields and variants.

## Template

Place the template at `src/main/resources/nomos/templates/com/example/examples/shapes/shape.json`. The Maven plugin derives the base package `com.example.examples.shapes` from that path.

```json
{
  "useOptionTypes": true,
  "listType": "List",
  "definitions": [
    {
      "name": "Shape",
      "description": "Shape variants with a discriminator",
      "template": {
        "$type": {
          "discriminator": "shapeType",
          "includeDiscriminator": true,
          "commonFields": {
            "color": "string",
            "id": "string"
          },
          "variants": {
            "circle": {
              "radius": "number"
            },
            "rectangle": {
              "width": "number",
              "height": "number"
            },
            "triangle": {
              "base": "number",
              "height": "number"
            }
          }
        }
      }
    }
  ]
}
```

## Running the Example

With the Maven plugin configured, generate sources with:

```bash
mvn generate-sources
```

Or use the public API directly:

```scala
import dev.cjfravel.nomos.Nomos
import scala.io.Source

val source = Source.fromFile("src/main/resources/nomos/templates/com/example/examples/shapes/shape.json")
val templateJson = try source.mkString finally source.close()

val generated = for {
  template <- Nomos.parseTemplate(templateJson, "com.example.examples.shapes")
  report <- Nomos.generateCode(template, "target/generated-sources")
} yield report
```

## Generated Code

The template generates `target/generated-sources/com/example/examples/shapes/Shape.scala` plus `NomosFormats` in the same base package:

```scala
package com.example.examples.shapes

sealed trait Shape

case class Circle(
  shapeType: String,
  color: String,
  id: String,
  radius: Double
) extends Shape

case class Rectangle(
  shapeType: String,
  color: String,
  id: String,
  width: Double,
  height: Double
) extends Shape

case class Triangle(
  shapeType: String,
  color: String,
  id: String,
  base: Double,
  height: Double
) extends Shape

object Shape {
  import NomosFormats._
  import dev.cjfravel.nomos.validation.ValidationError
  import com.fasterxml.jackson.databind.JsonNode

  def fromJson(json: String): Either[String, Shape] = {
    try {
      val jsonNode = mapper.readTree(json)
      val discriminatorValue = jsonNode.get("shapeType").asText()
      discriminatorValue match {
        case "circle" => Right(mapper.treeToValue(jsonNode, classOf[Circle]))
        case "rectangle" => Right(mapper.treeToValue(jsonNode, classOf[Rectangle]))
        case "triangle" => Right(mapper.treeToValue(jsonNode, classOf[Triangle]))
        case other => Left(s"Unknown shapeType value: $other")
      }
    } catch {
      case e: Exception => Left(s"Failed to parse JSON: ${e.getMessage}")
    }
  }

  def toJson(obj: Shape): String = mapper.writeValueAsString(obj)

  def validate(json: String): Either[List[ValidationError], Shape] = {
    validator.validate(json, "com.example.examples.shapes.Shape") match {
      case Right(_) => fromJson(json).left.map(err => List(ValidationError("root", err, "valid JSON", json)))
      case Left(errors) => Left(errors)
    }
  }
}
```

## Example JSON

Valid JSON for a circle:

```json
{
  "shapeType": "circle",
  "color": "red",
  "id": "shape-001",
  "radius": 5.0
}
```

Valid JSON for a rectangle:

```json
{
  "shapeType": "rectangle",
  "color": "blue",
  "id": "shape-002",
  "width": 10.0,
  "height": 20.0
}
```

## Without Discriminator Field

Set `includeDiscriminator` to `false` in JSON when you do not want the discriminator field emitted in variant case class constructors. Variants must still have distinguishable field structures.

```json
{
  "definitions": [
    {
      "name": "Shape",
      "template": {
        "$type": {
          "discriminator": "shapeType",
          "includeDiscriminator": false,
          "commonFields": {
            "color": "string"
          },
          "variants": {
            "circle": {
              "radius": "number"
            },
            "rectangle": {
              "width": "number",
              "height": "number"
            }
          }
        }
      }
    }
  ]
}
```

This generates variant case classes without `shapeType` constructor fields:

```scala
sealed trait Shape

case class Circle(
  color: String,
  radius: Double
) extends Shape

case class Rectangle(
  color: String,
  width: Double,
  height: Double
) extends Shape
```
