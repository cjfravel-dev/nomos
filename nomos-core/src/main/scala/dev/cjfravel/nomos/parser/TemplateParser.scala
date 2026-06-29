package dev.cjfravel.nomos.parser

import dev.cjfravel.nomos.model._
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap

/**
 * Parses JSON template definitions into Template objects using Jackson
 */
class TemplateParser {

  private val mapper = new ObjectMapper()

  /**
   * Parses a type definition
   */
  private def parseType(json: JsonNode, path: String): Either[ParseError, TemplateType] = {
    if (json.isTextual) {
      val text = json.asText()
      text match {
        // Simple string type names
        case "string" => Right(StringType())
        case "number" => Right(NumberType())
        case "boolean" => Right(BooleanType())
        
        // Reference to another definition (for multi-template mode)
        case ref if ref.startsWith("$ref:") =>
          val typeName = ref.substring(5)
          Right(ReferenceType(typeName))
        
        case _ => Left(ParseError.InvalidType(text, path))
      }
    } else if (json.isArray) {
      // Array type
      if (json.size() == 1) {
        parseType(json.get(0), s"$path[]").map(ArrayType)
      } else {
        Left(ParseError.InvalidType("array", path, "Array must have exactly one element type"))
      }
    } else if (json.isObject) {
      // Check for different object patterns
      if (json.has("type")) {
        parseComplexType(json, path)
      } else if (json.has("$type")) {
        parseDiscriminator(json, path)
      } else if (json.has("$optional")) {
        for {
          innerType <- extractField(json, "$optional", path)
          parsedType <- parseType(innerType, path)
        } yield parsedType // The optional flag is handled at the FieldDef level
      } else {
        // Plain object type
        parseObjectType(json, path)
      }
    } else {
      Left(ParseError.InvalidType(json.toString, path, "Unrecognized type format"))
    }
  }

  /**
   * Parses a complex type definition with constraints
   */
  private def parseComplexType(json: JsonNode, path: String): Either[ParseError, TemplateType] = {
    extractString(json, "type", path).flatMap {
      case "string" =>
        val constraints = parseStringConstraints(json, path)
        Right(StringType(constraints))
      
      case "number" =>
        val constraints = parseNumberConstraints(json, path)
        Right(NumberType(constraints))
      
      case "boolean" =>
        Right(BooleanType())
      
      case other =>
        Left(ParseError.InvalidType(other, path))
    }
  }

  /**
   * Parses string constraints
   */
  private def parseStringConstraints(json: JsonNode, path: String): List[Constraint] = {
    var constraints = List.empty[Constraint]
    
    extractOptionalInt(json, "minLength").foreach(len => constraints = MinLength(len) :: constraints)
    extractOptionalInt(json, "maxLength").foreach(len => constraints = MaxLength(len) :: constraints)
    extractOptionalString(json, "pattern").foreach(pat => constraints = Pattern(pat) :: constraints)
    extractOptionalString(json, "format").foreach(fmt => constraints = Format(fmt) :: constraints)
    
    constraints
  }

  /**
   * Parses number constraints
   */
  private def parseNumberConstraints(json: JsonNode, path: String): List[Constraint] = {
    var constraints = List.empty[Constraint]
    
    extractOptionalDouble(json, "min").foreach(v => constraints = Min(v) :: constraints)
    extractOptionalDouble(json, "max").foreach(v => constraints = Max(v) :: constraints)
    extractOptionalDouble(json, "multipleOf").foreach(v => constraints = MultipleOf(v) :: constraints)
    
    constraints
  }

  /**
   * Parses an object type
   */
  private def parseObjectType(json: JsonNode, path: String): Either[ParseError, ObjectType] = {
    if (json.isObject) {
      val fields = json.fields().asScala.toList
      val fieldResults = fields.map { entry =>
        val fieldName = entry.getKey
        val fieldValue = entry.getValue
        parseFieldDef(fieldValue, s"$path.$fieldName").map(fieldName -> _)
      }
      
      val errors = fieldResults.collect { case Left(err) => err }
      if (errors.nonEmpty) {
        Left(ParseError.MultipleErrors(errors))
      } else {
        // Use ListMap to preserve field order from JSON
        val fieldMap = ListMap(fieldResults.collect { case Right(pair) => pair }: _*)
        Right(ObjectType(fieldMap))
      }
    } else {
      Left(ParseError.InvalidType("object", path, "Expected JSON object"))
    }
  }

