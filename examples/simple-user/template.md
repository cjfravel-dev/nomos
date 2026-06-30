# Simple User Example

This example demonstrates basic code generation with Nomos.

## Template

Place the template at `src/main/resources/nomos/templates/com/example/examples/models/user.json`. The Maven plugin derives the base package `com.example.examples.models` from that path; the `subPackage` makes the generated class `com.example.examples.models.user.User`.

```json
{
  "useOptionTypes": true,
  "listType": "List",
  "definitions": [
    {
      "name": "User",
      "subPackage": "user",
      "description": "User model with basic fields",
      "template": {
        "id": "string",
        "username": "string",
        "email": "string",
        "age": { "$optional": "number" },
        "bio": { "$optional": "string" }
      }
    }
  ]
}
```

## Running the Example

With the Maven plugin configured, generate sources during the standard Maven lifecycle:

```bash
mvn generate-sources
```

Programmatic generation uses the public `Nomos` entry point and an explicit base package:

```scala
import dev.cjfravel.nomos.Nomos
import scala.io.Source

val source = Source.fromFile("src/main/resources/nomos/templates/com/example/examples/models/user.json")
val templateJson = try source.mkString finally source.close()

val result = for {
  template <- Nomos.parseTemplate(templateJson, "com.example.examples.models")
  report <- Nomos.generateCode(template, "target/generated-sources")
} yield report

result match {
  case Right(report) => report.successPaths.foreach(path => println(s"Generated: $path"))
  case Left(error) => println(s"Error: ${error.message}")
}
```

## Generated Code

The template generates `target/generated-sources/com/example/examples/models/user/User.scala` plus a shared `NomosFormats` object in `com.example.examples.models`:

```scala
package com.example.examples.models.user

case class User(
  id: String,
  username: String,
  email: String,
  age: Option[Double],
  bio: Option[String]
)

object User {
  import com.example.examples.models.NomosFormats
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
    validator.validate(json, "com.example.examples.models.user.User") match {
      case Right(_) => fromJson(json).left.map(err => List(ValidationError("root", err, "valid JSON", json)))
      case Left(errors) => Left(errors)
    }
  }
}
```

## Usage Example

```scala
import com.example.examples.models.user.User

val user = User(
  id = "123",
  username = "jdoe",
  email = "jdoe@example.com",
  age = Some(30),
  bio = None
)

val json = User.toJson(user)
val parsed = User.fromJson(json)
val validated = User.validate(json)
```
