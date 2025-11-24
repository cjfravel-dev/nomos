package dev.cjfravel.chisel.parser

import dev.cjfravel.chisel.model._
import org.json4s._
import org.json4s.native.JsonMethods

/**
 * Parses JSON template definitions into Template objects
 */
class TemplateParser {

  implicit val formats: Formats = DefaultFormats

  /**
   * Parses a JSON string into a Template
   */
  def parseTemplate(jsonString: String): Either[ParseError, Template] = {
    try {
      val json = JsonMethods.parse(jsonString)
      parseTemplateJson(json)
    } catch {
      case e: Exception =>
        Left(ParseError.JsonSyntaxError(e.getMessage))
    }
  }

  /**
   * Parses a JSON template structure
   */
  private def parseTemplateJson(json: JValue): Either[ParseError, Template] = {
    val path = "root"
    
    for {
      name <- extractString(json, "name", path)
      subPackage = extractOptionalString(json, "subPackage")
      description = extractOptionalString(json, "description")
      version = extractOptionalString(json, "version")
      templateJson <- extractField(json, "template", path)
      templateType <- parseType(templateJson, s"$path.template")
    } yield Template(name, subPackage, templateType, description, version)
  }

  /**
   * Parses a type definition
   */
  private def parseType(json: JValue, path: String): Either[ParseError, TemplateType] = {
    json match {
      // Simple string type names
      case JString("string") => Right(StringType())
      case JString("number") => Right(NumberType())
      case JString("boolean") => Right(BooleanType())
      
      // Reference to another definition (for multi-template mode)
      case JString(ref) if ref.startsWith("$ref:") =>
        val typeName = ref.substring(5)
        Right(ReferenceType(typeName))
      
      // Array type
      case JArray(List(elementType)) =>
        parseType(elementType, s"$path[]").map(ArrayType)
      
      // Object with type field (complex type definition)
      case JObject(fields) if fields.exists(_._1 == "type") =>
        parseComplexType(json, path)
      
      // Type discriminator
      case JObject(fields) if fields.exists(_._1 == "$type") =>
        parseDiscriminator(json, path)
      
      // Optional field wrapper
      case JObject(fields) if fields.exists(_._1 == "$optional") =>
        for {
          innerType <- extractField(json, "$optional", path)
          parsedType <- parseType(innerType, path)
        } yield parsedType // The optional flag is handled at the FieldDef level
      
      // Plain object type
      case JObject(_) =>
        parseObjectType(json, path)
      
      case _ =>
        Left(ParseError.InvalidType(json.toString, path, "Unrecognized type format"))
    }
  }

  /**
   * Parses a complex type definition with constraints
   */
  private def parseComplexType(json: JValue, path: String): Either[ParseError, TemplateType] = {
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
  private def parseStringConstraints(json: JValue, path: String): List[Constraint] = {
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
  private def parseNumberConstraints(json: JValue, path: String): List[Constraint] = {
    var constraints = List.empty[Constraint]
    
    extractOptionalDouble(json, "min").foreach(v => constraints = Min(v) :: constraints)
    extractOptionalDouble(json, "max").foreach(v => constraints = Max(v) :: constraints)
    extractOptionalDouble(json, "multipleOf").foreach(v => constraints = MultipleOf(v) :: constraints)
    
    constraints
  }

  /**
   * Parses an object type
   */
  private def parseObjectType(json: JValue, path: String): Either[ParseError, ObjectType] = {
    json match {
      case JObject(fields) =>
        val fieldResults = fields.map { case (fieldName, fieldValue) =>
          parseFieldDef(fieldValue, s"$path.$fieldName").map(fieldName -> _)
        }
        
        val errors = fieldResults.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(ParseError.MultipleErrors(errors))
        } else {
          val fieldMap = fieldResults.collect { case Right(pair) => pair }.toMap
          Right(ObjectType(fieldMap))
        }
      
      case _ =>
        Left(ParseError.InvalidType("object", path, "Expected JSON object"))
    }
  }

  /**
   * Parses a field definition
   */
  private def parseFieldDef(json: JValue, path: String): Either[ParseError, FieldDef] = {
    json match {
      // Optional field wrapper
      case JObject(fields) if fields.exists(_._1 == "$optional") =>
        for {
          innerType <- extractField(json, "$optional", path)
          fieldType <- parseType(innerType, path)
        } yield FieldDef(fieldType, optional = true)
      
      // Regular field
      case _ =>
        parseType(json, path).map(FieldDef(_, optional = false))
    }
  }

  /**
   * Parses a type discriminator
   */
  private def parseDiscriminator(json: JValue, path: String): Either[ParseError, TypeDiscriminator] = {
    for {
      typeObj <- extractField(json, "$type", path)
      fieldName <- extractString(typeObj, "discriminator", s"$path.$$type")
      variantsJson <- extractField(typeObj, "variants", s"$path.$$type")
      variants <- parseVariants(variantsJson, s"$path.$$type.variants")
      commonFields <- parseCommonFields(typeObj, s"$path.$$type")
      includeInOutput = extractOptionalBoolean(typeObj, "includeDiscriminator").getOrElse(true)
    } yield TypeDiscriminator(fieldName, variants, commonFields, includeInOutput)
  }

  /**
   * Parses discriminator variants
   */
  private def parseVariants(json: JValue, path: String): Either[ParseError, Map[String, ObjectType]] = {
    json match {
      case JObject(fields) =>
        val variantResults = fields.map { case (variantName, variantJson) =>
          parseObjectType(variantJson, s"$path.$variantName").map(variantName -> _)
        }
        
        val errors = variantResults.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(ParseError.MultipleErrors(errors))
        } else {
          Right(variantResults.collect { case Right(pair) => pair }.toMap)
        }
      
      case _ =>
        Left(ParseError.InvalidDiscriminator("variants must be an object", path))
    }
  }

