# Chisel

A Scala library for defining JSON templates and generating case classes with validation.

## Overview

Chisel allows you to define JSON templates that describe the structure and constraints of your JSON data. From these templates, Chisel generates:
- Type-safe Scala case classes
- Validators to ensure JSON strings match the template specification

## Key Features

### 1. Type Discriminators (Enum-like Types)
Define a type key that acts as a discriminator, allowing different property sets based on the type value:

```json
{
  "type": "circle",
  "radius": 10
}
```

or

```json
{
  "type": "rectangle",
  "width": 20,
  "height": 30
}
```

### 2. Recursive Structures
Support for self-referential types, enabling tree-like and nested data structures:

```json
{
  "value": 1,
  "children": [
    {
      "value": 2,
      "children": []
    }
  ]
}
```

### 3. Comprehensive Validation
Built-in validators with rich constraint support:
- String: minLength, maxLength, pattern (regex), format (email, uuid, etc.)
- Number: min, max, multipleOf
- Arrays and nested objects
- Optional fields
- Type checking

## Installation

### Maven

```xml
<dependency>
  <groupId>dev.cjfravel</groupId>
  <artifactId>chisel</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### SBT

```scala
libraryDependencies += "dev.cjfravel" %% "chisel" % "0.1.0-SNAPSHOT"
```

## Quick Start

```scala
import dev.cjfravel.chisel.Chisel

// Define a JSON template
val templateJson = """
{
  "name": "User",
  "subPackage": "models",
  "template": {
    "id": { "type": "string", "format": "uuid" },
    "name": { "type": "string", "minLength": 1, "maxLength": 100 },
    "email": { "type": "string", "format": "email" },
    "age": { "$optional": { "type": "number", "min": 0, "max": 150 } }
  }
}
"""

// Parse template and generate code
val result = Chisel.process(
  templateJson,
  basePackage = "com.example",
  outputDir = "src/main/scala"
)

