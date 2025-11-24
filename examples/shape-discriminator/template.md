# Shape Type Discriminator Example

This example demonstrates type discriminators with common fields and variants.

## Template

We'll create a Shape type with different variants (Circle, Rectangle, Triangle) that have common fields and variant-specific fields.

## Running the Example

```scala
import dev.cjfravel.chisel.model._
import dev.cjfravel.chisel.generation._

// Define the template
val shapeTemplate = Template(
  name = "Shape",
  subPackage = Some("examples.shapes"),
  templateType = TypeDiscriminator(
    fieldName = "shapeType",
    variants = Map(
      "circle" -> ObjectType(Map(
        "radius" -> FieldDef(NumberType(), optional = false)
      )),
      "rectangle" -> ObjectType(Map(
        "width" -> FieldDef(NumberType(), optional = false),
        "height" -> FieldDef(NumberType(), optional = false)
      )),
      "triangle" -> ObjectType(Map(
        "base" -> FieldDef(NumberType(), optional = false),
        "height" -> FieldDef(NumberType(), optional = false)
      ))
    ),
    commonFields = Map(
      "color" -> FieldDef(StringType(), optional = false),
      "id" -> FieldDef(StringType(), optional = false)
    ),
    includeInOutput = true
  )
)

// Configure the generator
val config = GeneratorConfig(
  basePackage = "com.example",
  outputDir = "target/generated-sources"
)

// Generate code
val generator = new CodeGenerator(config)
val result = generator.generate(shapeTemplate)

result match {
  case Right(files) =>
    files.foreach { file =>
      println(s"Generated: ${file.relativePath}")
      file.writeTo(config.outputDirectory)
    }
  case Left(error) =>
    println(s"Error: ${error.message}")
}
```

## Generated Code

The above will generate `target/generated-sources/com/example/examples/shapes/Shape.scala`:

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

If you set `includeInOutput = false`, the discriminator field won't be in the case classes, but the variants must have different field structures to be distinguishable:

```scala
TypeDiscriminator(
  fieldName = "shapeType",
  variants = Map(
    "circle" -> ObjectType(Map(
      "radius" -> FieldDef(NumberType(), optional = false)
    )),
    "rectangle" -> ObjectType(Map(
      "width" -> FieldDef(NumberType(), optional = false),
      "height" -> FieldDef(NumberType(), optional = false)
    ))
  ),
  commonFields = Map(
    "color" -> FieldDef(StringType(), optional = false)
  ),
  includeInOutput = false
)
```

This generates:
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