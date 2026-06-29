# Nomos Example Project

This is a working example demonstrating how to use Nomos for code generation and validation.

## What This Example Demonstrates

1. **Template Definition** - JSON template for a User model
2. **Code Generation** - Automatic case class generation via Maven plugin
3. **Serialization** - JSON to/from Scala objects using Jackson
4. **Validation** - Runtime JSON validation against template schema

## Project Structure

```
nomos-example/
├── pom.xml                              # Maven configuration
├── src/main/resources/templates/
│   └── user.json                        # Template definition
└── src/main/scala/com/example/
    ├── ExampleApp.scala                 # Demo application
    └── models/                          # Generated code (auto-generated)
        ├── NomosFormats.scala
        └── user/
            └── User.scala
```

## Running the Example

### Build and Generate Code

```bash
mvn clean compile
```

This will:
1. Generate `User` case class from the template
2. Generate serialization methods (`fromJson`, `toJson`)
3. Compile all code

### Run the Application

```bash
mvn exec:java
```

Expected output:
```
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

The `user.json` template defines the structure:

```json
{
  "basePackage": "com.example.models",
  "outputDir": "src/main/scala",
  "mainClass": "User",
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

## Generated Code Usage

### Basic Serialization

```scala
import com.example.models.user.User

// Create instance
val user = User(
  id = "123",
  username = "john",
  email = "john@example.com",
  age = Some(30),
  roles = List("admin", "user")
)

// Serialize to JSON
val json = User.toJson(user)

// Deserialize from JSON
val result = User.fromJson(json) // Either[String, User]
```

### With Validation

```scala
import dev.cjfravel.nomos.Nomos

// Load template
val template = Nomos.parseTemplate(templateJson).right.get

// Validate before parsing
Nomos.validate(template, jsonData, "User") match {
  case Right(_) => 
    // Valid - safe to parse
    User.fromJson(jsonData)
  case Left(errors) => 
    // Invalid - handle errors
    errors.foreach(e => println(s"${e.path}: ${e.message}"))
}
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
            <version>0.1.0-SNAPSHOT</version>
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

## Key Features Demonstrated

- ✅ Template-based code generation
- ✅ Optional fields with `Option[T]`
- ✅ Array types with configurable collection types
- ✅ Jackson-based JSON serialization
- ✅ Runtime validation with detailed error messages
- ✅ Type-safe Scala case classes
- ✅ Compile-time safety with generated code

## Learn More

- [Template Format Documentation](../docs/TEMPLATE_FORMAT.md)
- [Multi-Module Design](../docs/MULTI_MODULE_DESIGN.md)
- [More Examples](../docs/EXAMPLES.md)