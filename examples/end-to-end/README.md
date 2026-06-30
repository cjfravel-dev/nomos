# End-to-End Example

This example demonstrates the complete workflow: defining a JSON template, generating Scala code, and using the generated companion objects.

## Step 1: Define JSON Template

Create `src/main/resources/nomos/templates/com/myapp/models/user/user.json`. The Maven plugin derives the base package `com.myapp.models.user` from the template path.

```json
{
  "useOptionTypes": true,
  "listType": "List",
  "definitions": [
    {
      "name": "Profile",
      "template": {
        "firstName": "string",
        "lastName": "string",
        "age": {
          "type": "number",
          "min": 0,
          "max": 150
        },
        "bio": { "$optional": "string" }
      }
    },
    {
      "name": "Role",
      "template": {
        "$type": {
          "discriminator": "roleType",
          "includeDiscriminator": true,
          "commonFields": {
            "createdAt": "string"
          },
          "variants": {
            "admin": {
              "permissions": ["string"],
              "department": "string"
            },
            "member": {
              "subscriptionLevel": "string",
              "subscriptionExpiry": { "$optional": "string" }
            },
            "guest": {}
          }
        }
      }
    },
    {
      "name": "User",
      "template": {
        "id": "string",
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
        "profile": "$ref:Profile",
        "role": "$ref:Role"
      }
    }
  ]
}
```

## Step 2: Parse and Generate

### Maven plugin flow

The plugin scans `src/main/resources/nomos/templates` for `**/*.json`, derives each base package from the file path, and generates during `generate-sources`.

```bash
mvn generate-sources
```

The plugin can be configured with `nomos.templateDirectory`, `nomos.includes`, `nomos.excludes`, and `nomos.outputDirectory`.

### Programmatic flow

```scala
import dev.cjfravel.nomos.Nomos
import scala.io.Source

object UserModelGenerator {
  def main(args: Array[String]): Unit = {
    val path = "src/main/resources/nomos/templates/com/myapp/models/user/user.json"
    val source = Source.fromFile(path)
    val templateJson = try source.mkString finally source.close()

    val result = for {
      template <- Nomos.parseTemplate(templateJson, "com.myapp.models.user")
      report <- Nomos.generateCode(template, "src/main/scala")
    } yield report

    result match {
      case Right(report) =>
        println(report.summary)
        report.successPaths.foreach(path => println(s"  ✓ $path"))
      case Left(error) =>
        println(s"✗ ${error.message}")
    }
  }
}
```

## Step 3: Generated Output

The template generates `Profile.scala`, `Role.scala`, `User.scala`, and a shared `NomosFormats.scala` under `src/main/scala/com/myapp/models/user`.

```scala
package com.myapp.models.user

case class Profile(
  firstName: String,
  lastName: String,
  age: Double,
  bio: Option[String]
)

object Profile {
  import NomosFormats._
  import dev.cjfravel.nomos.validation.ValidationError

  def fromJson(json: String): Either[String, Profile] = {
    try {
      Right(mapper.readValue(json, classOf[Profile]))
    } catch {
      case e: Exception => Left(s"Failed to parse JSON: ${e.getMessage}")
    }
  }

  def toJson(obj: Profile): String = mapper.writeValueAsString(obj)

  def validate(json: String): Either[List[ValidationError], Profile] = {
    validator.validate(json, "com.myapp.models.user.Profile") match {
      case Right(_) => fromJson(json).left.map(err => List(ValidationError("root", err, "valid JSON", json)))
      case Left(errors) => Left(errors)
    }
  }
}

sealed trait Role

case class Admin(
  roleType: String,
  createdAt: String,
  permissions: List[String],
  department: String
) extends Role

case class Member(
  roleType: String,
  createdAt: String,
  subscriptionLevel: String,
  subscriptionExpiry: Option[String]
) extends Role

case class Guest(
  roleType: String,
  createdAt: String
) extends Role

object Role {
  import NomosFormats._
  import dev.cjfravel.nomos.validation.ValidationError
  import com.fasterxml.jackson.databind.JsonNode

  def fromJson(json: String): Either[String, Role] = {
    try {
      val jsonNode = mapper.readTree(json)
      val discriminatorValue = jsonNode.get("roleType").asText()
      discriminatorValue match {
        case "admin" => Right(mapper.treeToValue(jsonNode, classOf[Admin]))
        case "member" => Right(mapper.treeToValue(jsonNode, classOf[Member]))
        case "guest" => Right(mapper.treeToValue(jsonNode, classOf[Guest]))
        case other => Left(s"Unknown roleType value: $other")
      }
    } catch {
      case e: Exception => Left(s"Failed to parse JSON: ${e.getMessage}")
    }
  }

  def toJson(obj: Role): String = mapper.writeValueAsString(obj)

  def validate(json: String): Either[List[ValidationError], Role] = {
    validator.validate(json, "com.myapp.models.user.Role") match {
      case Right(_) => fromJson(json).left.map(err => List(ValidationError("root", err, "valid JSON", json)))
      case Left(errors) => Left(errors)
    }
  }
}

case class User(
  id: String,
  username: String,
  email: String,
  profile: Profile,
  role: Role
)

object User {
  import NomosFormats._
  import dev.cjfravel.nomos.validation.ValidationError

  def fromJson(json: String): Either[String, User] = {
    try {
      Right(mapper.readValue(json, classOf[User]))
    } catch {
      case e: Exception => Left(s"Failed to parse JSON: ${e.getMessage}")
    }
  }

  def toJson(obj: User): String = mapper.writeValueAsString(obj)

  def validate(json: String): Either[List[ValidationError], User] = {
    validator.validate(json, "com.myapp.models.user.User") match {
      case Right(_) => fromJson(json).left.map(err => List(ValidationError("root", err, "valid JSON", json)))
      case Left(errors) => Left(errors)
    }
  }
}
```

Each generated companion contains concrete `fromJson`, `toJson`, and `validate` methods. `fromJson` returns `Either[String, T]` by default; set top-level `"fromJsonStyle": "throwing"` to generate throwing parsers instead.

## Step 4: Use Generated Code

```scala
import com.myapp.models.user._

val adminRole = Admin(
  roleType = "admin",
  createdAt = "2024-01-01T00:00:00Z",
  permissions = List("read", "write", "delete"),
  department = "Engineering"
)

val adminUser = User(
  id = "usr_001",
  username = "admin_user",
  email = "admin@example.com",
  profile = Profile(
    firstName = "Admin",
    lastName = "User",
    age = 30,
    bio = Some("System administrator")
  ),
  role = adminRole
)

val json = User.toJson(adminUser)
val parsed = User.fromJson(json)
val validated = User.validate(json)
```

## Running the Example

```bash
# Generate sources and compile
mvn clean compile

# Or only run generation
mvn generate-sources
```

## Key Features Demonstrated

1. ✅ **JSON Template Parsing** - Parse multi-definition templates with `definitions`
2. ✅ **References** - Use `$ref:Profile` and `$ref:Role` between definitions
3. ✅ **Type Discriminator** - `Role` as sealed trait with variants
4. ✅ **Common Fields** - `createdAt` shared across all roles
5. ✅ **Optional Fields** - `bio` and `subscriptionExpiry`
6. ✅ **Constraints** - Username pattern, age range, email format
7. ✅ **Arrays** - List of permissions
8. ✅ **Package Structure** - Base package derived from template path
9. ✅ **File Writing** - `Nomos.generateCode(template, outputDir)` writes generated files
10. ✅ **Error Reporting** - `Either` results for parsing, generation, and validation
