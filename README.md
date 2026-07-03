# Nomos

A Scala library for generating case classes and JSON validators from declarative templates.

## Features

- **Code Generation**: Generate Scala case classes from JSON templates
- **Type Safety**: Dependency-free serialization with generated codecs (no third-party JSON library)
- **Validation**: Runtime JSON validation against template schemas
- **Type Discriminators**: Support for sealed traits with variants
- **Maven Integration**: Maven plugin for build-time code generation
- **BOM Support**: Centralized dependency management

## Quick Start

### 1. Add Dependencies

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.cjfravel</groupId>
            <artifactId>nomos-bom</artifactId>
            <version>0.1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>dev.cjfravel</groupId>
        <artifactId>nomos-core</artifactId>
    </dependency>
</dependencies>
```

### 2. Add Maven Plugin

```xml
<plugin>
    <groupId>dev.cjfravel</groupId>
    <artifactId>nomos-maven-plugin</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 3. Create Template

`src/main/resources/nomos/templates/com/example/models/user.json` (the base package `com.example.models` is derived from the path):
```json
{
  "definitions": [
    {
      "name": "User",
      "subPackage": "user",
      "template": {
        "id": "string",
        "username": "string",
        "email": "string",
        "age": { "$optional": "number" },
        "roles": ["string"]
      }
    }
  ]
}
```

### 4. Generate Code

```bash
mvn generate-sources
```

This generates:
```scala
case class User(
  email: String,
  username: String,
  age: Option[Double],
  id: String,
  roles: List[String]
)

object User {
  def fromJson(json: String): Either[String, User] = { ... }
  def toJson(obj: User): String = { ... }
}
```

### 5. Use Generated Code

```scala
import com.example.models.user.User

// Parse JSON (returns Either[String, User] by default)
val parsed = User.fromJson("""{"id":"123","username":"john","email":"j@x.com","roles":[]}""")

// Or validate against the embedded template and parse in one step
User.validate("""{"id":"123","username":"john","email":"j@x.com","roles":[]}""") match {
  case Right(user)  => println(user)
  case Left(errors) => errors.foreach(println) // Handle validation errors
}
```

## Documentation

- [Template Format](docs/TEMPLATE_FORMAT.md) - JSON template syntax
- [Multi-Module Design](docs/MULTI_MODULE_DESIGN.md) - Project architecture
- [Examples](docs/EXAMPLES.md) - Usage examples
- [Template Embedding](docs/TEMPLATE_EMBEDDING.md) - Runtime validation from the embedded template

## Runtime JSON API

`nomos-runtime` is dependency-free: generated code, validation, and your own runtime code use a
first-party JSON model in `dev.cjfravel.nomos.json` instead of a third-party library. This model
is a **public, supported, semver-stable** part of `nomos-runtime` — safe to depend on directly
from hand-written runtime code:

```scala
import dev.cjfravel.nomos.json._

Json.parse("""{"a":1,"b":2}""") match {
  case Right(obj: JsonObject) =>
    val renamed = obj.mapKeys(_.toUpperCase).updated("c", JsonNumber.fromInt(3))
    Json.write(renamed)            // {"A":1,"B":2,"c":3}
  case Right(other) => other.typeName
  case Left(error)  => error
}
```

It is intentionally minimal — it provides only what generated codecs, validation, and
straightforward hand-written runtime code need, and it is **not** a general-purpose JSON library.

**In scope:**

- an immutable JSON tree (`JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`, `JsonBoolean`,
  `JsonNull`); objects preserve key order and compare order-independently;
- `Json.parse` and compact/pretty `Json.write` (whole-document, in-memory);
- `Option`-returning type accessors (`asString`, `asInt`, `asObject`, ...) and exact numbers
  (the original lexeme is preserved, so `30.0` round-trips);
- shallow, single-level lookups (`JsonObject.field`, `JsonArray.get`) and immutable single-level
  transforms (`updated`, `remove`, `mapKeys`).

**Out of scope (intentionally excluded; requests for these are declined):**

- path/query languages (JSON Pointer, JSONPath) or deep navigation helpers;
- streaming / incremental parsing — the API is whole-document;
- schema languages, diff/patch, canonicalization;
- reflection/macro mapping to arbitrary case classes (that is what generated codecs are for);
- mutable builders/in-place mutation, lenses/optics, comments/JSON5.

If you need any of the above, layer your own library on top of this model — nomos will not grow
into a general-purpose JSON toolkit.

## Project Structure

```
nomos/
├── nomos-bom/          # Dependency management BOM
├── nomos-runtime/      # Zero-dependency runtime: JSON model, validation, codecs
├── nomos-core/         # Build-time library (parser, generator)
├── nomos-maven-plugin/ # Maven plugin
└── nomos-example/      # Example project
```

## Building

```bash
mvn clean install
```

## Testing

```bash
mvn test
```

## License

TBD