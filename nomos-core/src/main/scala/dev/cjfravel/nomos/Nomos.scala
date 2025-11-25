package dev.cjfravel.nomos

import dev.cjfravel.nomos.model.MultiTemplate
import dev.cjfravel.nomos.parser.{TemplateParser, ParseError}
import dev.cjfravel.nomos.generation.{CodeGenerator, FileWriter, GeneratorConfig, GeneratorError, WriteReport}
import dev.cjfravel.nomos.validation.{MultiValidator, ValidationError}
import com.fasterxml.jackson.databind.JsonNode

/**
 * Main entry point for the Nomos library.
 *
 * Nomos allows you to define JSON templates with multiple type definitions and then:
 * 1. Generate Scala case classes from templates
 * 2. Validate JSON strings against templates
 *
 * @example
 * {{{
 * // Parse a template from JSON
 * val template = Nomos.parseTemplate(templateJson).right.get
 *
 * // Generate case classes (uses basePackage and outputDir from template)
 * val result = Nomos.generateCode(template)
 *
 * // Validate JSON data against the main class
 * val validationResult = Nomos.validate(template, jsonData)
 * }}}
 */
object Nomos {
  
  /**
   * Parse a template from JSON string.
   *
   * @param json The JSON string containing the template definition with definitions array
   * @return Either ParseError or MultiTemplate
   */
  def parseTemplate(json: String): Either[ParseError, MultiTemplate] = {
    TemplateParser.parseMultiTemplateString(json)
  }
  
  /**
   * Generate Scala case classes from a template.
   * Uses the basePackage, outputDir, useOptionTypes, and listType specified in the template.
   *
   * @param template The template to generate code from
   * @return Either GeneratorError or WriteReport containing successes and failures
   */
  def generateCode(template: MultiTemplate): Either[GeneratorError, WriteReport] = {
    val config = GeneratorConfig(
      template.basePackage,
      template.outputDir,
      template.useOptionTypes,
      template.listType
    )
    val generator = new CodeGenerator(config)
    val writer = new FileWriter()
    val outputDirFile = new java.io.File(template.outputDir)
    
    generator.generateMulti(template).map { generatedFiles =>
      writer.writeFilesWithReport(generatedFiles, outputDirFile)
    }
  }
  
  /**
   * Validate a JSON string against a template.
   * 
   * @param template The template to validate against
   * @param json The JSON string to validate
   * @param definitionName The name of the definition to validate against (defaults to mainClass)
   * @return Either a list of validation errors or the parsed JSON value
   */
  def validate(
    template: MultiTemplate,
    json: String,
    definitionName: String = null
  ): Either[List[ValidationError], JsonNode] = {
    val validator = new MultiValidator(template)
    val actualDefinitionName = if (definitionName == null) template.mainClass else definitionName
    validator.validate(json, actualDefinitionName)
  }
  
  /**
   * Complete workflow: parse template, generate code, and provide a validator.
   *
   * @param templateJson The JSON template definition
   * @return Either error or NomosResult with template and write report
   */
  def process(templateJson: String): Either[NomosError, NomosResult] = {
    (for {
      template <- parseTemplate(templateJson).left.map(NomosError.ParseFailed)
      report <- generateCode(template).left.map(NomosError.GenerationFailed)
    } yield NomosResult(template, report)).left.map(identity)
  }
  
  /**
   * Parse template and create a validator for it.
   * 
   * @param templateJson The JSON template definition
   * @return Either ParseError or MultiValidator
   */
  def createValidator(templateJson: String): Either[ParseError, MultiValidator] = {
    parseTemplate(templateJson).map(new MultiValidator(_))
  }
}

/**
 * Errors that can occur in Nomos operations
 */
sealed trait NomosError {
  def message: String
}

object NomosError {
  case class ParseFailed(error: ParseError) extends NomosError {
    def message: String = s"Failed to parse template: ${error.message}"
  }
  
  case class GenerationFailed(error: GeneratorError) extends NomosError {
    def message: String = s"Failed to generate code: ${error.message}"
  }
}

/**
 * Result of processing a template.
 *
 * @param template The parsed template
 * @param writeReport The report from writing generated files
 */
case class NomosResult(
  template: MultiTemplate,
  writeReport: WriteReport
) {
  /**
   * Create a validator for this template.
   */
  def validator: MultiValidator = new MultiValidator(template)
  
  /**
   * Check if code generation was successful.
   */
  def isSuccess: Boolean = writeReport.failures.isEmpty
  
  /**
   * Get all generated file paths.
   */
  def generatedFiles: List[String] = writeReport.successPaths
  
  /**
   * Get all errors from code generation.
   */
  def errors: List[String] = writeReport.failureMessages
}