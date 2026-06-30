# Nomos Multi-Module Design

## Overview

Nomos is organized as a multi-module Maven project using a **Bill of Materials (BOM)** pattern for dependency management. This architecture provides clean separation of concerns and consistent dependency versions across all modules.

## Module Structure

```
nomos/
├── pom.xml                      # Parent POM
├── nomos-bom/                  # BOM for dependency management
│   └── pom.xml
├── nomos-core/                 # Core library
│   ├── pom.xml
│   └── src/
│       ├── main/scala/          # Template parsing & code generation
│       └── test/scala/          # Unit tests
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
- Declares Jackson 2.15.2 versions
- Declares Scala library versions
- Declares Nomos module versions
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

**Purpose**: Core functionality for template processing and code generation.

**Components**:
- **Template Parsing**: [`TemplateParser`](../nomos-core/src/main/scala/dev/cjfravel/nomos/parser/TemplateParser.scala) - Parses JSON templates
- **Code Generation**: [`CodeGenerator`](../nomos-core/src/main/scala/dev/cjfravel/nomos/generation/CodeGenerator.scala) - Generates Scala case classes
- **Validation**: [`Validator`](../nomos-core/src/main/scala/dev/cjfravel/nomos/validation/Validator.scala) - Runtime JSON validation
- **Models**: [`MultiTemplate`/`TemplateDefinition`](../nomos-core/src/main/scala/dev/cjfravel/nomos/model/Template.scala), [`TemplateType`](../nomos-core/src/main/scala/dev/cjfravel/nomos/model/TemplateType.scala) - Core data structures
- **File I/O**: [`FileWriter`](../nomos-core/src/main/scala/dev/cjfravel/nomos/generation/FileWriter.scala) - File writing utilities

**Dependencies** (versions managed by BOM):
- `scala-library`
- `jackson-databind`
- `jackson-module-scala`

### 3. nomos-maven-plugin

**Purpose**: Maven plugin for automatic code generation during builds.

**Goal**: `nomos:generate`
- **Default Phase**: `generate-sources`
- **Purpose**: Generate Scala case classes from JSON templates
- **Parameters**:
  - `templateDirectory`: Directory containing templates (default: `src/main/resources/nomos/templates`)
  - `includes`: File patterns to include (default: `**/*.json`)
  - `excludes`: File patterns to exclude (optional)

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
  email: String,
  username: String,
  age: Option[Double],
  id: String,
  roles: List[String]
)

object User {
  import com.example.models.NomosFormats
  import NomosFormats._

  def fromJson(json: String): Either[String, User] = {
    try {
      Right(mapper.readValue(json, classOf[User]))
    } catch {
      case e: Exception => Left(s"Failed to parse JSON: ${e.getMessage}")
    }
  }

  def toJson(obj: User): String = {
    mapper.writeValueAsString(obj)
  }
}
```

### NomosFormats Object

```scala
package com.example.models

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object NomosFormats {
  val mapper: ObjectMapper = {
    val m = new ObjectMapper()
    m.registerModule(DefaultScalaModule)
    m
  }
}
```

### Discriminated Types (Sealed Traits)

```scala
sealed trait Shape {
  def shapeType: String
}

case class Circle(
  shapeType: String,
  radius: Double
) extends Shape

case class Rectangle(
  shapeType: String,
  width: Double,
  height: Double
) extends Shape

object Shape {
  import com.example.models.NomosFormats
  import NomosFormats._
  import com.fasterxml.jackson.databind.JsonNode

  def fromJson(json: String): Either[String, Shape] = {
    try {
      val jsonNode = mapper.readTree(json)
      val discriminatorValue = jsonNode.get("shapeType").asText()
      discriminatorValue match {
        case "circle" => Right(mapper.treeToValue(jsonNode, classOf[Circle]))
        case "rectangle" => Right(mapper.treeToValue(jsonNode, classOf[Rectangle]))
        case other => Left(s"Unknown shapeType value: $other")
      }
    } catch {
      case e: Exception => Left(s"Failed to parse JSON: ${e.getMessage}")
    }
  }

  def toJson(obj: Shape): String = {
    mapper.writeValueAsString(obj)
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
        <groupId>org.scala-lang</groupId>
        <artifactId>scala-library</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.module</groupId>
        <artifactId>jackson-module-scala_2.12</artifactId>
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

```
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

1. **Clean Dependencies**: Standard Jackson with Scala module support
2. **Version Consistency**: BOM ensures compatible versions
3. **Industry Standard**: Follows Maven best practices
4. **Simple Generated Code**: Clean, readable Jackson calls
5. **User Control**: Version overrides possible when needed
6. **Maintainability**: Clear module boundaries

## Compatibility

- **Scala**: 2.12.x (2.13.x support via appropriate jackson-module-scala)
- **Maven**: 3.6.0+
- **Java**: 8+ (for Maven plugin execution)
- **Jackson**: 2.15.2+ (managed by BOM, overridable)

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