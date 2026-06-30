# Nomos Template Format

This document defines the JSON template format used by Nomos to generate case classes and validators.

## Template Structure

All Nomos templates use a multi-definition format that allows you to define multiple related types in a single file. The base package is derived from the template file's location under `src/main/resources/nomos/templates`, so a template at `nomos/templates/com/example/models/user.json` generates into base package `com.example.models`:

```json
{
  "definitions": [
    {
      "name": "User",
      "subPackage": "models",
      "description": "A user in the system",
      "template": {
        // ... type definition
      }
    },
    {
      "name": "Address",
      "subPackage": "models",
      "description": "A postal address",
      "template": {
        // ... type definition
      }
    }
  ]
}
```

### Top-Level Fields

- **`definitions`** (required): Array of type definitions
- **`useOptionTypes`** (optional): Use `Option[T]` for optional fields (default: true)
- **`listType`** (optional): Collection type for arrays, "List" or "Array" (default: "List")
- **`fromJsonStyle`** (optional): `"either"` (default) makes generated `fromJson` return `Either[String, T]`; `"throwing"` makes it return `T` and throw on failure
- **`dateType`** (optional): Scala type generated for `date` fields (default: `java.time.LocalDate`)
- **`dateTimeType`** (optional): Scala type generated for `datetime` fields (default: `java.time.LocalDateTime`)
- **`mapType`** (optional): Collection type generated for maps (default: `Map`)

The base package is derived from the template path; there is no `basePackage`, `outputDir`, or `mainClass` field.

### Definition Fields

Each definition in the `definitions` array has:

- **`name`** (required): The name of the type (must start with uppercase)
- **`template`** (required): The type structure definition
- **`subPackage`** (optional): Additional package path (e.g., "models" → "com.example.models")
- **`description`** (optional): Human-readable description of the type
- **`validators`** (optional): Names of registered cross-field validators to run after schema validation (see `Nomos.validators`)

> Nomos generates the data shape, codecs, and validation only. Derived behavior (helper or
> computed members) belongs in ordinary hand-written code in the consuming language — e.g. a
> Scala extension object (`implicit class`) over the generated type — which keeps the extra
> behavior explicit and portable rather than injected into the generated class.

## Basic Types

Templates support the following primitive types:

### String
```json
{
  "name": "string"
}
```

### Number
```json
{
  "age": "number",
  "price": "number"
}
```

### Integer / Long / Decimal
Whole-number and high-precision scalars generate `Int`, `Long`, and `BigDecimal`; int/long reject fractional values.
```json
{
  "retryCount": "int",
  "fileSize": "long",
  "rate": "decimal",
  "depth": { "type": "int", "min": 1 }
}
```

### Boolean
```json
{
  "isActive": "boolean"
}
```

### Maps
Open string-keyed maps generate `Map[String, T]`; keys are unrestricted, values validated.
```json
{
  "settings": { "$map": "string" }
}
```

### Array
```json
{
  "tags": ["string"],
  "scores": { "type": "array", "items": "number", "minItems": 1, "maxItems": 5, "uniqueItems": true }
}
```

### Enums
Closed value sets on a string or array items:
```json
{
  "priority": { "type": "string", "enum": ["low", "medium", "high"] },
  "colors": { "type": "array", "items": "string", "enum": ["red", "green", "blue"] }
}
```

### Defaults
```json
{
  "verbose": { "type": "boolean", "default": false }
}
```

### Additional Properties
Per object, control unknown keys with `$additionalProperties`: `true` allows, `false` forbids (default), a type validates extras.
```json
{
  "id": "string",
  "$additionalProperties": "string"
}
```

### Named Validators
Definitions may run registered cross-field validators by name (see `Nomos.validators`).
```json
{
  "name": "Reservation",
  "validators": ["dates.startBeforeEnd"],
  "template": { "startDate": "string", "endDate": "string" }
}
```

### Nested Objects
```json
{
  "address": {
    "street": "string",
    "city": "string",
    "zipCode": "string"
  }
}
```

## Advanced Features

### 1. Type References

Use `$ref:TypeName` to reference other type definitions:

```json
{
  "definitions": [
    {
      "name": "User",
      "subPackage": "models",
      "template": {
        "name": "string",
        "email": "string",
        "address": "$ref:Address"
      }
    },
    {
      "name": "Address",
      "subPackage": "models",
      "template": {
        "street": "string",
        "city": "string",
        "state": "string",
        "zipCode": "string"
      }
    }
  ]
}
```

**Generated Case Classes:**
```scala
package com.example.models

case class User(
  name: String,
  email: String,
  address: Address
)

case class Address(
  street: String,
  city: String,
  state: String,
  zipCode: String
)
```

### 2. External Types (`$extern:` and `$gen:`)

A field may reference a type that is **not** defined in the current template set, by its
fully-qualified Scala name. There are two forms, depending on whether the target is a
nomos-generated type:

