package dev.cjfravel.nomos.generation

import java.io.File

/**
 * Configuration for code generation
 *
 * @param basePackage The base package for generated code (e.g., "com.myapp")
 * @param outputDir The directory where generated code will be written
 * @param useOptionTypes Whether to use Option[T] for optional fields (true) or nullable types (false). Default is true.
 * @param listType The collection type to use for arrays: "List" or "Array" (default: "List")
 */
case class GeneratorConfig(
  basePackage: String,
  outputDir: String,
  useOptionTypes: Boolean = true,
  listType: String = "List"
) {
  /**
   * Validates the configuration
   */
  def validate(): List[String] = {
    var errors = List.empty[String]
    
    if (basePackage.isEmpty) {
      errors = "Base package cannot be empty" :: errors
    }
    
    if (!basePackage.split('.').forall(part => 
      part.nonEmpty && part.head.isLower && part.forall(c => c.isLetterOrDigit || c == '_')
    )) {
      errors = s"Invalid base package format: $basePackage" :: errors
    }
    
    if (outputDir.isEmpty) {
      errors = "Output directory cannot be empty" :: errors
    }
    
    errors.reverse
  }

  /**
   * Gets the output directory as a File object
   */
  def outputDirectory: File = new File(outputDir)

  /**
   * Creates the directory structure for a given package
   */
  def packageDirectory(packageName: String): File = {
    val packagePath = packageName.replace('.', File.separatorChar)
    new File(outputDirectory, packagePath)
  }
}

object GeneratorConfig {
  /**
   * Creates a default configuration with standard Maven structure
   */
  def default(basePackage: String): GeneratorConfig = {
    GeneratorConfig(basePackage, "src/main/scala", useOptionTypes = true)
  }
  
  /**
   * Creates a configuration that uses nullable types instead of Option
   */
  def withNullableTypes(basePackage: String, outputDir: String): GeneratorConfig = {
    GeneratorConfig(basePackage, outputDir, useOptionTypes = false)
  }
  
  /**
   * Creates a configuration that uses Array instead of List for collections
   */
  def withArrayType(basePackage: String, outputDir: String): GeneratorConfig = {
    GeneratorConfig(basePackage, outputDir, listType = "Array")
  }
}