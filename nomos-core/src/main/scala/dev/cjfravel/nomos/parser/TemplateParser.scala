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
        case "int" => Right(IntType())
        case "long" => Right(LongType())
        case "decimal" => Right(DecimalType())
        case "double" => Right(NumberType())
        case "boolean" => Right(BooleanType())
        case "date" => Right(DateType())
        case "datetime" => Right(DateTimeType())
        
        // Reference to another definition (for multi-template mode)
        case ref if ref.startsWith("$ref:") =>
          val typeName = ref.substring(5)
          Right(ReferenceType(typeName))
        
        case ext if ext.startsWith("$extern:") =>
          Right(ExternalType(ext.substring(8)))
        
        case _ => Left(ParseError.InvalidType(text, path))
      }
    } else if (json.isArray) {
      if (json.size() == 1) {
        parseType(json.get(0), s"$path[]").map(t => ArrayType(t))
      } else if (json.size() > 1) {
        val parsed = json.elements().asScala.toList.zipWithIndex.map { case (n, i) => parseType(n, s"$path|$i") }
        val errs = parsed.collect { case Left(e) => e }
        if (errs.nonEmpty) Left(ParseError.MultipleErrors(errs))
        else Right(UnionType(parsed.collect { case Right(t) => t }))
      } else {
        Left(ParseError.InvalidType("array", path, "Array must have at least one element type"))
      }
    } else if (json.isObject) {
      // Check for different object patterns
      if (json.has("type")) {
        parseComplexType(json, path)
      } else if (json.has("$type")) {
        parseDiscriminator(json, path)
      } else if (json.has("$map")) {
        for {
          valueJson <- extractField(json, "$map", path)
          valueType <- parseType(valueJson, s"$path{}")
        } yield MapType(valueType)
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
        if (json.has("enum") && extractOptionalString(json, "as").contains("enumType")) {
          val name = extractOptionalString(json, "name").getOrElse("Enum")
          val values = parseEnum(json).map(_.values).getOrElse(Nil)
          Right(EnumType(name, values))
        } else {
          Right(StringType(parseStringConstraints(json, path)))
        }
      
      case "number" =>
        val constraints = parseNumberConstraints(json, path)
        Right(NumberType(constraints))
      
      case "int" =>
        Right(IntType(parseNumberConstraints(json, path)))
      
      case "long" =>
        Right(LongType(parseNumberConstraints(json, path)))
      
      case "decimal" =>
        Right(DecimalType(parseNumberConstraints(json, path)))
      
      case "double" =>
        Right(NumberType(parseNumberConstraints(json, path)))
      
      case "boolean" =>
        Right(BooleanType())
      
      case "array" =>
        extractField(json, "items", path).flatMap { itemsJson =>
          parseType(itemsJson, s"$path.items").map { elementType =>
            val withEnum = parseEnum(json).map(e => withConstraint(elementType, e)).getOrElse(elementType)
            ArrayType(withEnum, parseArrayConstraints(json))
          }
        }
      
      case other =>
        Left(ParseError.InvalidType(other, path))
    }
  }

  /**
   * Returns an Enum constraint if the node carries a string "enum" array.
   */
  private def parseEnum(json: JsonNode): Option[Enum] = {
    if (json.has("enum") && json.get("enum").isArray) {
      Some(Enum(json.get("enum").elements().asScala.collect { case n if n.isTextual => n.asText() }.toList))
    } else None
  }

  /**
   * Appends a constraint to a scalar type's constraint list.
   */
  private def withConstraint(t: TemplateType, c: Constraint): TemplateType = t match {
    case StringType(cs) => StringType(cs :+ c)
    case other => other
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
    parseEnum(json).foreach(e => constraints = e :: constraints)
    
    constraints
  }

  /**
   * Parses array item-count and uniqueness constraints.
   */
  private def parseArrayConstraints(json: JsonNode): List[Constraint] = {
    var constraints = List.empty[Constraint]
    extractOptionalInt(json, "minItems").foreach(n => constraints = MinItems(n) :: constraints)
    extractOptionalInt(json, "maxItems").foreach(n => constraints = MaxItems(n) :: constraints)
    extractOptionalBoolean(json, "uniqueItems").foreach(u => constraints = UniqueItems(u) :: constraints)
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
      val fields = json.fields().asScala.toList.filterNot(_.getKey == "$additionalProperties")
      val fieldResults = fields.map { entry =>
        val fieldName = unescapeKey(entry.getKey)
        val fieldValue = entry.getValue
        parseFieldDef(fieldValue, s"$path.$fieldName").map(fieldName -> _)
      }
      
      val errors = fieldResults.collect { case Left(err) => err }
      if (errors.nonEmpty) {
        Left(ParseError.MultipleErrors(errors))
      } else {
        val fieldMap = ListMap(fieldResults.collect { case Right(pair) => pair }: _*)
        Right(ObjectType(fieldMap, parseAdditional(json, path)))
      }
    } else {
      Left(ParseError.InvalidType("object", path, "Expected JSON object"))
    }
  }

  /**
   * Strips surrounding backticks so a quoted key like `type` becomes the literal field name "type".
   */
  private def unescapeKey(key: String): String =
    if (key.length >= 2 && key.startsWith("`") && key.endsWith("`")) key.substring(1, key.length - 1) else key

  /**
   * Parses the $additionalProperties policy: true allows extras, false forbids, a type validates extras.
   */
  private def parseAdditional(json: JsonNode, path: String): AdditionalProperties = {
    if (!json.has("$additionalProperties")) ForbidExtra
    else {
      val node = json.get("$additionalProperties")
      if (node.isBoolean) { if (node.asBoolean()) AllowExtra else ForbidExtra }
      else parseType(node, s"$path.$$additionalProperties").map(TypedExtra).getOrElse(ForbidExtra)
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
      } yield FieldDef(fieldType, optional = true, nullable = extractOptionalBoolean(json, "nullable").getOrElse(false))
    } else {
      parseType(json, path).map(FieldDef(_, optional = false, default = renderDefault(json), adapter = extractOptionalString(json, "adapter")))
    }
  }

  /**
   * Renders a "default" literal from a complex type node as Scala source (strings quoted).
   */
  private def renderDefault(json: JsonNode): Option[String] = {
    if (json.isObject && json.has("default")) {
      val d = json.get("default")
      if (d.isTextual) Some("\"" + d.asText() + "\"") else Some(d.toString)
    } else None
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
      variantMatch = extractOptionalString(typeObj, "variantMatch").getOrElse("exact")
      variantSubPackage = extractOptionalString(typeObj, "variantSubPackage")
    } yield TypeDiscriminator(fieldName, variants, commonFields, includeInOutput, variantNames, variantMatch, variantSubPackage)
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
        if (variantJson.isTextual && variantJson.asText().startsWith("$ref:")) {
          val refName = variantJson.asText().substring(5)
          Right(variantName -> ObjectType(ListMap("$ref" -> FieldDef(ReferenceType(refName)))))
        } else {
          parseObjectType(variantJson, s"$path.$variantName").map(variantName -> _)
        }
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

  private def extractStringList(json: JsonNode, fieldName: String): List[String] = {
    if (json.has(fieldName) && json.get(fieldName).isArray) {
      json.get(fieldName).elements().asScala.collect { case n if n.isTextual => n.asText() }.toList
    } else {
      List.empty
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
  /**
   * Replaces $ref variant markers in discriminators with the referenced definition's object fields.
   */
  private def resolveRefVariants(definitions: List[TemplateDefinition]): List[TemplateDefinition] = {
    val byName = definitions.map(d => d.name -> d).toMap
    def resolveVariant(v: ObjectType): ObjectType = v.fields.get("$ref") match {
      case Some(FieldDef(ReferenceType(name), _, _, _, _)) =>
        byName.get(name).map(_.templateType).collect { case o: ObjectType => o }.getOrElse(v)
      case _ => v
    }
    definitions.map { d =>
      d.templateType match {
        case td: TypeDiscriminator =>
          d.copy(templateType = td.copy(variants = td.variants.map { case (k, v) => k -> resolveVariant(v) }))
        case _ => d
      }
    }
  }

  def parseMultiTemplate(jsonString: String, basePackage: String, validateRefs: Boolean = true): Either[ParseError, MultiTemplate] = {
    try {
      val json = mapper.readTree(jsonString)
      parseMultiTemplateJson(json, basePackage, validateRefs)
    } catch {
      case e: Exception =>
        Left(ParseError.JsonSyntaxError(e.getMessage))
    }
  }

  /**
   * Parses a multi-template JSON structure. basePackage is supplied by the caller.
   */
  private def parseMultiTemplateJson(json: JsonNode, basePackage: String, validateRefs: Boolean): Either[ParseError, MultiTemplate] = {
    val path = "root"
    
    for {
      definitionsJson <- extractField(json, "definitions", path)
      parsed <- parseDefinitions(definitionsJson, s"$path.definitions")
    } yield {
      val definitions = resolveRefVariants(parsed)
      val useOptionTypes = extractOptionalBoolean(json, "useOptionTypes").getOrElse(true)
      val listType = extractOptionalString(json, "listType").getOrElse("List")
      val fromJsonStyle = extractOptionalString(json, "fromJsonStyle").getOrElse("either")
      val dateType = extractOptionalString(json, "dateType").getOrElse("java.time.LocalDate")
      val dateTimeType = extractOptionalString(json, "dateTimeType").getOrElse("java.time.LocalDateTime")
      val mapType = extractOptionalString(json, "mapType").getOrElse("Map")
      val multiTemplate = MultiTemplate(basePackage, definitions, useOptionTypes, listType, fromJsonStyle, dateType, dateTimeType, mapType)
      
      multiTemplate.validate(validateRefs) match {
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
      validators = extractStringList(json, "validators")
      methods = extractStringList(json, "methods")
      templateJson <- extractField(json, "template", path)
      templateType <- parseType(templateJson, s"$path.template")
    } yield TemplateDefinition(name, templateType, subPackage, description, validators, methods)
  }
}

object TemplateParser {
  def apply(): TemplateParser = new TemplateParser()
  
  /**
   * Convenience method to parse multi-template from string
   */
  def parseMultiTemplateString(jsonString: String, basePackage: String, validateRefs: Boolean = true): Either[ParseError, MultiTemplate] = {
    new TemplateParser().parseMultiTemplate(jsonString, basePackage, validateRefs)
  }
}