- **`$gen:<fully.qualified.Name>`** — the target is *another nomos-generated type* (typically
  generated in a different Maven module and consumed as a JAR). Generated code calls that type's
  companion `decode`/`encode` **directly**, so there is no runtime registration and the reference
  is checked at compile time. Use this for generated types you can't reach with `$ref` because they
  live in another generation unit.

  ```json
  { "owner": "$gen:com.example.shared.models.Owner" }
  ```

- **`$extern:<fully.qualified.Name>`** — the target is an *opaque, hand-written* type that nomos
  does not generate. Generated code (de)serializes it through the runtime `CodecRegistry`, so the
  application must register a codec at startup:

  ```scala
  import dev.cjfravel.nomos.serialization.CodecRegistry
  CodecRegistry.register("com.example.legacy.Money")(MyMoneyCodec)
  ```

  If no codec is registered, `fromJson`/`toJson` fail with a descriptive error. Use `$gen:` instead
  whenever the target is itself a nomos-generated type.

In both forms the field's Scala type is the fully-qualified name, emitted verbatim, and the runtime
validator treats the value as opaque (it is not schema-checked).

### 3. Recursive Types

Use `$ref:TypeName` for self-referential structures:

```json
{
  "definitions": [
    {
      "name": "TreeNode",
      "template": {
        "value": "number",
        "children": ["$ref:TreeNode"]
      }
    }
  ]
}
```

**Example Valid JSON:**
```json
{
  "value": 1,
  "children": [
    {
      "value": 2,
      "children": []
    },
    {
      "value": 3,
      "children": [
        {
          "value": 4,
          "children": []
        }
      ]
    }
  ]
}
```

**Generated Case Class:**
```scala
case class TreeNode(
  value: Double,
  children: List[TreeNode]
)
```

### 3. Optional Fields

Use `$optional` to mark fields as optional:

```json
{
  "name": "string",
  "email": "string",
  "phone": {
    "$optional": "string"
  }
}
```

**Generated Case Class:**
```scala
case class Person(
  name: String,
  email: String,
  phone: Option[String]
)
```

### 4. Type Discriminators (Enum-like Behavior)

Use the `$type` field to define a discriminator that determines which properties are valid:

```json
{
  "definitions": [
    {
      "name": "Shape",
      "template": {
        "$type": {
          "discriminator": "shapeType",
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

**Example Valid JSON:**
```json
{
  "shapeType": "circle",
  "color": "red",
  "radius": 10
}
```

**Generated Case Classes:**
```scala
sealed trait Shape

case class Circle(
  shapeType: String,
  color: String,
  radius: Double
) extends Shape

case class Rectangle(
  shapeType: String,
  color: String,
  width: Double,
  height: Double
) extends Shape

case class Triangle(
  shapeType: String,
  color: String,
  base: Double,
  height: Double
) extends Shape
```

### 5. Constraints

Add validation constraints to fields:

#### String Constraints
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

Supported string formats:
- `email` - Valid email address
- `url` - Valid HTTP/HTTPS URL
- `uuid` - Valid UUID format

#### Number Constraints
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

## Complete Example

```json
{
  "definitions": [
    {
      "name": "User",
      "subPackage": "models",
      "description": "A user in the system",
      "template": {
        "id": {
          "type": "string",
          "format": "uuid"
        },
        "username": {
          "type": "string",
          "minLength": 3,
          "maxLength": 20
        },
        "email": {
          "type": "string",
          "format": "email"
        },
        "age": {
          "$optional": {
            "type": "number",
            "min": 0,
            "max": 150
          }
        },
        "address": "$ref:Address",
        "tags": ["string"]
      }
    },
    {
      "name": "Address",
      "subPackage": "models",
      "description": "A postal address",
      "template": {
        "street": "string",
        "city": "string",
        "state": {
          "type": "string",
          "minLength": 2,
          "maxLength": 2
        },
        "zipCode": {
          "type": "string",
          "pattern": "^[0-9]{5}$"
        }
      }
    }
  ]
}
```

## Cross-Package References

When definitions have different `subPackage` values, Nomos automatically generates import statements:

```json
{
  "definitions": [
    {
      "name": "DataContract",
      "subPackage": "contracts",
      "template": {
        "name": "string",
        "owner": "$ref:Owner"
      }
    },
    {
      "name": "Owner",
      "subPackage": "owners",
      "template": {
        "service": "string",
        "team": "string"
      }
    }
  ]
}
```

Generates:
```scala
// com/example/contracts/DataContract.scala
package com.example.contracts

import com.example.owners.Owner

case class DataContract(
  name: String,
  owner: Owner
)
```

```scala
// com/example/owners/Owner.scala
package com.example.owners

case class Owner(
  service: String,
  team: String
)
```

## Notes

- All field names must be valid Scala identifiers
- Type names must start with an uppercase letter
- A `$ref` must use the exact name of another definition. When the Maven plugin generates a
  whole project, all template files under the template directory share one definition space, so
  a `$ref` may point to a definition declared in another file. A direct `Nomos.parseTemplate`
  call resolves references only within the single template string passed to it.
- Circular references are supported (e.g., TreeNode can reference itself)
- Validation targets a definition by its fully-qualified name (e.g., `com.example.models.User`)