  /**
   * Parses a field definition
   */
  private def parseFieldDef(json: JsonNode, path: String): Either[ParseError, FieldDef] = {
    if (json.isObject && json.has("$optional")) {
      for {
        innerType <- extractField(json, "$optional", path)
        fieldType <- parseType(innerType, path)
      } yield FieldDef(fieldType, optional = true)
    } else {
      parseType(json, path).map(FieldDef(_, optional = false))
    }
  }

  /**
   * Parses a type discriminator
   */
  private def parseDiscriminator(json: JsonNode, path: String): Either[ParseError, TypeDiscriminator] = {
    for {
      typeObj <- extractField(json, "$type", path)
      fieldName <- extractString(typeObj, "discriminator", s"$path.$$type")
      variantsJson <- extractField(typeObj, "variants", s"$path.$$type")
      variants <- parseVariants(variantsJson, s"$path.$$type.variants")
      commonFields <- parseCommonFields(typeObj, s"$path.$$type")
      includeInOutput = extractOptionalBoolean(typeObj, "includeDiscriminator").getOrElse(true)
      variantNames = parseVariantNames(typeObj, s"$path.$$type")
    } yield TypeDiscriminator(fieldName, variants, commonFields, includeInOutput, variantNames)
  }

  /**
   * Parses discriminator variants
   */
  private def parseVariants(json: JsonNode, path: String): Either[ParseError, ListMap[String, ObjectType]] = {
    if (json.isObject) {
      val fields = json.fields().asScala.toList
      val variantResults = fields.map { entry =>
        val variantName = entry.getKey
        val variantJson = entry.getValue
        parseObjectType(variantJson, s"$path.$variantName").map(variantName -> _)
      }
      
      val errors = variantResults.collect { case Left(err) => err }
      if (errors.nonEmpty) {
        Left(ParseError.MultipleErrors(errors))
      } else {
        // Use ListMap to preserve variant order from JSON
        Right(ListMap(variantResults.collect { case Right(pair) => pair }: _*))
      }
    } else {
      Left(ParseError.InvalidDiscriminator("variants must be an object", path))
    }
  }

  /**
   * Parses common fields for discriminator
   */
  private def parseCommonFields(json: JsonNode, path: String): Either[ParseError, ListMap[String, FieldDef]] = {
    extractOptionalField(json, "commonFields") match {
      case Some(commonJson) =>
        parseObjectType(commonJson, s"$path.commonFields").map(_.fields)
      case None =>
        Right(ListMap.empty)
    }
  }

  /**
   * Parses variant names mapping for discriminator
   */
  private def parseVariantNames(json: JsonNode, path: String): Map[String, String] = {
    extractOptionalField(json, "variantNames") match {
      case Some(variantNamesNode) if variantNamesNode.isObject =>
        variantNamesNode.fields().asScala.collect {
          case entry if entry.getValue.isTextual =>
            entry.getKey -> entry.getValue.asText()
        }.toMap
      case _ =>
        Map.empty
    }
  }

  // Helper methods for extracting JSON fields

  private def extractField(json: JsonNode, fieldName: String, path: String): Either[ParseError, JsonNode] = {
    if (json.has(fieldName)) {
      Right(json.get(fieldName))
    } else {
      Left(ParseError.MissingField(fieldName, path))
    }
  }

  private def extractOptionalField(json: JsonNode, fieldName: String): Option[JsonNode] = {
    if (json.has(fieldName)) Some(json.get(fieldName)) else None
  }

  private def extractString(json: JsonNode, fieldName: String, path: String): Either[ParseError, String] = {
    if (json.has(fieldName)) {
      val node = json.get(fieldName)
      if (node.isTextual) {
        Right(node.asText())
      } else {
        Left(ParseError.InvalidFieldValue(fieldName, "string", node.toString, path))
      }
    } else {
      Left(ParseError.MissingField(fieldName, path))
    }
  }

