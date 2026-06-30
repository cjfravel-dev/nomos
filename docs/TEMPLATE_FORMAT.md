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

This keeps the field a `String` (or `List[String]`) and **validates** membership; it does not
create a new type.

#### Named enum type (`as: "enumType"`)

To instead generate a dedicated sealed-trait enum and type the field as that enum, add
`"as": "enumType"` and a `"name"`:

```json
{ "status": { "type": "string", "as": "enumType", "name": "Status", "enum": ["active", "inactive"] } }
```

Generates a `Status` type (in the definition's package) and types the field as `Status`:

```scala
sealed trait Status
object Status {
  case object Active extends Status
  case object Inactive extends Status
  val values: List[Status] = List(Active, Inactive)
  def fromString(s: String): Option[Status] = ...
  def asString(v: Status): String = ...
  // plus decode / encode against the on-the-wire string
}
```

Case-object names are the `PascalCase` of the enum values; encode/decode use the original
string values.

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

A field's type must be a named type, not an inline (anonymous) object. Give the nested shape its
own definition and reference it with [`$ref`](#1-type-references):

```json
{
  "definitions": [
    {
      "name": "Person",
      "template": { "name": "string", "address": "$ref:Address" }
    },
    {
      "name": "Address",
      "template": { "street": "string", "city": "string", "zipCode": "string" }
    }
  ]
}
```

Inline nested objects (an object literal as a field's value) are rejected at generation time with
a message pointing here. (An empty object with [`$additionalProperties`](#additional-properties)
is the exception — it is the supported way to express an open map.)

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

#### `nullable`: a boxed, null-defaulting field instead of `Option`

For interop with sources that represent "absent" as JSON `null` (or omission) but where you want
a plain reference type rather than `Option`, add `"nullable": true` to an `$optional` field. The
field is generated as its **raw (boxed) type defaulting to `null`** — not `Option[T]` — and a
missing or `null` value decodes to `null`:

```json
{ "score": { "$optional": "int", "nullable": true } }
```

```scala
case class Row(score: java.lang.Integer = null)   // not Option[Int]
```

`nullable` takes precedence over `optional`: a field marked both is the boxed raw type, never an
`Option`.

#### `adapter`: a named (de)serialization adapter for a string field

A required string field may declare an `adapter` to convert between its on-the-wire form and the
value stored in the case class, keeping output byte-compatible with existing payloads. Register
the named adapter at runtime; `decode` runs on parse and `encode` on output:

```json
{ "createdAt": { "type": "string", "adapter": "epochMillis" } }
```

```scala
import dev.cjfravel.nomos.Nomos
Nomos.adapters.register("epochMillis")(
  decode = wire => /* wire -> model */ wire,
  encode = model => /* model -> wire */ model
)
```

`adapter` is only supported on **string** fields (a non-string field with an adapter is rejected
at parse time). If no adapter is registered for the name at runtime, the value passes through
unchanged.

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

**Discriminator options:**

| Key | Required | Default | Purpose |
| --- | --- | --- | --- |
| `discriminator` | yes | — | Name of the field whose value selects the variant. |
| `variants` | yes | — | Map of discriminator value → that variant's object structure. |
| `commonFields` | no | `{}` | Fields shared by every variant. |
| `includeDiscriminator` | no | `true` | Emit the discriminator field on the generated case classes. |
| `variantNames` | no | `{}` | Override the generated class name for a variant key (e.g. `"string"` → `"StringColumn"`). |
| `variantMatch` | no | `"exact"` | How a discriminator value selects a variant: `"exact"` (equality) or `"prefix"` (the value *starts with* the variant key). |
| `variantSubPackage` | no | — | Emit the variant case classes into a sub-package of the trait's package (see below). |
| `fallbackVariant` | no | — | Name of a catch-all variant for unrecognized discriminator values (see below). |
| `discriminatorEnum` | no | — | Generate an enum over the discriminator values and type the discriminator field as it (see below). |

#### `variantSubPackage`: split the trait and its variants across packages

By default the sealed trait and all of its variant case classes are generated into the same
package (the definition's package). Set `variantSubPackage` to keep the **trait** in the
definition's package `P` while emitting every **variant case class** into the sub-package
`P.<variantSubPackage>`:

```json
{
  "name": "Shape",
  "subPackage": "shapes",
  "template": {
    "$type": {
      "discriminator": "kind",
      "variantSubPackage": "kinds",
      "variants": {
        "circle": { "radius": "number" },
        "square": { "side": "number" }
      }
    }
  }
}
```

With a base package of `com.example`, this generates:

- `com.example.shapes.Shape` — the sealed trait (and its `decode`/`encode` codecs)
- `com.example.shapes.kinds.Circle`, `com.example.shapes.kinds.Square` — the variant case classes

This is purely a code-layout control; it does not affect validation or serialization semantics.
It is intended for porting an existing public API whose union trait lives in one package while
its (often numerous) variant types live in a dedicated sibling package, so the generated layout
can match the existing one without relocating public types.

#### `fallbackVariant`: forward-compatible catch-all for unknown values

By default a discriminator value that matches no variant fails to decode (and fails validation).
Set `fallbackVariant` to a class name to instead capture unrecognized values in a generated
catch-all case class, so older code can read — and faithfully re-emit — documents that use a
discriminator value added later:

```json
{
  "name": "Condition",
  "template": {
    "$type": {
      "discriminator": "type",
      "fallbackVariant": "UnknownCondition",
      "commonFields": { "id": "string" },
      "variants": {
        "threshold": { "limit": "int" }
      }
    }
  }
}
```

Behavior when set:

- An unrecognized discriminator value decodes into `UnknownCondition`, which carries the
  discriminator value, every common field, and the **raw object**.
- `encode` re-emits that preserved raw object **verbatim**, so an unknown variant round-trips
  without loss.
- Validation **accepts** an unrecognized value (common fields are still enforced, since every
  variant shares them; the unknown variant-specific shape is not validated).

When unset, behavior is unchanged: an unrecognized discriminator value is rejected (fail-closed).
This keeps strict-by-default validation while allowing opt-in forward compatibility. To keep the
discriminator type-safe while still accepting unknowns, combine it with
[`discriminatorEnum`](#discriminatorenum-type-the-discriminator-as-a-generated-enum).

#### `discriminatorEnum`: type the discriminator as a generated enum

By default the discriminator field is generated as a plain `String`, so call sites compare
against string literals. Set `discriminatorEnum` to a class name to generate a sealed-trait enum
with one case object per discriminator value and type the discriminator field — on the trait and
every variant — as that enum:

```json
{
  "name": "Condition",
  "template": {
    "$type": {
      "discriminator": "type",
      "discriminatorEnum": "ConditionType",
      "variants": {
        "threshold": { "limit": "int" },
        "window": { "seconds": "int" }
      }
    }
  }
}
```

Generates `ConditionType` (in the union's package, the same machinery as the
[named enum type](#named-enum-type-as-enumtype)) and types the field as it:

```scala
sealed trait ConditionType
object ConditionType {
  case object Threshold extends ConditionType
  case object Window extends ConditionType
  // values / fromString / asString / decode / encode
}

case class Threshold(`type`: ConditionType, limit: Int) extends Condition
```

So call sites read `c.`type` == ConditionType.Window` (type-checked, discoverable) instead of
comparing to a string. Codecs map the enum to/from its JSON string, so the wire format is
unchanged. Case-object names are the `PascalCase` of the discriminator values.

`discriminatorEnum` requires `includeDiscriminator: true` (there must be a field to type) and is
incompatible with `variantMatch: "prefix"` (parameterized values are not a fixed set); these
combinations are rejected.

It **can** be combined with [`fallbackVariant`](#fallbackvariant-forward-compatible-catch-all-for-unknown-values):
the generated enum then also gets an open-ended `final case class Unknown(value: String)` member,
and the fallback variant carries the unrecognized discriminator value as `<Enum>.Unknown("…")`.
So an unknown value is both type-safe (it is still an `<Enum>`) and preserved — `encode` re-emits
the original payload verbatim, and `<Enum>.asString` recovers the original string:

```scala
sealed trait ConditionType
object ConditionType {
  case object Threshold extends ConditionType
  case object Window extends ConditionType
  final case class Unknown(value: String) extends ConditionType   // only when fallbackVariant is set
  // ...
}
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
- Two definitions may share the same simple `name` as long as they are in **different**
  sub-packages; a duplicate name within one package is an error. A bare `$ref:Name` resolves to a
  definition in the **referrer's own package** first, otherwise to the unique definition with that
  name; if the same name exists in two other packages the reference is ambiguous and must be
  qualified with `$gen:<fully.qualified.Name>`.
- Inline (anonymous) objects and discriminators are not supported as a field's type in
  multi-definition templates — give the nested shape its own named definition and reference it
  with `$ref`. (An empty object with `$additionalProperties` is the supported way to express an
  open map.)
- The runtime JSON parser bounds hostile input: maximum nesting depth 512 and maximum input size
  16 MB.