package dev.cjfravel.chisel

import dev.cjfravel.chisel.model.Template
import dev.cjfravel.chisel.parser.{TemplateParser, ParseError}
import dev.cjfravel.chisel.generation.{CodeGenerator, FileWriter, GeneratorConfig, GeneratorError, WriteReport}
import dev.cjfravel.chisel.validation.{Validator, ValidationError}
import org.json4s.JValue

/**
 * Main entry point for the Chisel library.
 * 
 * Chisel allows you to define JSON templates and then:
 * 1. Generate Scala case classes from templates
 * 2. Validate JSON strings against templates
 * 
 * @example
 * {{{
 * // Parse a template from JSON
 * val template = Chisel.parseTemplate(templateJson).right.get
 * 
 * // Generate case classes
 * val result = Chisel.generateCode(template, "src/main/scala")
 * 
 * // Validate JSON data
 * val validationResult = Chisel.validate(template, jsonData)
 * }}}
 */
object Chisel {
  
  /**
   * Parse a template from JSON string.
   *
   * @param json The JSON string containing the template definition
   * @return Either ParseError or Template
   */
  def parseTemplate(json: String): Either[ParseError, Template] = {
    TemplateParser.parseString(json)
  }
  
  /**
   * Generate Scala case classes from a template.
   *
   * @param template The template to generate code from
   * @param basePackage The base package for generated code
   * @param outputDir The directory to write generated files to
   * @return Either GeneratorError or WriteReport containing successes and failures
   */
  def generateCode(
    template: Template,
    basePackage: String,
    outputDir: String
  ): Either[GeneratorError, WriteReport] = {
    val config = GeneratorConfig(basePackage, outputDir)
    val generator = new CodeGenerator(config)
    val writer = new FileWriter()
    val outputDirFile = new java.io.File(outputDir)
    
    generator.generate(template).map { generatedFiles =>
      writer.writeFilesWithReport(generatedFiles, outputDirFile)
    }
  }
  
  /**
   * Validate a JSON string against a template.
   * 
   * @param template The template to validate against
   * @param json The JSON string to validate
   * @return Either a list of validation errors or the parsed JSON value
   */
  def validate(template: Template, json: String): Either[List[ValidationError], JValue] = {
    val validator = new Validator(template)
    validator.validate(json)
  }
  
  /**
   * Complete workflow: parse template, generate code, and provide a validator.
   *
   * @param templateJson The JSON template definition
   * @param basePackage The base package for generated code
   * @param outputDir The directory to write generated files to
   * @return Either error or ChiselResult with template and write report
   */
  def process(
    templateJson: String,
    basePackage: String,
    outputDir: String
  ): Either[ChiselError, ChiselResult] = {
    (for {
      template <- parseTemplate(templateJson).left.map(ChiselError.ParseFailed)
      report <- generateCode(template, basePackage, outputDir).left.map(ChiselError.GenerationFailed)
    } yield ChiselResult(template, report)).left.map(identity)
  }
  
  /**
   * Parse template and create a validator for it.
   * 
   * @param templateJson The JSON template definition
   * @return Either ParseError or Validator
   */
  def createValidator(templateJson: String): Either[ParseError, Validator] = {
    parseTemplate(templateJson).map(new Validator(_))
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
  template: Template,
  writeReport: WriteReport
) {
  /**
   * Create a validator for this template.
   */
  def validator: Validator = new Validator(template)
  
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