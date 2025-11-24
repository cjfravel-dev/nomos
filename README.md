# Chisel

A Scala library for defining JSON templates with multiple type definitions and generating case classes with validation.

## Overview

Chisel allows you to define JSON templates that describe the structure and constraints of your JSON data. From these templates, Chisel generates:
- Type-safe Scala case classes with automatic imports for cross-references
- Validators to ensure JSON strings match the template specification
- Support for multiple related types in a single template file

## Key Features

### 1. Multiple Type Definitions with References
Define multiple related types in a single template and reference them using `$ref:TypeName`:

```json
{
  "basePackage": "com.example",
  "outputDir": "src/main/scala",
  "mainClass": "User",
  "definitions": [
    {
      "name": "User",
      "template": {
        "name": "string",
        "address": "$ref:Address"
      }
    },
    {
      "name": "Address",
      "template": {
        "street": "string",
        "city": "string"
      }
    }
  ]
}
```

### 2. Type Discriminators (Enum-like Types)
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

### 3. Recursive Structures
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

### 4. Comprehensive Validation
Built-in validators with rich constraint support:
- String: minLength, maxLength, pattern (regex), format (email, uuid, url)
- Number: min, max, multipleOf
- Arrays and nested objects
- Optional fields
- Type checking
- Cross-reference validation

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

// Define a JSON template with multiple definitions
val templateJson = """
{
  "basePackage": "com.example",
  "outputDir": "src/main/scala",
  "mainClass": "User",
  "definitions": [
    {
      "name": "User",
      "subPackage": "models",
      "template": {
        "id": { "type": "string", "format": "uuid" },
        "name": { "type": "string", "minLength": 1, "maxLength": 100 },
        "email": { "type": "string", "format": "email" },
        "age": { "$optional": { "type": "number", "min": 0, "max": 150 } },
        "address": "$ref:Address"
      }
    },
    {
      "name": "Address",
      "subPackage": "models",
      "template": {
        "street": "string",
        "city": "string",
        "zipCode": { "type": "string", "pattern": "^[0-9]{5}$" }
      }
    }
  ]
}
"""

// Parse template and generate code
val result = Chisel.process(templateJson)

result match {
  case Right(chiselResult) =>
    println(s"✓ Generated ${chiselResult.writeReport.successCount} file(s)")
    
    // Validate JSON data
    val jsonData = """
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "John",
      "email": "john@example.com",
      "address": {
        "street": "123 Main St",
        "city": "Springfield",
        "zipCode": "62701"
      }
    }
    """
    chiselResult.validator.validate(jsonData) match {
      case Right(_) => println("✓ Valid JSON")
      case Left(errors) => errors.foreach(e => println(s"✗ ${e.message}"))
    }
    
  case Left(error) =>
    println(s"✗ Error: ${error.message}")
}
```

## JSON Template Format

### Template Structure

All templates use a multi-definition format:

```json
{
  "basePackage": "com.example",
  "outputDir": "src/main/scala",
  "mainClass": "MainType",
  "definitions": [
    {
      "name": "MainType",
      "subPackage": "models",
      "description": "Main type description",
      "template": { /* type definition */ }
    },
    {
      "name": "ReferencedType",
      "template": { /* type definition */ }
    }
  ]
}
```

### Basic Types

```json
{
  "stringField": "string",
  "numberField": "number",
  "boolField": "boolean",
  "arrayField": ["string"],
  "objectField": {
    "nestedProp": "string"
  }
}
```

### Type References

Use `$ref:TypeName` to reference other definitions:

```json
{
  "definitions": [
    {
      "name": "User",
      "template": {
        "name": "string",
        "address": "$ref:Address"
      }
    },
    {
      "name": "Address",
      "template": {
        "street": "string",
        "city": "string"
      }
    }
  ]
}
```

### String Constraints

```json
{
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
```

### Number Constraints

```json
{
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
```

### Optional Fields

```json
{
  "required": "string",
  "optional": {
    "$optional": "string"
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

#### `parseTemplate(json: String): Either[ParseError, MultiTemplate]`
Parse a JSON template string into a MultiTemplate object.

#### `generateCode(template: MultiTemplate): Either[GeneratorError, WriteReport]`
Generate Scala case classes from a template and write them to disk. Uses the `basePackage` and `outputDir` from the template.

#### `validate(template: MultiTemplate, json: String, definitionName: String = null): Either[List[ValidationError], JValue]`
Validate a JSON string against a template. Defaults to validating against the `mainClass`.

#### `process(templateJson: String): Either[ChiselError, ChiselResult]`
Convenience method that parses the template and generates code in one step.

#### `createValidator(templateJson: String): Either[ParseError, MultiValidator]`
Create a validator from a JSON template string.

## Examples

See the `examples/` directory for complete examples:

- [`examples/end-to-end/`](examples/end-to-end/) - Complete workflow with User → Address reference
- [`playground/`](playground/) - DataContract example with multi-definition template

### Running the End-to-End Example

```bash
mvn compile
scala -cp target/classes:$(mvn dependency:build-classpath -q -DincludeScope=compile -Dmdep.outputFile=/dev/stdout) examples/end-to-end/Example.scala
```

### Running the DataContract Example

```bash
scala -cp target/classes:$(mvn dependency:build-classpath -q -DincludeScope=compile -Dmdep.outputFile=/dev/stdout) playground/runners/DataContractRunner.scala
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
├── docs/
│   └── TEMPLATE_FORMAT.md           # Detailed format documentation
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
├── examples/
│   └── end-to-end/                   # Complete example with references
└── playground/
    ├── templates/                    # Example templates
    ├── json/                         # Example JSON data
    └── runners/                      # Example runners
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

## Documentation

- [Template Format Reference](docs/TEMPLATE_FORMAT.md) - Complete format documentation with examples
- [Implementation Plan](docs/IMPLEMENTATION_PLAN.md) - Original design and architecture

## License

MIT License (or your chosen license)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Roadmap

- [x] Multi-definition templates with cross-references
- [x] Automatic import generation
- [x] Reference resolution in validation
- [ ] Additional format validators
- [ ] JSON Schema import/export
- [ ] Performance optimizations
- [ ] CLI tool for command-line usage
- [ ] Plugin for popular build tools (sbt, gradle)