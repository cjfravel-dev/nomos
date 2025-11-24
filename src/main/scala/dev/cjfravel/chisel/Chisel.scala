package dev.cjfravel.chisel

import dev.cjfravel.chisel.model.MultiTemplate
import dev.cjfravel.chisel.parser.{TemplateParser, ParseError}
import dev.cjfravel.chisel.generation.{CodeGenerator, FileWriter, GeneratorConfig, GeneratorError, WriteReport}
import dev.cjfravel.chisel.validation.{MultiValidator, ValidationError}
import org.json4s.JValue

/**
 * Main entry point for the Chisel library.
 * 
 * Chisel allows you to define JSON templates with multiple type definitions and then:
 * 1. Generate Scala case classes from templates
 * 2. Validate JSON strings against templates
 * 
 * @example
 * {{{
 * // Parse a template from JSON
 * val template = Chisel.parseTemplate(templateJson).right.get
 * 
 * // Generate case classes (uses basePackage and outputDir from template)
 * val result = Chisel.generateCode(template)
 * 
 * // Validate JSON data against the main class
 * val validationResult = Chisel.validate(template, jsonData)
 * }}}
 */
object Chisel {
  
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
   * Uses the basePackage and outputDir specified in the template.
   *
   * @param template The template to generate code from
   * @return Either GeneratorError or WriteReport containing successes and failures
   */
  def generateCode(template: MultiTemplate): Either[GeneratorError, WriteReport] = {
    val config = GeneratorConfig(template.basePackage, template.outputDir)
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
  ): Either[List[ValidationError], JValue] = {
    val validator = new MultiValidator(template)
    val actualDefinitionName = if (definitionName == null) template.mainClass else definitionName
    validator.validate(json, actualDefinitionName)
  }
  
  /**
   * Complete workflow: parse template, generate code, and provide a validator.
   *
   * @param templateJson The JSON template definition
   * @return Either error or ChiselResult with template and write report
   */
  def process(templateJson: String): Either[ChiselError, ChiselResult] = {
    (for {
      template <- parseTemplate(templateJson).left.map(ChiselError.ParseFailed)
      report <- generateCode(template).left.map(ChiselError.GenerationFailed)
    } yield ChiselResult(template, report)).left.map(identity)
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
 * Errors that can occur in Chisel operations
 */
sealed trait ChiselError {
  def message: String
}

object ChiselError {
  case class ParseFailed(error: ParseError) extends ChiselError {
    def message: String = s"Failed to parse template: ${error.message}"
  }
  
  case class GenerationFailed(error: GeneratorError) extends ChiselError {
    def message: String = s"Failed to generate code: ${error.message}"
  }
}

/**
 * Result of processing a template.
 *
 * @param template The parsed template
 * @param writeReport The report from writing generated files
 */
case class ChiselResult(
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