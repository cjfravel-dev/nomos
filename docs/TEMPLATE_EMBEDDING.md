# Template Embedding Feature

## Overview

Nomos now embeds the template definition directly in the generated code, allowing downstream users to validate JSON without needing access to the original template file. This feature was added to enable validation in production environments where template files may not be available.

## How It Works

### 1. Template Serialization

When generating code, Nomos serializes the `MultiTemplate` object into Scala source code. This serialized template is embedded in the generated `NomosFormats` object.

### 2. Embedded Components

The generated `NomosFormats` object contains:

- **`embeddedTemplate`**: A lazy val containing the reconstructed `MultiTemplate`
- **`validator`**: A lazy val containing a `MultiValidator` instance initialized with the embedded template
- **`mapper`**: The Jackson `ObjectMapper` (existing functionality)

### 3. Validation Methods

Each generated companion object now includes a `validate()` method that:

1. Uses the embedded template to validate the JSON structure
2. If validation passes, parses the JSON into the case class
3. Returns either validation errors or the parsed instance

## Usage Example

### Generated Code

```scala
package com.example.models

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.validation.{MultiValidator, ValidationError}

object NomosFormats {
  val mapper: ObjectMapper = {
    val m = new ObjectMapper()
    m.registerModule(DefaultScalaModule)
    m
  }

  lazy val embeddedTemplate: MultiTemplate = {
    MultiTemplate(
      basePackage = "com.example.models",
      definitions = List(
        TemplateDefinition(
          name = "User",
          templateType = ObjectType(ListMap(
            "id" -> FieldDef(StringType(List()), optional = false),
            "email" -> FieldDef(StringType(List()), optional = false)
          )),
          subPackage = Some("user"),
          description = Some("User model")
        )
      ),
      useOptionTypes = true,
      listType = "List"
    )
  }

  lazy val validator: MultiValidator = new MultiValidator(embeddedTemplate)
}
```

### Using Validation

```scala
import com.example.models.user.User

// Validate and parse JSON in one step
val json = """{"id": "123", "email": "test@example.com"}"""

User.validate(json) match {
  case Right(user) =>
    println(s"Valid user: $user")
  
  case Left(errors) =>
    errors.foreach { error =>
      println(s"Validation error at ${error.path}: ${error.message}")
    }
}
```

### Validation Without Template File

The key benefit is that you can now validate JSON in environments where the original template file is not available:

```scala
// In production, no template file needed!
val userJson = getJsonFromApi()

User.validate(userJson) match {
  case Right(user) => processUser(user)
  case Left(errors) => logValidationErrors(errors)
}
```

## API Reference

### `User.validate(json: String)`

Validates a JSON string against the embedded template and returns a parsed instance.

**Parameters:**
- `json`: The JSON string to validate

**Returns:**
- `Either[List[ValidationError], User]`: Either a list of validation errors or the parsed User instance

**Example:**
```scala
val json = """{"id": "123", "email": "user@example.com"}"""
User.validate(json) match {
  case Right(user) => // Handle valid user
  case Left(errors) => // Handle validation errors
}
```

### `NomosFormats.embeddedTemplate`

The compiled template embedded in the generated code.

**Type:** `MultiTemplate`

**Usage:**
```scala
// Access the embedded template if needed
val template = NomosFormats.embeddedTemplate
```

### `NomosFormats.validator`

A validator instance initialized with the embedded template.

**Type:** `MultiValidator`

**Usage:**
```scala
// Validate against a specific definition (fully-qualified name includes the subPackage)
NomosFormats.validator.validate(json, "com.example.models.user.User") match {
  case Right(jsonNode) => // Valid JSON structure
  case Left(errors) => // Validation errors
}
```

## Benefits

1. **No Template File Required**: Validation works without access to the original `.json` template file
2. **Production Ready**: Perfect for deployed applications where template files may not be available
3. **Type-Safe**: Full Scala type safety with case class parsing
4. **Comprehensive Validation**: Validates all constraints defined in the template (patterns, formats, required fields, etc.)
5. **Single Source of Truth**: Template and validation logic stay in sync

## Implementation Details

### Template Serialization

The `TemplateSerializer` object converts `MultiTemplate` and related model objects into Scala source code:

- Handles all `TemplateType` variants (StringType, NumberType, ObjectType, etc.)
- Preserves constraints (pattern, format, min/max, etc.)
- Maintains field order using `ListMap`
- Properly escapes strings for Scala source code

### Code Generation

The `CodeGenerator` class:

1. Calls `TemplateSerializer.serializeMultiTemplate()` to generate the embedded template code
2. Adds the serialized template to `NomosFormats`
3. Creates a `MultiValidator` instance
4. Adds `validate()` methods to each companion object with proper imports

## Limitations

- The embedded template increases the size of generated files
- Template must be fully resolved at generation time (no dynamic references)
- Validation happens at runtime (not compile time)

## Migration Guide

Existing code continues to work without changes. The new `validate()` method is additive and doesn't affect:

- Existing `fromJson()` / `toJson()` methods
- Direct JSON parsing with Jackson
- Any existing validation logic

To adopt the new feature, simply replace manual validation with calls to the generated `validate()` method.