result match {
  case Right(chiselResult) =>
    println(s"✓ Generated ${chiselResult.writeReport.successCount} file(s)")
    
    // Validate JSON data
    val jsonData = """{"id": "...", "name": "John", "email": "john@example.com"}"""
    chiselResult.validator.validate(jsonData) match {
      case Right(_) => println("✓ Valid JSON")
      case Left(errors) => errors.foreach(e => println(s"✗ ${e.message}"))
    }
    
  case Left(error) =>
    println(s"✗ Error: ${error.message}")
}
```

## JSON Template Format

### Basic Types

```json
{
  "name": "Example",
  "template": {
    "stringField": "string",
    "numberField": "number",
    "boolField": "boolean",
    "arrayField": ["string"],
    "objectField": {
      "nestedProp": "string"
    }
  }
}
```

### String Constraints

```json
{
  "name": "Example",
  "template": {
    "username": {
      "type": "string",
      "minLength": 3,
      "maxLength": 20,
      "pattern": "^[a-zA-Z0-9_]+$"
    },
    "email": {
      "type": "string",
      "format": "email"
    },
    "id": {
      "type": "string",
      "format": "uuid"
    }
  }
}
```

### Number Constraints

```json
{
  "name": "Example",
  "template": {
    "age": {
      "type": "number",
      "min": 0,
      "max": 150
    },
    "price": {
      "type": "number",
      "min": 0,
      "multipleOf": 0.01
    }
  }
}
```

### Optional Fields

```json
{
  "name": "Example",
  "template": {
    "required": "string",
    "optional": {
      "$optional": "string"
    }
  }
}
```

### Type Discriminators

```json
{
  "name": "Shape",
  "template": {
    "$type": {
      "discriminator": "type",
      "includeDiscriminator": true,
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
```

This generates:

```scala
sealed trait Shape

case class Circle(
  type: String,
  color: String,
  radius: Double
) extends Shape

case class Rectangle(
  type: String,
  color: String,
  width: Double,
  height: Double
) extends Shape
```

### Recursive Types

```json
{
  "name": "TreeNode",
  "template": {
    "value": "number",
    "children": ["$ref:TreeNode"]
  }
}
```

This generates:

```scala
case class TreeNode(
  value: Double,
  children: List[TreeNode]
)
```

## API Reference

### Chisel Object

The main entry point for the library.

#### `parseTemplate(json: String): Either[ParseError, Template]`
Parse a JSON template string into a Template object.

#### `generateCode(template: Template, basePackage: String, outputDir: String): Either[GeneratorError, WriteReport]`
Generate Scala case classes from a template and write them to disk.

#### `validate(template: Template, json: String): Either[List[ValidationError], JValue]`
Validate a JSON string against a template.

#### `process(templateJson: String, basePackage: String, outputDir: String): Either[ChiselError, ChiselResult]`
Convenience method that parses the template and generates code in one step.

#### `createValidator(templateJson: String): Either[ParseError, Validator]`
Create a validator from a JSON template string.

## Examples

See the `examples/` directory for complete examples:

- [`examples/simple-user/`](examples/simple-user/) - Basic user model
- [`examples/shape-discriminator/`](examples/shape-discriminator/) - Type discriminators with sealed traits
- [`examples/recursive-tree/`](examples/recursive-tree/) - Recursive data structures
- [`examples/end-to-end/`](examples/end-to-end/) - Complete workflow demonstration

### Running the End-to-End Example

```bash
scala -cp target/classes:$(mvn dependency:build-classpath -q -DincludeScope=compile -Dmdep.outputFile=/dev/stdout) examples/end-to-end/Example.scala
```

## Building from Source

```bash
# Clone the repository
git clone https://github.com/cjfravel/chisel.git
cd chisel

# Compile
mvn compile

# Run tests (62 tests)
mvn test

# Package
mvn package
```

## Project Structure

```
chisel/
├── pom.xml                           # Maven configuration
├── README.md                         # This file
├── src/
│   ├── main/scala/dev/cjfravel/chisel/
│   │   ├── Chisel.scala             # Main API
│   │   ├── model/                   # Template type system
│   │   │   ├── Template.scala
│   │   │   ├── TemplateType.scala
│   │   │   ├── Constraint.scala
│   │   │   └── FieldDef.scala
│   │   ├── parser/                  # JSON template parser
│   │   │   ├── TemplateParser.scala
│   │   │   └── ParseError.scala
│   │   ├── generation/              # Code generator
│   │   │   ├── CodeGenerator.scala
│   │   │   ├── FileWriter.scala
│   │   │   ├── GeneratorConfig.scala
│   │   │   ├── GeneratedFile.scala
│   │   │   └── ScalaCodeBuilder.scala
│   │   └── validation/              # JSON validator
│   │       ├── Validator.scala
│   │       └── ValidationError.scala
│   └── test/scala/dev/cjfravel/chisel/
│       ├── generation/
│       │   ├── CodeGeneratorSpec.scala    # 13 tests
│       │   └── FileWriterSpec.scala       # 12 tests
│       ├── parser/
│       │   └── TemplateParserSpec.scala   # 18 tests
│       └── validation/
│           └── ValidatorSpec.scala        # 19 tests
└── examples/
    ├── simple-user/
    ├── shape-discriminator/
    ├── recursive-tree/
    └── end-to-end/
```

## Package Information

- **Package**: `dev.cjfravel.chisel`
- **Build System**: Maven
- **Language**: Scala 2.12
- **Dependencies**: json4s (JSON parsing)

## Test Coverage

All 62 tests passing:
- ✓ 13 CodeGenerator tests
- ✓ 12 FileWriter tests  
- ✓ 18 TemplateParser tests
- ✓ 19 Validator tests

## License

MIT License (or your chosen license)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Roadmap

- [ ] Support for more complex nested types
- [ ] JSON Schema import/export
- [ ] Additional format validators
- [ ] Performance optimizations
- [ ] CLI tool for command-line usage
- [ ] Plugin for popular build tools (sbt, gradle)