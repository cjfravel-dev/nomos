package com.example

import com.example.models.user.User

object TestRunner {
  def main(args: Array[String]): Unit = {
    println("=== Testing Serialization/Deserialization ===\n")
    
    // Test 1: Create a User instance
    val user = User(
      email = "test@example.com",
      username = "testuser",
      age = Some(30.0),
      id = "user123",
      roles = List("admin", "user")
    )
    
    println(s"Original User: $user\n")
    
    // Test 2: Serialize to JSON
    val json = User.toJson(user)
    println(s"Serialized JSON:\n$json\n")
    
    // Test 3: Deserialize back to User
    User.fromJson(json) match {
      case Right(deserializedUser) =>
        println(s"Deserialized User: $deserializedUser\n")
        println("✅ Serialization and deserialization successful!")
        
        // Verify fields match
        if (deserializedUser == user) {
          println("✅ All fields match!")
        } else {
          println("❌ Fields don't match!")
          println(s"Expected: $user")
          println(s"Got: $deserializedUser")
        }
        
      case Left(error) =>
        println(s"❌ Deserialization failed: $error")
        System.exit(1)
    }
    
    // Test 4: Test with minimal JSON (no age)
    println("\n=== Testing with minimal JSON (no age) ===\n")
    val minimalJson = """{"email":"min@example.com","username":"minuser","id":"user456","roles":["user"]}"""
    println(s"Minimal JSON:\n$minimalJson\n")
    
    User.fromJson(minimalJson) match {
      case Right(minUser) =>
        println(s"Deserialized minimal User: $minUser")
        println(s"Age field: ${minUser.age}")
        println("✅ Optional field handling works!")
        
      case Left(error) =>
        println(s"❌ Minimal JSON deserialization failed: $error")
        System.exit(1)
    }
  }
}