  /**
   * Parses common fields for discriminator
   */
  private def parseCommonFields(json: JValue, path: String): Either[ParseError, Map[String, FieldDef]] = {
    extractOptionalField(json, "commonFields") match {
      case Some(commonJson) =>
        parseObjectType(commonJson, s"$path.commonFields").map(_.fields)
      case None =>
        Right(Map.empty)
    }
  }

  // Helper methods for extracting JSON fields

  private def extractField(json: JValue, fieldName: String, path: String): Either[ParseError, JValue] = {
    json \ fieldName match {
      case JNothing => Left(ParseError.MissingField(fieldName, path))
      case value => Right(value)
    }
  }

  private def extractOptionalField(json: JValue, fieldName: String): Option[JValue] = {
    json \ fieldName match {
      case JNothing => None
      case value => Some(value)
    }
  }

  private def extractString(json: JValue, fieldName: String, path: String): Either[ParseError, String] = {
    json \ fieldName match {
      case JString(s) => Right(s)
      case JNothing => Left(ParseError.MissingField(fieldName, path))
      case other => Left(ParseError.InvalidFieldValue(fieldName, "string", other.toString, path))
    }
  }

  private def extractOptionalString(json: JValue, fieldName: String): Option[String] = {
    json \ fieldName match {
      case JString(s) => Some(s)
      case _ => None
    }
  }

  private def extractOptionalInt(json: JValue, fieldName: String): Option[Int] = {
    json \ fieldName match {
      case JInt(i) => Some(i.toInt)
      case JDouble(d) => Some(d.toInt)
      case _ => None
    }
  }

  private def extractOptionalDouble(json: JValue, fieldName: String): Option[Double] = {
    json \ fieldName match {
      case JInt(i) => Some(i.toDouble)
      case JDouble(d) => Some(d)
      case _ => None
    }
  }

  private def extractOptionalBoolean(json: JValue, fieldName: String): Option[Boolean] = {
    json \ fieldName match {
      case JBool(b) => Some(b)
      case _ => None
    }
  }

  /**
   * Parses a JSON string into a MultiTemplate
   */
  def parseMultiTemplate(jsonString: String): Either[ParseError, MultiTemplate] = {
    try {
      val json = JsonMethods.parse(jsonString)
      parseMultiTemplateJson(json)
    } catch {
      case e: Exception =>
        Left(ParseError.JsonSyntaxError(e.getMessage))
    }
  }

  /**
   * Parses a multi-template JSON structure
   */
  private def parseMultiTemplateJson(json: JValue): Either[ParseError, MultiTemplate] = {
    val path = "root"
    
    for {
      basePackage <- extractString(json, "basePackage", path)
      outputDir <- extractString(json, "outputDir", path)
      mainClass <- extractString(json, "mainClass", path)
      definitionsJson <- extractField(json, "definitions", path)
      definitions <- parseDefinitions(definitionsJson, s"$path.definitions")
    } yield {
      val multiTemplate = MultiTemplate(basePackage, outputDir, mainClass, definitions)
      
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
  private def parseDefinitions(json: JValue, path: String): Either[ParseError, List[TemplateDefinition]] = {
    json match {
      case JArray(definitionJsons) =>
        val definitionResults = definitionJsons.zipWithIndex.map { case (defJson, idx) =>
          parseDefinition(defJson, s"$path[$idx]")
        }
        
        val errors = definitionResults.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(ParseError.MultipleErrors(errors))
        } else {
          Right(definitionResults.collect { case Right(definition) => definition })
        }
      
      case _ =>
        Left(ParseError.InvalidType("array", path, "definitions must be an array"))
    }
  }

  /**
   * Parses a single definition
   */
  private def parseDefinition(json: JValue, path: String): Either[ParseError, TemplateDefinition] = {
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
   * Convenience method to parse from string
   */
  def parseString(jsonString: String): Either[ParseError, Template] = {
    new TemplateParser().parseTemplate(jsonString)
  }
  
  /**
   * Convenience method to parse multi-template from string
   */
  def parseMultiTemplateString(jsonString: String): Either[ParseError, MultiTemplate] = {
    new TemplateParser().parseMultiTemplate(jsonString)
  }
}