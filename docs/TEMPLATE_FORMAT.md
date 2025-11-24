# Chisel Template Format

This document defines the JSON template format used by Chisel to generate case classes and validators.

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

### 1. Type Discriminators (Enum-like Behavior)

Use the `$type` field to define a discriminator that determines which properties are valid:

```json
{
  "$type": {
    "discriminator": "shapeType",
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
```

**Example Valid JSON:**
```json
{
  "shapeType": "circle",
  "radius": 10
}
```

**Generated Case Classes:**
```scala
sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Rectangle(width: Double, height: Double) extends Shape
case class Triangle(base: Double, height: Double) extends Shape
```

### 2. Recursive Types

Use `$ref` to create self-referential structures:

```json
{
  "name": "TreeNode",
  "value": "number",
  "children": ["$ref:TreeNode"]
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

### 4. Constraints

Add validation constraints to fields:

```json
{
  "username": {
    "type": "string",
    "minLength": 3,
    "maxLength": 20,
    "pattern": "^[a-zA-Z0-9_]+$"
  },
  "age": {
    "type": "number",
    "min": 0,
    "max": 150
  },
  "email": {
    "type": "string",
    "format": "email"
  }
}
```

## Complete Example

```json
{
  "name": "User",
  "template": {
    "id": "string",
    "username": {
      "type": "string",
      "minLength": 3,
      "maxLength": 20
    },
    "profile": {
      "email": "string",
      "age": {
        "type": "number",
        "min": 0
      },
      "bio": {
        "$optional": "string"
      }
    },
    "role": {
      "$type": {
        "discriminator": "type",
        "variants": {
          "admin": {
            "permissions": ["string"]
          },
          "user": {
            "subscription": "string"
          }
        }
      }
    }
  }
}
```

## Template Metadata

Templates can include metadata:

```json
{
  "name": "MyType",
  "description": "Description of the type",
  "version": "1.0.0",
  "template": {
    // ... actual template definition
  }
}
```

## Notes

- All field names must be valid Scala identifiers
- Type names must start with an uppercase letter
- Recursive references must be named and defined before use