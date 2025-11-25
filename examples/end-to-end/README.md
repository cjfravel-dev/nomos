# End-to-End Example

This example demonstrates the complete workflow: parsing a JSON template and generating Scala code.

## Step 1: Define JSON Template

Create a file `user-template.json`:

```json
{
  "name": "User",
  "subPackage": "models.user",
  "description": "User account with profile information",
  "version": "1.0.0",
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
    "profile": {
      "firstName": "string",
      "lastName": "string",
      "age": {
        "type": "number",
        "min": 0,
        "max": 150
      },
      "bio": {
        "$optional": "string"
      }
    },
    "role": {
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
          "user": {
            "subscriptionLevel": "string",
            "subscriptionExpiry": {
              "$optional": "string"
            }
          },
          "guest": {}
        }
      }
    }
  }
}
```

## Step 2: Parse and Generate

```scala
import dev.cjfravel.nomos.parser.TemplateParser
import dev.cjfravel.nomos.generation.{CodeGenerator, GeneratorConfig, FileWriter}
import scala.io.Source

object UserModelGenerator {
  def main(args: Array[String]): Unit = {
    // Read template file
    val templateJson = Source.fromFile("user-template.json").mkString
    
    // Parse template
    val parser = new TemplateParser()
    val templateResult = parser.parseTemplate(templateJson)
    
    templateResult match {
      case Right(template) =>
        println(s"✓ Parsed template: ${template.name}")
        template.description.foreach(d => println(s"  Description: $d"))
        
        // Configure generator
        val config = GeneratorConfig(
          basePackage = "com.myapp",
          outputDir = "src/main/scala"
        )
        
        // Generate code
        val generator = new CodeGenerator(config)
        generator.generate(template) match {
          case Right(files) =>
            println(s"✓ Generated ${files.length} file(s)")
            
            // Write files
            val writer = new FileWriter()
            val report = writer.writeFilesWithReport(files, config.outputDirectory)
            
            println(s"\n${report.summary}")
            report.successPaths.foreach { path =>
              println(s"  ✓ $path")
            }
            
            if (report.isFailure) {
              println("\nErrors:")
              report.failureMessages.foreach { msg =>
                println(s"  ✗ $msg")
              }
            }
            
          case Left(error) =>
            println(s"✗ Generation error: ${error.message}")
        }
        
      case Left(error) =>
        println(s"✗ Parse error: ${error.message}")
    }
  }
}
```

## Step 3: Generated Output

The code will generate `src/main/scala/com/myapp/models/user/User.scala`:

```scala
package com.myapp.models.user

case class Profile(
  firstName: String,
  lastName: String,
  age: Double,
  bio: Option[String]
)

sealed trait Role

case class Admin(
  roleType: String,
  createdAt: String,
  permissions: List[String],
  department: String
) extends Role

case class User(
  roleType: String,
  createdAt: String,
  subscriptionLevel: String,
  subscriptionExpiry: Option[String]
) extends Role

case class Guest(
  roleType: String,
  createdAt: String
) extends Role

case class User(
  id: String,
  username: String,
  email: String,
  profile: Profile,
  role: Role
)
```

## Step 4: Use Generated Code

```scala
import com.myapp.models.user._

// Create an admin user
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

// Create a regular user
val userRole = User(
  roleType = "user",
  createdAt = "2024-01-15T00:00:00Z",
  subscriptionLevel = "premium",
  subscriptionExpiry = Some("2025-01-15")
)

val regularUser = User(
  id = "usr_002",
  username = "john_doe",
  email = "john@example.com",
  profile = Profile(
    firstName = "John",
    lastName = "Doe",
    age = 25,
    bio = None
  ),
  role = userRole
)

println(s"Admin: ${adminUser.username}")
println(s"User: ${regularUser.username}")
```

## Running the Example

```bash
# Compile the generator
mvn compile

# Run code generation
scala -cp target/classes UserModelGenerator

# Compile generated code
mvn compile

# Use in your application
```

## Key Features Demonstrated

1. ✅ **JSON Template Parsing** - Parse complex template with metadata
2. ✅ **Nested Objects** - Profile object nested within User
3. ✅ **Type Discriminator** - Role as sealed trait with variants
4. ✅ **Common Fields** - createdAt shared across all roles
5. ✅ **Optional Fields** - bio and subscriptionExpiry
6. ✅ **Constraints** - Username pattern, age range, email format
7. ✅ **Arrays** - List of permissions
8. ✅ **Package Structure** - Organized under com.myapp.models.user
9. ✅ **File Writing** - Automatic directory creation and file output
10. ✅ **Error Reporting** - Comprehensive error messages