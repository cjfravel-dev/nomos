# Nomos Multi-Module Design

## Overview

Nomos is organized as a multi-module Maven project using a **Bill of Materials (BOM)** pattern for dependency management. This architecture provides clean separation of concerns and consistent dependency versions across all modules.

## Module Structure

```text
nomos/
├── pom.xml                      # Parent POM
├── nomos-bom/                  # BOM for dependency management
│   └── pom.xml
├── nomos-core/                 # Build-time generator/parser
│   ├── pom.xml
│   └── src/
│       ├── main/scala/          # Template parsing & code generation
│       └── test/scala/          # Unit tests
├── nomos-runtime/              # Dependency-free runtime for generated code
│   ├── pom.xml
│   └── src/
│       └── main/scala/          # JSON model/parser/writer, validation, codecs
├── nomos-maven-plugin/         # Maven plugin for build integration
│   ├── pom.xml
│   └── src/
│       ├── main/java/           # Maven plugin implementation
│       └── test/java/           # Plugin tests
└── nomos-example/              # Example project
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── scala/           # Application code
        │   └── resources/
        │       └── templates/   # JSON template files
        └── test/scala/          # Tests
```

## Module Details

### 1. nomos-bom

**Purpose**: Bill of Materials for centralized dependency version management.

**Features**:
- Declares Scala library versions
- Declares Nomos module versions, including `nomos-runtime`
- Provides consistent versions across all projects

**Benefits**:
- Single source of truth for dependency versions
- Prevents version conflicts
- Users can override versions when needed
- Industry-standard Maven pattern

