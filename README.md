# Chisel

A Scala library for generating case classes and JSON validators from declarative templates.

## Features

- **Code Generation**: Generate Scala case classes from JSON templates
- **Type Safety**: Automatic serialization/deserialization with Jackson
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

`src/main/resources/templates/user.json`:
```json
{
  "basePackage": "com.example.models",
  "outputDir": "src/main/scala",
  "mainClass": "User",
  "definitions": [
    {
      "name": "User",
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
import dev.cjfravel.nomos.Chisel

// Parse JSON
val user = User.fromJson("""{"id":"123","username":"john",...}""")

// Validate before parsing
val template = Chisel.parseTemplate(templateJson).right.get
Chisel.validate(template, jsonData, "User") match {
  case Right(_) => User.fromJson(jsonData)
  case Left(errors) => // Handle validation errors
}
```

## Documentation

- [Template Format](docs/TEMPLATE_FORMAT.md) - JSON template syntax
- [Multi-Module Design](docs/MULTI_MODULE_DESIGN.md) - Project architecture
- [Examples](docs/EXAMPLES.md) - Usage examples
- [Implementation Plan](docs/IMPLEMENTATION_PLAN.md) - Design decisions

## Project Structure

```
nomos/
├── nomos-bom/          # Dependency management BOM
├── nomos-core/         # Core library (parser, validator, generator)
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