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

The base package is derived from the template path; there is no `basePackage`, `outputDir`, or `mainClass` field.

### Definition Fields

Each definition in the `definitions` array has:

- **`name`** (required): The name of the type (must start with uppercase)
- **`template`** (required): The type structure definition
- **`subPackage`** (optional): Additional package path (e.g., "models" → "com.example.models")
- **`description`** (optional): Human-readable description of the type

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

### Boolean
```json
{
  "isActive": "boolean"
}
```

### Array
```json
{
  "tags": ["string"],
  "scores": ["number"]
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

### 2. Recursive Types

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
- References must use the exact name of another definition in the same template
- Circular references are supported (e.g., TreeNode can reference itself)
- Validation targets a definition by its fully-qualified name (e.g., `com.example.models.User`)