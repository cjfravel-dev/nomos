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
  import dev.cjfravel.nomos.json._
  import dev.cjfravel.nomos.serialization.{Codecs, CodecRegistry}
  import dev.cjfravel.nomos.validation.ValidationError

  def decode(json: JsonValue): Either[String, Profile] = json match {
    case o: JsonObject =>
      for {
        firstName <- Codecs.required(o, "firstName", Codecs.string)
        lastName <- Codecs.required(o, "lastName", Codecs.string)
        age <- Codecs.required(o, "age", Codecs.double)
        bio <- Codecs.optional(o, "bio", Codecs.string)
      } yield Profile(firstName, lastName, age, bio)
    case other => Left("Profile: expected object, got " + other.typeName)
  }

  def encode(obj: Profile): JsonValue = JsonObject.fromFields(List(
    Some("firstName" -> JsonString(obj.firstName)),
    Some("lastName" -> JsonString(obj.lastName)),
    Some("age" -> JsonNumber.fromDouble(obj.age)),
    obj.bio.map(v => "bio" -> JsonString(v))
  ).flatten)

  def fromJson(json: String): Either[String, Profile] = Json.parse(json).right.flatMap(decode)

  def toJson(obj: Profile): String = Json.write(encode(obj))

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
  import dev.cjfravel.nomos.json._
  import dev.cjfravel.nomos.serialization.{Codecs, CodecRegistry}
  import dev.cjfravel.nomos.validation.ValidationError

  def decode(json: JsonValue): Either[String, Role] = json match {
    case o: JsonObject =>
      o.field("roleType") match {
        case Some(JsonString(d)) =>
          d match {
            case "admin" =>
              for {
                createdAt <- Codecs.required(o, "createdAt", Codecs.string)
                permissions <- Codecs.required(o, "permissions", Codecs.list(Codecs.string))
                department <- Codecs.required(o, "department", Codecs.string)
              } yield Admin(d, createdAt, permissions, department)
            case "member" =>
              for {
                createdAt <- Codecs.required(o, "createdAt", Codecs.string)
                subscriptionLevel <- Codecs.required(o, "subscriptionLevel", Codecs.string)
                subscriptionExpiry <- Codecs.optional(o, "subscriptionExpiry", Codecs.string)
              } yield Member(d, createdAt, subscriptionLevel, subscriptionExpiry)
            case "guest" =>
              for {
                createdAt <- Codecs.required(o, "createdAt", Codecs.string)
              } yield Guest(d, createdAt)
            case other => Left("Unknown roleType value: " + other)
          }
        case Some(_) => Left("roleType: expected string")
        case None => Left("missing required field 'roleType'")
      }
    case other => Left("Role: expected object, got " + other.typeName)
  }

  def encode(obj: Role): JsonValue = obj match {
    case v: Admin => JsonObject.fromFields(List(Some("roleType" -> JsonString(v.roleType)), Some("createdAt" -> JsonString(v.createdAt)), Some("permissions" -> JsonArray(v.permissions.iterator.map(x => JsonString(x)).toVector)), Some("department" -> JsonString(v.department))).flatten)
    case v: Member => JsonObject.fromFields(List(Some("roleType" -> JsonString(v.roleType)), Some("createdAt" -> JsonString(v.createdAt)), Some("subscriptionLevel" -> JsonString(v.subscriptionLevel)), v.subscriptionExpiry.map(x => "subscriptionExpiry" -> JsonString(x))).flatten)
    case v: Guest => JsonObject.fromFields(List(Some("roleType" -> JsonString(v.roleType)), Some("createdAt" -> JsonString(v.createdAt))).flatten)
  }

  def fromJson(json: String): Either[String, Role] = Json.parse(json).right.flatMap(decode)

  def toJson(obj: Role): String = Json.write(encode(obj))

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
  import dev.cjfravel.nomos.json._
  import dev.cjfravel.nomos.serialization.{Codecs, CodecRegistry}
  import dev.cjfravel.nomos.validation.ValidationError

  def decode(json: JsonValue): Either[String, User] = json match {
    case o: JsonObject =>
      for {
        id <- Codecs.required(o, "id", Codecs.string)
        username <- Codecs.required(o, "username", Codecs.string)
        email <- Codecs.required(o, "email", Codecs.string)
        profile <- Codecs.required(o, "profile", Profile.decode)
        role <- Codecs.required(o, "role", Role.decode)
      } yield User(id, username, email, profile, role)
    case other => Left("User: expected object, got " + other.typeName)
  }

  def encode(obj: User): JsonValue = JsonObject.fromFields(List(
    Some("id" -> JsonString(obj.id)),
    Some("username" -> JsonString(obj.username)),
    Some("email" -> JsonString(obj.email)),
    Some("profile" -> Profile.encode(obj.profile)),
    Some("role" -> Role.encode(obj.role))
  ).flatten)

  def fromJson(json: String): Either[String, User] = Json.parse(json).right.flatMap(decode)

  def toJson(obj: User): String = Json.write(encode(obj))

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
