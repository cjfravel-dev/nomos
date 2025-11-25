# Simple User Example

This example demonstrates basic code generation with Chisel.

## Template

We'll create a simple User type with basic fields.

## Running the Example

```scala
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.generation._

// Define the template
val userTemplate = Template(
  name = "User",
  subPackage = Some("examples.models"),
  templateType = ObjectType(Map(
    "id" -> FieldDef(StringType(), optional = false),
    "username" -> FieldDef(StringType(), optional = false),
    "email" -> FieldDef(StringType(), optional = false),
    "age" -> FieldDef(NumberType(), optional = false),
    "bio" -> FieldDef(StringType(), optional = true)
  ))
)

// Configure the generator
val config = GeneratorConfig(
  basePackage = "com.example",
  outputDir = "target/generated-sources"
)

// Generate code
val generator = new CodeGenerator(config)
val result = generator.generate(userTemplate)

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

The above will generate `target/generated-sources/com/example/examples/models/User.scala`:

```scala
package com.example.examples.models

case class User(
  id: String,
  username: String,
  email: String,
  age: Double,
  bio: Option[String]
)