# Chisel Examples

This document provides practical examples of using Chisel for various use cases.

## Example 1: Simple User Profile

### Template Definition

```json
{
  "name": "UserProfile",
  "template": {
    "userId": "string",
    "username": "string",
    "email": "string",
    "age": "number",
    "isActive": "boolean"
  }
}
```

### Valid JSON

```json
{
  "userId": "usr_12345",
  "username": "johndoe",
  "email": "john@example.com",
  "age": 28,
  "isActive": true
}
```

### Generated Scala Code

```scala
case class UserProfile(
  userId: String,
  username: String,
  email: String,
  age: Double,
  isActive: Boolean
)
```

## Example 2: Shape Hierarchy with Type Discriminator

### Template Definition

```json
{
  "name": "Shape",
  "template": {
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
        "polygon": {
          "sides": "number",
          "vertices": [{
            "x": "number",
            "y": "number"
          }]
        }
      }
    }
  }
}
```

### Valid JSON Examples

**Circle:**
```json
{
  "shapeType": "circle",
  "radius": 5.0
}
```

**Rectangle:**
```json
{
  "shapeType": "rectangle",
  "width": 10.0,
  "height": 20.0
}
```

**Polygon:**
```json
{
  "shapeType": "polygon",
  "sides": 3,
  "vertices": [
    {"x": 0, "y": 0},
    {"x": 1, "y": 0},
    {"x": 0.5, "y": 1}
  ]
}
```

### Generated Scala Code

```scala
sealed trait Shape

case class Circle(radius: Double) extends Shape

case class Rectangle(width: Double, height: Double) extends Shape

case class Polygon(
  sides: Double,
  vertices: List[Vertex]
) extends Shape

case class Vertex(x: Double, y: Double)
```

## Example 3: Recursive Tree Structure

### Template Definition

```json
{
  "name": "TreeNode",
  "template": {
    "id": "string",
    "value": "string",
    "children": ["$ref:TreeNode"]
  }
}
```

### Valid JSON

```json
{
  "id": "root",
  "value": "Root Node",
  "children": [
    {
      "id": "child1",
      "value": "First Child",
      "children": [
        {
          "id": "grandchild1",
          "value": "First Grandchild",
          "children": []
        }
      ]
    },
    {
      "id": "child2",
      "value": "Second Child",
      "children": []
    }
  ]
}
```

### Generated Scala Code

```scala
case class TreeNode(
  id: String,
  value: String,
  children: List[TreeNode]
)
```

## Example 4: API Response with Optional Fields

### Template Definition

```json
{
  "name": "ApiResponse",
  "template": {
    "status": "number",
    "message": "string",
    "data": {
      "$optional": {
        "id": "string",
        "timestamp": "string",
        "payload": "string"
      }
    },
    "error": {
      "$optional": {
        "code": "string",
        "details": "string"
      }
    }
  }
}
```

### Valid JSON Examples

**Success Response:**
```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "id": "req_12345",
    "timestamp": "2024-01-01T00:00:00Z",
    "payload": "..."
  }
}
```

**Error Response:**
```json
{
  "status": 400,
  "message": "Bad Request",
  "error": {
    "code": "INVALID_INPUT",
    "details": "Missing required field: username"
  }
}
```

### Generated Scala Code

```scala
case class ApiResponse(
  status: Double,
  message: String,
  data: Option[ResponseData],
  error: Option[ResponseError]
)

case class ResponseData(
  id: String,
  timestamp: String,
  payload: String
)

case class ResponseError(
  code: String,
  details: String
)
```

## Example 5: E-commerce Order System

### Template Definition

```json
{
  "name": "Order",
  "template": {
    "orderId": "string",
    "customerId": "string",
    "orderDate": "string",
    "status": "string",
    "items": [{
      "productId": "string",
      "quantity": "number",
      "price": "number"
    }],
    "shipping": {
      "address": {
        "street": "string",
        "city": "string",
        "state": "string",
        "zipCode": "string"
      },
      "method": "string"
    },
    "payment": {
      "$type": {
        "discriminator": "method",
        "variants": {
          "creditCard": {
            "cardLast4": "string",
            "cardType": "string"
          },
          "paypal": {
            "email": "string"
          },
          "bankTransfer": {
            "accountNumber": "string",
            "bankName": "string"
          }
        }
      }
    },
    "total": "number"
  }
}
```

### Valid JSON

```json
{
  "orderId": "ORD-2024-001",
  "customerId": "CUST-12345",
  "orderDate": "2024-01-15T10:30:00Z",
  "status": "processing",
  "items": [
    {
      "productId": "PROD-001",
      "quantity": 2,
      "price": 29.99
    },
    {
      "productId": "PROD-002",
      "quantity": 1,
      "price": 49.99
    }
  ],
  "shipping": {
    "address": {
      "street": "123 Main St",
      "city": "Springfield",
      "state": "IL",
      "zipCode": "62701"
    },
    "method": "standard"
  },
  "payment": {
    "method": "creditCard",
    "cardLast4": "1234",
    "cardType": "Visa"
  },
  "total": 109.97
}
```

## Example 6: Recursive Expression Tree

### Template Definition

```json
{
  "name": "Expression",
  "template": {
    "$type": {
      "discriminator": "type",
      "variants": {
        "literal": {
          "value": "number"
        },
        "variable": {
          "name": "string"
        },
        "binaryOp": {
          "operator": "string",
          "left": "$ref:Expression",
          "right": "$ref:Expression"
        },
        "unaryOp": {
          "operator": "string",
          "operand": "$ref:Expression"
        }
      }
    }
  }
}
```

### Valid JSON (representing: (x + 5) * 2)

```json
{
  "type": "binaryOp",
  "operator": "*",
  "left": {
    "type": "binaryOp",
    "operator": "+",
    "left": {
      "type": "variable",
      "name": "x"
    },
    "right": {
      "type": "literal",
      "value": 5
    }
  },
  "right": {
    "type": "literal",
    "value": 2
  }
}
```

### Generated Scala Code

```scala
sealed trait Expression

case class Literal(value: Double) extends Expression

case class Variable(name: String) extends Expression

case class BinaryOp(
  operator: String,
  left: Expression,
  right: Expression
) extends Expression

case class UnaryOp(
  operator: String,
  operand: Expression
) extends Expression
```

## Usage in Scala

```scala
import dev.cjfravel.chisel._

// Load template from file or string
val template = ChiselTemplate.fromFile("templates/order.json")

// Create validator
val validator = ChiselValidator.fromTemplate(template)

// Validate JSON string
val jsonString = """{"orderId": "ORD-001", ...}"""

validator.validate(jsonString) match {
  case Right(order) =>
    println(s"Order validated: ${order.orderId}")
  case Left(errors) =>
    errors.foreach(err => println(s"Validation error: $err"))
}

// Generate case class code
val generatedCode = ChiselGenerator.generateCaseClasses(template)
println(generatedCode)