**Usage**:
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
```

### 2. nomos-core

**Purpose**: Build-time functionality for template processing and code generation.

**Components**:
- **Template Parsing**: [`TemplateParser`](../nomos-core/src/main/scala/dev/cjfravel/nomos/parser/TemplateParser.scala) - Parses JSON templates
- **Code Generation**: [`CodeGenerator`](../nomos-core/src/main/scala/dev/cjfravel/nomos/generation/CodeGenerator.scala) - Generates Scala case classes
- **Validation Wiring**: Generates calls to the first-party runtime validator
- **Models**: Uses the runtime `MultiTemplate`/`TemplateDefinition` and `TemplateType` data structures
- **File I/O**: [`FileWriter`](../nomos-core/src/main/scala/dev/cjfravel/nomos/generation/FileWriter.scala) - File writing utilities

**Dependencies** (versions managed by BOM):
- `scala-library`
- `nomos-runtime`

### 3. nomos-runtime

**Purpose**: First-party, zero-dependency runtime used by generated code and runtime validation.

**Components**:
- **JSON Model**: `dev.cjfravel.nomos.json.JsonValue`, `JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`, `JsonBoolean`, `JsonNull`
- **JSON Parser/Writer**: `Json.parse` and `Json.write`
- **Validation**: Runtime validation against embedded templates
- **Models and Codecs**: Shared model types and codec helpers for generated companions

**Dependencies**:
- `scala-library` only

### 4. nomos-maven-plugin

**Purpose**: Maven plugin for automatic code generation during builds.

**Goal**: `nomos:generate`
- **Default Phase**: `generate-sources`
- **Purpose**: Generate Scala case classes from JSON templates
- **Parameters**:
  - `templateDirectory`: Directory containing templates (default: `src/main/resources/nomos/templates`)
  - `includes`: File patterns to include (default: `**/*.json`)
  - `excludes`: File patterns to exclude (optional)
  - `outputDirectory`: Directory for generated Scala sources (default: `src/main/scala`)

**Design Philosophy**:
The base package is derived from each template file's path under the template directory
(e.g. `.../templates/com/example/models/user.json` → `com.example.models`); the output
directory is a plugin parameter. Generation behavior is controlled by optional top-level
keys in the template file:
- `useOptionTypes`: Whether to use `Option[T]` for optional fields (default: `true`)
- `listType`: Collection type for arrays — `List` or `Array` (default: `List`)
- `fromJsonStyle`: `either` (default) or `throwing`
- `dateType` / `dateTimeType` / `mapType`: override the generated date/date-time/map types

This provides:
- Multi-module support (paths relative to `${project.basedir}`)
- Package layout that mirrors the template directory structure
- Minimal plugin configuration
- Maximum flexibility

**Example Configuration**:
```xml
<plugin>
    <groupId>dev.cjfravel</groupId>
    <artifactId>nomos-maven-plugin</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <templateDirectory>src/main/resources/nomos/templates</templateDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Generated Code Structure

### Simple Case Class

```scala
package com.example.models.user

case class User(
  id: String,
  username: String,
  email: String,
  age: Option[Double],
  roles: List[String]
)

object User {
  import com.example.models.NomosFormats
  import NomosFormats._
  import dev.cjfravel.nomos.json._
  import dev.cjfravel.nomos.serialization.{Codecs, CodecRegistry}
  import dev.cjfravel.nomos.validation.ValidationError

  def decode(json: JsonValue): Either[String, User] = json match {
    case o: JsonObject =>
      for {
        id <- Codecs.required(o, "id", Codecs.string)
        username <- Codecs.required(o, "username", Codecs.string)
        email <- Codecs.required(o, "email", Codecs.string)
        age <- Codecs.optional(o, "age", Codecs.double)
        roles <- Codecs.required(o, "roles", Codecs.list(Codecs.string))
      } yield User(id, username, email, age, roles)
    case other => Left("User: expected object, got " + other.typeName)
  }

  def encode(obj: User): JsonValue = JsonObject.fromFields(List(
    Some("id" -> JsonString(obj.id)),
    Some("username" -> JsonString(obj.username)),
    Some("email" -> JsonString(obj.email)),
    obj.age.map(v => "age" -> JsonNumber.fromDouble(v)),
    Some("roles" -> JsonArray(obj.roles.iterator.map(x => JsonString(x)).toVector))
  ).flatten)

  def fromJson(json: String): Either[String, User] = Json.parse(json).right.flatMap(decode)

  def toJson(obj: User): String = Json.write(encode(obj))

  def validate(json: String): Either[List[ValidationError], User] = {
    validator.validate(json, "com.example.models.user.User") match {
      case Right(_) => fromJson(json).left.map(err => List(ValidationError("root", err, "valid JSON", json)))
      case Left(errors) => Left(errors)
    }
  }
}
```

### NomosFormats Object

```scala
package com.example.models

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.validation.MultiValidator
import scala.collection.immutable.ListMap

object NomosFormats {
  lazy val embeddedTemplate: MultiTemplate = {
    MultiTemplate(
      basePackage = "com.example.models",
      definitions = List(
        TemplateDefinition(
          name = "User",
          templateType = ObjectType(ListMap(
            "id" -> FieldDef(StringType(List()), optional = false),
            "username" -> FieldDef(StringType(List()), optional = false),
            "email" -> FieldDef(StringType(List()), optional = false),
            "age" -> FieldDef(NumberType(List()), optional = true),
            "roles" -> FieldDef(ArrayType(StringType(List()), List()), optional = false)
          ), ForbidExtra),
          subPackage = Some("user"),
          description = Some("User model with basic information"),
          validators = List(),
          methods = List()
        )
      ),
      useOptionTypes = true,
      listType = "List",
      fromJsonStyle = "either"
    )
  }

  lazy val validator: MultiValidator = new MultiValidator(embeddedTemplate)
}
```

### Discriminated Types (Sealed Traits)

```scala
package com.example.models.column

sealed trait Column

case class Decimal(
  `type`: String,
  scale: Int
) extends Column

case class Varchar(`type`: String) extends Column

object Column {
  import com.example.models.NomosFormats
  import NomosFormats._
  import dev.cjfravel.nomos.json._
  import dev.cjfravel.nomos.serialization.{Codecs, CodecRegistry}
  import dev.cjfravel.nomos.validation.ValidationError

  def decode(json: JsonValue): Either[String, Column] = json match {
    case o: JsonObject =>
      o.field("type") match {
        case Some(JsonString(d)) =>
          d match {
            case d2 if d2.startsWith("Decimal") =>
              for {
                scale <- Codecs.required(o, "scale", Codecs.int)
              } yield Decimal(d, scale)
            case d2 if d2.startsWith("Varchar") =>
              Right(Varchar(d))
            case other => Left("Unknown type value: " + other)
          }
        case Some(_) => Left("type: expected string")
        case None => Left("missing required field 'type'")
      }
    case other => Left("Column: expected object, got " + other.typeName)
  }

  def encode(obj: Column): JsonValue = obj match {
    case v: Decimal => JsonObject.fromFields(List(Some("type" -> JsonString(v.`type`)), Some("scale" -> JsonNumber.fromInt(v.scale))).flatten)
    case v: Varchar => JsonObject.fromFields(List(Some("type" -> JsonString(v.`type`))).flatten)
  }

  def fromJson(json: String): Either[String, Column] = Json.parse(json).right.flatMap(decode)

  def toJson(obj: Column): String = Json.write(encode(obj))

  def validate(json: String): Either[List[ValidationError], Column] = {
    validator.validate(json, "com.example.models.column.Column") match {
      case Right(_) => fromJson(json).left.map(err => List(ValidationError("root", err, "valid JSON", json)))
      case Left(errors) => Left(errors)
    }
  }
}
```

## Development Workflow

### 1. Project Setup

Add BOM and dependencies to `pom.xml`:

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
        <artifactId>nomos-runtime</artifactId>
    </dependency>
</dependencies>
```

### 2. Add Maven Plugin

```xml
<build>
    <plugins>
        <plugin>
            <groupId>dev.cjfravel</groupId>
            <artifactId>nomos-maven-plugin</artifactId>
            <version>0.1.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 3. Create Template

`src/main/resources/nomos/templates/com/example/models/user.json` (base package `com.example.models` is derived from the path):
```json
{
  "useOptionTypes": true,
  "listType": "List",
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

Generated files appear in the specified `outputDir`.

### 5. Use Generated Code

```scala
import com.example.models.user.User

// Parse JSON
val userResult = User.fromJson("""{"id":"123","username":"john",...}""")

// Serialize to JSON
val json = User.toJson(user)

// With validation
import dev.cjfravel.nomos.Nomos

val template = Nomos.parseTemplate(templateJson, "com.example").right.get
Nomos.validate(template, jsonData, "User") match {
  case Right(_) => User.fromJson(jsonData)
  case Left(errors) => // Handle errors
}
```

## Multi-Module Projects

The plugin works seamlessly in multi-module projects. Each module can have its own templates:

```text
my-project/
├── pom.xml              # Parent POM
├── core/
│   ├── pom.xml
│   └── src/main/resources/nomos/templates/
│       └── core-models.json
└── api/
    ├── pom.xml
    └── src/main/resources/nomos/templates/
        └── api-models.json
```

Each template specifies its configuration:
- All paths resolved relative to module's basedir
- Each module controls its own code generation
- No cross-module configuration needed

## Architecture Benefits

1. **Clean Dependencies**: Generated code uses the first-party zero-dependency JSON runtime
2. **Version Consistency**: BOM ensures compatible Nomos module versions
3. **Industry Standard**: Follows Maven best practices
4. **Simple Generated Code**: Explicit, readable generated codecs
5. **User Control**: Runtime and plugin versions are managed independently
6. **Maintainability**: Clear module boundaries

## Compatibility

- **Scala**: 2.12.x
- **Maven**: 3.6.0+
- **Java**: 8+ (for Maven plugin execution)
- **Runtime JSON**: First-party `nomos-runtime`, which depends only on `scala-library`

## Future Enhancements

- Gradle plugin
- SBT plugin
- CLI tool for standalone usage
- IDE plugins (IntelliJ, VSCode)
- Enhanced validation in IDEs
- Incremental code generation
- Multi-Scala-version support

## Summary

Nomos's multi-module design provides:
- ✅ Clean, maintainable architecture
- ✅ Standard Maven dependency management via BOM
- ✅ Simple, readable generated code
- ✅ Flexible template-based configuration
- ✅ Easy integration into existing projects
- ✅ Industry-standard patterns
