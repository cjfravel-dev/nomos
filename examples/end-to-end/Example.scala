package examples

import dev.cjfravel.chisel.Chisel

/**
 * End-to-end example demonstrating the complete Chisel workflow:
 * 1. Define a JSON template
 * 2. Parse the template
 * 3. Generate Scala case classes
 * 4. Validate JSON data against the template
 */
object EndToEndExample {
  
  def main(args: Array[String]): Unit = {
    println("=== Chisel End-to-End Example ===\n")
    
    // Step 1: Define a JSON template for a User type
    val templateJson = """
    {
      "name": "User",
      "subPackage": "models",
      "description": "A user in the system",
      "version": "1.0.0",
      "template": {
        "id": {
          "type": "string",
          "format": "uuid"
        },
        "name": {
          "type": "string",
          "minLength": 1,
          "maxLength": 100
        },
        "email": {
          "type": "string",
          "format": "email"
        },
        "age": {
          "$optional": {
            "type": "number",
            "min": 0,
            "max": 150
          }
        },
        "tags": [
          "string"
        ]
      }
    }
    """
    
    println("Step 1: Template Definition")
    println("----------------------------")
    println(templateJson)
    println()
    
    // Step 2: Parse the template
    println("Step 2: Parse Template")
    println("----------------------")
    val templateResult = Chisel.parseTemplate(templateJson)
    
    templateResult match {
      case Left(error) =>
        println(s"ERROR: Failed to parse template: ${error.message}")
        return
      case Right(template) =>
        println(s"✓ Successfully parsed template: ${template.name}")
        println(s"  Package: ${template.fullPackage("com.example")}")
        println(s"  Description: ${template.description.getOrElse("N/A")}")
        println(s"  Version: ${template.version.getOrElse("N/A")}")
        println()
    }
    
    val template = templateResult.right.get
    
    // Step 3: Generate Scala case classes
    println("Step 3: Generate Code")
    println("---------------------")
    val codeResult = Chisel.generateCode(
      template,
      basePackage = "com.example",
      outputDir = "examples/end-to-end/generated"
    )
    
    codeResult match {
      case Left(error) =>
        println(s"ERROR: Failed to generate code: ${error.message}")
        return
      case Right(report) =>
        println(s"✓ Code generation completed")
        println(s"  Files written: ${report.successCount}")
        println(s"  Failures: ${report.failureCount}")
        if (report.successCount > 0) {
          println("  Generated files:")
          report.successPaths.foreach(path => println(s"    - $path"))
        }
        if (report.failureCount > 0) {
          println("  Errors:")
          report.failureMessages.foreach(msg => println(s"    - $msg"))
        }
        println()
    }
    
    // Step 4: Validate JSON data
    println("Step 4: Validate JSON Data")
    println("--------------------------")
    
    // Valid JSON
    val validJson = """
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "John Doe",
      "email": "john@example.com",
      "age": 30,
      "tags": ["developer", "scala"]
    }
    """
    
    println("Testing valid JSON:")
    val validResult = Chisel.validate(template, validJson)
    validResult match {
      case Left(errors) =>
        println(s"  ✗ Validation failed with ${errors.length} error(s):")
        errors.foreach(err => println(s"    - ${err.path}: ${err.message}"))
      case Right(_) =>
        println("  ✓ Validation passed")
    }
    println()
    
    // Invalid JSON - missing required field
    val invalidJson1 = """
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "john@example.com",
      "tags": ["developer"]
    }
    """
    
    println("Testing invalid JSON (missing required 'name' field):")
    val invalidResult1 = Chisel.validate(template, invalidJson1)
    invalidResult1 match {
      case Left(errors) =>
        println(s"  ✗ Validation failed with ${errors.length} error(s):")
        errors.foreach(err => println(s"    - ${err.path}: ${err.message}"))
      case Right(_) =>
        println("  ✓ Validation passed")
    }
    println()
    
    // Invalid JSON - constraint violation
    val invalidJson2 = """
    {
      "id": "not-a-uuid",
      "name": "",
      "email": "invalid-email",
      "age": 200,
      "tags": ["developer"]
    }
    """
    
    println("Testing invalid JSON (multiple constraint violations):")
    val invalidResult2 = Chisel.validate(template, invalidJson2)
    invalidResult2 match {
      case Left(errors) =>
        println(s"  ✗ Validation failed with ${errors.length} error(s):")
        errors.foreach(err => println(s"    - ${err.path}: ${err.message}"))
      case Right(_) =>
        println("  ✓ Validation passed")
    }
    println()
    
    // Step 5: Using the process() convenience method
    println("Step 5: Using Chisel.process() convenience method")
    println("--------------------------------------------------")
    val processResult = Chisel.process(
      templateJson,
      basePackage = "com.example.alternate",
      outputDir = "examples/end-to-end/generated-alt"
    )
    
    processResult match {
      case Left(error) =>
        println(s"ERROR: Process failed: ${error.message}")
      case Right(result) =>
        println(s"✓ Process completed successfully")
        println(s"  Template: ${result.template.name}")
        println(s"  Files written: ${result.writeReport.successCount}")
        println(s"  Can validate: ${result.validator != null}")
        
        // Use the result's validator
        println("\n  Testing validator from result:")
        val testResult = result.validator.validate(validJson)
        testResult match {
          case Left(errors) =>
            println(s"    ✗ Validation failed")
          case Right(_) =>
            println(s"    ✓ Validation passed")
        }
    }
    println()
    
    println("=== Example Complete ===")
  }
}