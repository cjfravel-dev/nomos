package com.example

import com.example.models.user.User
import dev.cjfravel.chisel.Chisel
import scala.io.Source

/**
 * Example application demonstrating Chisel-generated code usage with Jackson and validation.
 */
object ExampleApp extends App {
  
  println("=== Chisel Example Application ===\n")
  
  // Create a User instance
  val user = User(
    id = "user-123",
    username = "johndoe",
    email = "john@example.com",
    age = Some(30),
    roles = List("admin", "user")
  )
  
  println("Created user:")
  println(s"  ID: ${user.id}")
  println(s"  Username: ${user.username}")
  println(s"  Email: ${user.email}")
  println(s"  Age: ${user.age.getOrElse("Not specified")}")
  println(s"  Roles: ${user.roles.mkString(", ")}")
  println()
  
  // Serialize to JSON using generated toJson method
  val jsonString = User.toJson(user)
  
  println("Serialized to JSON:")
  println(jsonString)
  println()
  
  // Deserialize from JSON using generated fromJson method
  User.fromJson(jsonString) match {
    case Right(deserializedUser) =>
      println("Deserialized from JSON:")
      println(s"  ID: ${deserializedUser.id}")
      println(s"  Username: ${deserializedUser.username}")
      println(s"  Email: ${deserializedUser.email}")
      println(s"  Age: ${deserializedUser.age.getOrElse("Not specified")}")
      println(s"  Roles: ${deserializedUser.roles.mkString(", ")}")
      println()
      
      // Verify they match
      if (user == deserializedUser) {
        println("✓ Serialization round-trip successful!")
      } else {
        println("✗ Serialization round-trip failed!")
      }
      
    case Left(error) =>
      println(s"✗ Failed to deserialize: $error")
  }
  
  // Demonstrate validation
  println("\n=== Validation Examples ===\n")
  
  // Load template for validation
  val templateSource = Source.fromResource("templates/user.json")
  val templateJson = try templateSource.mkString finally templateSource.close()
  
  val multiTemplate = Chisel.parseTemplate(templateJson) match {
    case Right(template) => template
    case Left(error) =>
      println(s"Failed to parse template: ${error.message}")
      sys.exit(1)
  }
  
  // Example 1: Valid JSON
  println("1. Validating valid JSON:")
  val validJson = """{"id":"user-456","username":"janedoe","email":"jane@example.com","roles":["user"]}"""
  println(s"   Input: $validJson")
  
  Chisel.validate(multiTemplate, validJson, "User") match {
    case Right(_) =>
      println("   ✓ Validation passed!")
      User.fromJson(validJson) match {
        case Right(u) => println(s"   Successfully parsed: ${u.username}")
        case Left(err) => println(s"   Parse error: $err")
      }
    case Left(errors) =>
      println(s"   ✗ Validation failed:")
      errors.foreach(e => println(s"     - ${e.path}: ${e.message}"))
  }
  
  println()
  
  // Example 2: Missing required field
  println("2. Validating JSON with missing required field:")
  val missingFieldJson = """{"id":"user-789","email":"bob@example.com","roles":[]}"""
  println(s"   Input: $missingFieldJson")
  
  Chisel.validate(multiTemplate, missingFieldJson, "User") match {
    case Right(_) => println("   ✓ Validation passed!")
    case Left(errors) =>
      println(s"   ✗ Validation failed (expected):")
      errors.foreach(e => println(s"     - ${e.path}: ${e.message}"))
  }
  
  println()
  
  // Example 3: Wrong type
  println("3. Validating JSON with wrong type:")
  val wrongTypeJson = """{"id":"user-999","username":"alice","email":"alice@example.com","age":"thirty","roles":["admin"]}"""
  println(s"   Input: $wrongTypeJson")
  
  Chisel.validate(multiTemplate, wrongTypeJson, "User") match {
    case Right(_) => println("   ✓ Validation passed!")
    case Left(errors) =>
      println(s"   ✗ Validation failed (expected):")
      errors.foreach(e => println(s"     - ${e.path}: ${e.message}"))
  }
  
  println()
  
  // Example 4: Invalid JSON syntax
  println("4. Validating malformed JSON:")
  val malformedJson = """{"id":"user-000","username":"charlie",email:"bad"}"""
  println(s"   Input: $malformedJson")
  
  Chisel.validate(multiTemplate, malformedJson, "User") match {
    case Right(_) => println("   ✓ Validation passed!")
    case Left(errors) =>
      println(s"   ✗ Validation failed (expected):")
      errors.foreach(e => println(s"     - ${e.path}: ${e.message}"))
  }
  
  println("\n=== Example Complete ===")
}