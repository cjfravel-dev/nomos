# Nomos Example Project

This is a working example demonstrating how to use Nomos for code generation and validation.

## What This Example Demonstrates

1. **Template Definition** - JSON templates for User, Column, Limits, and Account models
2. **Code Generation** - Automatic case class generation via Maven plugin
3. **Serialization** - JSON to/from Scala objects using the generated dependency-free codecs
4. **Validation** - Runtime JSON validation against template schemas

## Project Structure

```text
nomos-example/
├── pom.xml                              # Maven configuration
├── src/main/resources/nomos/templates/
│   └── com/example/models/
│       ├── user.json                    # Generates com.example.models.user.User
│       ├── account/account.json         # Generates com.example.models.account.Account and Tier
│       ├── column/column.json           # Generates com.example.models.column.Column variants
│       └── limits/limits.json           # Generates com.example.models.limits.Limits
└── src/main/scala/com/example/
    ├── ExampleApp.scala                 # Demo application
    ├── TestRunner.scala                 # Serialization/validation checks (run via mvn test)
    └── ops/AccountOps.scala             # Hand-written accessors over generated models

# Generated code is written to target/ (not checked in):
target/generated-sources/nomos/com/example/models/
    ├── NomosFormats.scala
    ├── user/User.scala
    ├── account/Account.scala
    ├── account/Tier.scala
    ├── column/Column.scala
    └── limits/Limits.scala
```

## Running the Example

### Build and Generate Code

From `nomos-example/`:

```bash
mvn clean test
```

This will:
1. Scan `src/main/resources/nomos/templates` for `**/*.json`
2. Derive base packages from template paths, such as `com.example.models` for `com/example/models/user.json`
3. Generate case classes, companion methods (`fromJson`, `toJson`, `validate`), and `NomosFormats` into `target/generated-sources/nomos`
4. Compile all code and run the example's serialization and validation checks

### Run the Application

```bash
mvn exec:java
```

Expected output:

```text
=== Nomos Example Application ===

Created user:
  ID: user-123
  Username: johndoe
  ...

✓ Serialization round-trip successful!

=== Validation Examples ===

1. Validating valid JSON:
   ✓ Validation passed!

2. Validating JSON with missing required field:
   ✗ Validation failed (expected):
     - root: Missing required field 'username'

3. Validating JSON with wrong type:
   ✗ Validation failed (expected):
     - root.age: Type mismatch

4. Validating malformed JSON:
   ✗ Validation failed (expected):
     - root: Invalid JSON: ...
```

## Template Format

The real `src/main/resources/nomos/templates/com/example/models/user.json` template is:

```json
{
  "useOptionTypes": true,
  "listType": "List",
  "definitions": [
    {
      "name": "User",
      "subPackage": "user",
      "description": "User model with basic information",
      "template": {
        "id": "string",
        "username": "string",
        "email": "string",
        "age": {
          "$optional": "number"
        },
        "roles": ["string"]
      }
    }
  ]
}
```

The Maven plugin derives the base package from the template's path under `src/main/resources/nomos/templates`.

## Generated Code Usage

### Basic Serialization

```scala
import com.example.models.user.User

val user = User(
  id = "123",
  username = "john",
  email = "john@example.com",
  age = Some(30),
  roles = List("admin", "user")
)

val json = User.toJson(user)
val result = User.fromJson(json) // Either[String, User]
val validation = User.validate(json) // Either[List[ValidationError], User]
```

### With Nomos Validation API

```scala
import dev.cjfravel.nomos.Nomos
import scala.io.Source

val source = Source.fromResource("nomos/templates/com/example/models/user.json")
val templateJson = try source.mkString finally source.close()

val template = Nomos.parseTemplate(templateJson, "com.example.models").right.get

Nomos.validate(template, jsonData, "com.example.models.user.User") match {
  case Right(_) =>
    User.fromJson(jsonData)
  case Left(errors) =>
    errors.foreach(e => println(s"${e.path}: ${e.message}"))
}
```

You can also validate by simple definition name when it is unambiguous:

```scala
Nomos.validate(template, jsonData, "User")
```

## Maven Configuration

### Dependencies

The example uses Nomos BOM for dependency management:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.cjfravel</groupId>
            <artifactId>nomos-bom</artifactId>
            <version>0.0.1-alpha2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Plugin Configuration

Minimal plugin setup:

```xml
<plugin>
    <groupId>dev.cjfravel</groupId>
    <artifactId>nomos-maven-plugin</artifactId>
    <version>0.0.1-alpha2</version>
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

The plugin scans `src/main/resources/nomos/templates` by default. Override with `nomos.templateDirectory`, `nomos.includes`, `nomos.excludes`, or `nomos.outputDirectory` if needed.

## Key Features Demonstrated

- ✅ Template-based code generation
- ✅ Optional fields with `Option[T]`
- ✅ Array types with configurable collection types
- ✅ Dependency-free JSON serialization
- ✅ Runtime validation with detailed error messages
- ✅ Type-safe Scala case classes
- ✅ Compile-time safety with generated code

## Learn More

- [Documentation site](https://cjfravel-dev.github.io/nomos/)
- [Template Format](https://cjfravel-dev.github.io/nomos/users/template-format.html)
- [Architecture](https://cjfravel-dev.github.io/nomos/contributors/architecture.html)