  private def extractOptionalString(json: JsonNode, fieldName: String): Option[String] = {
    if (json.has(fieldName) && json.get(fieldName).isTextual) {
      Some(json.get(fieldName).asText())
    } else {
      None
    }
  }

  private def extractOptionalInt(json: JsonNode, fieldName: String): Option[Int] = {
    if (json.has(fieldName)) {
      val node = json.get(fieldName)
      if (node.isInt) Some(node.asInt())
      else if (node.isDouble) Some(node.asDouble().toInt)
      else None
    } else {
      None
    }
  }

  private def extractOptionalDouble(json: JsonNode, fieldName: String): Option[Double] = {
    if (json.has(fieldName)) {
      val node = json.get(fieldName)
      if (node.isNumber) Some(node.asDouble())
      else None
    } else {
      None
    }
  }

  private def extractOptionalBoolean(json: JsonNode, fieldName: String): Option[Boolean] = {
    if (json.has(fieldName) && json.get(fieldName).isBoolean) {
      Some(json.get(fieldName).asBoolean())
    } else {
      None
    }
  }

  /**
   * Parses a JSON string into a MultiTemplate
   */
  def parseMultiTemplate(jsonString: String, basePackage: String): Either[ParseError, MultiTemplate] = {
    try {
      val json = mapper.readTree(jsonString)
      parseMultiTemplateJson(json, basePackage)
    } catch {
      case e: Exception =>
        Left(ParseError.JsonSyntaxError(e.getMessage))
    }
  }

  /**
   * Parses a multi-template JSON structure. basePackage is supplied by the caller.
   */
  private def parseMultiTemplateJson(json: JsonNode, basePackage: String): Either[ParseError, MultiTemplate] = {
    val path = "root"
    
    for {
      definitionsJson <- extractField(json, "definitions", path)
      definitions <- parseDefinitions(definitionsJson, s"$path.definitions")
    } yield {
      val useOptionTypes = extractOptionalBoolean(json, "useOptionTypes").getOrElse(true)
      val listType = extractOptionalString(json, "listType").getOrElse("List")
      val multiTemplate = MultiTemplate(basePackage, definitions, useOptionTypes, listType)
      
      // Validate the multi-template
      multiTemplate.validate() match {
        case Nil => multiTemplate
        case errors => return Left(ParseError.MultipleErrors(errors.map(err => ParseError.InvalidFieldValue("template", "valid", err, path))))
      }
      
      multiTemplate
    }
  }

  /**
   * Parses the definitions array
   */
  private def parseDefinitions(json: JsonNode, path: String): Either[ParseError, List[TemplateDefinition]] = {
    if (json.isArray) {
      val definitionResults = json.elements().asScala.toList.zipWithIndex.map { case (defJson, idx) =>
        parseDefinition(defJson, s"$path[$idx]")
      }
      
      val errors = definitionResults.collect { case Left(err) => err }
      if (errors.nonEmpty) {
        Left(ParseError.MultipleErrors(errors))
      } else {
        Right(definitionResults.collect { case Right(definition) => definition })
      }
    } else {
      Left(ParseError.InvalidType("array", path, "definitions must be an array"))
    }
  }

  /**
   * Parses a single definition
   */
  private def parseDefinition(json: JsonNode, path: String): Either[ParseError, TemplateDefinition] = {
    for {
      name <- extractString(json, "name", path)
      subPackage = extractOptionalString(json, "subPackage")
      description = extractOptionalString(json, "description")
      templateJson <- extractField(json, "template", path)
      templateType <- parseType(templateJson, s"$path.template")
    } yield TemplateDefinition(name, templateType, subPackage, description)
  }
}

object TemplateParser {
  def apply(): TemplateParser = new TemplateParser()
  
  /**
   * Convenience method to parse multi-template from string
   */
  def parseMultiTemplateString(jsonString: String, basePackage: String): Either[ParseError, MultiTemplate] = {
    new TemplateParser().parseMultiTemplate(jsonString, basePackage)
  }
}