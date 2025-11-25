package dev.cjfravel.nomos.validation

import dev.cjfravel.nomos.model._
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import scala.collection.JavaConverters._

/**
 * Validates JSON data against Nomos templates using Jackson
 */
class Validator(template: Template) {

  private val mapper = new ObjectMapper()

  /**
   * Validates a JSON string against the template
   * 
   * @param jsonString The JSON string to validate
   * @return Either a list of validation errors or the parsed JSON
   */
  def validate(jsonString: String): Either[List[ValidationError], JsonNode] = {
    try {
      val json = mapper.readTree(jsonString)
      validateType(template.templateType, json, "root") match {
        case Nil => Right(json)
        case errors => Left(errors)
      }
    } catch {
      case e: Exception =>
        Left(List(ValidationError("root", s"Invalid JSON: ${e.getMessage}", "valid JSON", jsonString)))
    }
  }

  /**
   * Validates a parsed JSON value against the template
   */
  def validateJson(json: JsonNode): Either[List[ValidationError], JsonNode] = {
    validateType(template.templateType, json, "root") match {
      case Nil => Right(json)
      case errors => Left(errors)
    }
  }

  /**
   * Validates a type against JSON
   */
  private def validateType(templateType: TemplateType, json: JsonNode, path: String): List[ValidationError] = {
    templateType match {
      case StringType(constraints) => validateString(json, path, constraints)
      case NumberType(constraints) => validateNumber(json, path, constraints)
      case BooleanType() => validateBoolean(json, path)
      case ArrayType(elementType) => validateArray(elementType, json, path)
      case ObjectType(fields) => validateObject(fields, json, path)
      case TypeDiscriminator(fieldName, variants, commonFields, _, _) =>
        validateDiscriminator(fieldName, variants, commonFields, json, path)
      case ReferenceType(typeName) =>
        // References should be resolved before validation in multi-template mode
        List(ValidationError(path, s"Unresolved reference: $typeName", "resolved type", "reference"))
      case RecursiveRef(_) =>
        // Recursive refs should have been resolved, but if we encounter one, we can't validate further
        List.empty
    }
  }

  private def validateString(json: JsonNode, path: String, constraints: List[Constraint]): List[ValidationError] = {
    if (json.isTextual) {
      val value = json.asText()
      constraints.flatMap {
        case MinLength(len) if value.length < len =>
          Some(ValidationError.constraintViolation(path, s"minLength: $len", s"length ${value.length}"))
        case MaxLength(len) if value.length > len =>
          Some(ValidationError.constraintViolation(path, s"maxLength: $len", s"length ${value.length}"))
        case Pattern(regex) if !value.matches(regex) =>
          Some(ValidationError.constraintViolation(path, s"pattern: $regex", value))
        case Format(fmt) => validateFormat(fmt, value, path)
        case _ => None
      }
    } else {
      List(ValidationError.typeMismatch(path, "string", json.getNodeType.toString))
    }
  }

  private def validateFormat(format: String, value: String, path: String): Option[ValidationError] = {
    format match {
      case "email" if !value.matches("""^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""") =>
        Some(ValidationError.constraintViolation(path, "format: email", value))
      case "url" if !value.matches("""^https?://.*""") =>
        Some(ValidationError.constraintViolation(path, "format: url", value))
      case "uuid" if !value.matches("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""") =>
        Some(ValidationError.constraintViolation(path, "format: uuid", value))
      case "iso8601" if !value.matches("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$""") =>
        Some(ValidationError.constraintViolation(path, "format: iso8601", value))
      case "alphaNoWhitespace" if !value.matches("""^[a-zA-Z]+$""") =>
        Some(ValidationError.constraintViolation(path, "format: alphaNoWhitespace", value))
      case "majorAndMinor" if !value.matches("""^[0-9]+\.[0-9]+$""") =>
        Some(ValidationError.constraintViolation(path, "format: majorAndMinor", value))
      case _ => None
    }
  }

  private def validateNumber(json: JsonNode, path: String, constraints: List[Constraint]): List[ValidationError] = {
    if (json.isNumber) {
      validateNumberConstraints(json.asDouble(), path, constraints)
    } else {
      List(ValidationError.typeMismatch(path, "number", json.getNodeType.toString))
    }
  }

  private def validateNumberConstraints(value: Double, path: String, constraints: List[Constraint]): List[ValidationError] = {
    constraints.flatMap {
      case Min(min) if value < min =>
        Some(ValidationError.constraintViolation(path, s"min: $min", value.toString))
      case Max(max) if value > max =>
        Some(ValidationError.constraintViolation(path, s"max: $max", value.toString))
      case MultipleOf(mult) if value % mult != 0 =>
        Some(ValidationError.constraintViolation(path, s"multipleOf: $mult", value.toString))
      case _ => None
    }
  }

  private def validateBoolean(json: JsonNode, path: String): List[ValidationError] = {
    if (json.isBoolean) {
      List.empty
    } else {
      List(ValidationError.typeMismatch(path, "boolean", json.getNodeType.toString))
    }
  }

  private def validateArray(elementType: TemplateType, json: JsonNode, path: String): List[ValidationError] = {
    if (json.isArray) {
      json.elements().asScala.toList.zipWithIndex.flatMap { case (element, idx) =>
        validateType(elementType, element, s"$path[$idx]")
      }
    } else {
      List(ValidationError.typeMismatch(path, "array", json.getNodeType.toString))
    }
  }

  private def validateObject(fields: Map[String, FieldDef], json: JsonNode, path: String): List[ValidationError] = {
    if (json.isObject) {
      val jsonFieldMap = json.fields().asScala.toList.map(entry => entry.getKey -> entry.getValue).toMap
      
      // Check required fields
      val missingFields = fields.collect {
        case (fieldName, fieldDef) if !fieldDef.optional && !jsonFieldMap.contains(fieldName) =>
          ValidationError.missingField(path, fieldName)
      }.toList

      // Validate present fields
      val fieldErrors = fields.flatMap { case (fieldName, fieldDef) =>
        jsonFieldMap.get(fieldName) match {
          case Some(value) =>
            validateType(fieldDef.fieldType, value, s"$path.$fieldName")
          case None if fieldDef.optional =>
            List.empty
          case None =>
            List.empty // Already handled in missingFields
        }
      }.toList

      // Check for extra fields not in template
      val extraFields = jsonFieldMap.keySet.diff(fields.keySet).toList.map { extraField =>
        ValidationError(
          s"$path.$extraField",
          s"Unexpected field '$extraField' not defined in template",
          s"one of: ${fields.keySet.mkString(", ")}",
          extraField
        )
      }

      missingFields ++ fieldErrors ++ extraFields

    } else {
      List(ValidationError.typeMismatch(path, "object", json.getNodeType.toString))
    }
  }

  private def validateDiscriminator(
    fieldName: String,
    variants: Map[String, ObjectType],
    commonFields: Map[String, FieldDef],
    json: JsonNode,
    path: String
  ): List[ValidationError] = {
    if (json.isObject) {
      val jsonFieldMap = json.fields().asScala.toList.map(entry => entry.getKey -> entry.getValue).toMap
      
      // Get discriminator value
      jsonFieldMap.get(fieldName) match {
        case Some(discriminatorNode) if discriminatorNode.isTextual =>
          val discriminatorValue = discriminatorNode.asText()
          variants.get(discriminatorValue) match {
            case Some(variantType) =>
              // Validate common fields
              val commonErrors = commonFields.flatMap { case (cfName, cfDef) =>
                jsonFieldMap.get(cfName) match {
                  case Some(value) =>
                    validateType(cfDef.fieldType, value, s"$path.$cfName")
                  case None if !cfDef.optional =>
                    List(ValidationError.missingField(path, cfName))
                  case None =>
                    List.empty
                }
              }.toList

              // Validate variant-specific fields (without checking for extra fields)
              val variantErrors = variantType.fields.flatMap { case (vfName, vfDef) =>
                jsonFieldMap.get(vfName) match {
                  case Some(value) =>
                    validateType(vfDef.fieldType, value, s"$path.$vfName")
                  case None if !vfDef.optional =>
                    List(ValidationError.missingField(path, vfName))
                  case None =>
                    List.empty
                }
              }.toList

              // Check for extra fields not in discriminator, common fields, or variant fields
              val allowedFields = Set(fieldName) ++ commonFields.keySet ++ variantType.fields.keySet
              val extraFields = jsonFieldMap.keySet.diff(allowedFields).toList.map { extraField =>
                ValidationError(
                  s"$path.$extraField",
                  s"Unexpected field '$extraField' not defined in template",
                  s"one of: ${allowedFields.mkString(", ")}",
                  extraField
                )
              }

              commonErrors ++ variantErrors ++ extraFields

            case None =>
              List(ValidationError.invalidDiscriminator(path, fieldName, variants.keySet, discriminatorValue))
          }

        case Some(_) =>
          List(ValidationError(path, s"Discriminator field '$fieldName' must be a string", "string", "non-string"))

        case None =>
          List(ValidationError.missingField(path, fieldName))
      }

    } else {
      List(ValidationError.typeMismatch(path, "object", json.getNodeType.toString))
    }
  }
}

object Validator {
  /**
   * Creates a validator from a template
   */
  def apply(template: Template): Validator = new Validator(template)
}

/**
 * Validates JSON data against multi-template definitions with reference resolution
 */
class MultiValidator(multiTemplate: MultiTemplate) {
  
  private val mapper = new ObjectMapper()
  
  /**
   * Validates a JSON string against a specific definition in the multi-template
   *
   * @param jsonString The JSON string to validate
   * @param definitionName The name of the definition to validate against (defaults to mainClass)
   * @return Either a list of validation errors or the parsed JSON
   */
  def validate(jsonString: String, definitionName: String = multiTemplate.mainClass): Either[List[ValidationError], JsonNode] = {
    try {
      val json = mapper.readTree(jsonString)
      validateJson(json, definitionName)
    } catch {
      case e: Exception =>
        Left(List(ValidationError("root", s"Invalid JSON: ${e.getMessage}", "valid JSON", jsonString)))
    }
  }
  
  /**
   * Validates a parsed JSON value against a specific definition
   */
  def validateJson(json: JsonNode, definitionName: String = multiTemplate.mainClass): Either[List[ValidationError], JsonNode] = {
    multiTemplate.getDefinition(definitionName) match {
      case Some(definition) =>
        validateTypeWithRefs(definition.templateType, json, "root", multiTemplate.definitionsMap) match {
          case Nil => Right(json)
          case errors => Left(errors)
        }
      case None =>
        Left(List(ValidationError("root", s"Definition '$definitionName' not found", "valid definition name", definitionName)))
    }
  }
  
  /**
   * Validates a type against JSON with reference resolution
   */
  private def validateTypeWithRefs(
    templateType: TemplateType,
    json: JsonNode,
    path: String,
    definitions: Map[String, TemplateDefinition]
  ): List[ValidationError] = {
    templateType match {
      case StringType(constraints) => validateString(json, path, constraints)
      case NumberType(constraints) => validateNumber(json, path, constraints)
      case BooleanType() => validateBoolean(json, path)
      case ArrayType(elementType) => validateArray(elementType, json, path, definitions)
      case ObjectType(fields) => validateObject(fields, json, path, definitions)
      case TypeDiscriminator(fieldName, variants, commonFields, _, _) =>
        validateDiscriminator(fieldName, variants, commonFields, json, path, definitions)
      case ReferenceType(typeName) =>
        // Resolve reference and validate
        definitions.get(typeName) match {
          case Some(refDef) =>
            validateTypeWithRefs(refDef.templateType, json, path, definitions)
          case None =>
            List(ValidationError(path, s"Unresolved reference: $typeName", "valid definition", typeName))
        }
      case RecursiveRef(typeName) =>
        // For backward compatibility - treat like ReferenceType
        definitions.get(typeName) match {
          case Some(refDef) =>
            validateTypeWithRefs(refDef.templateType, json, path, definitions)
          case None =>
            List.empty // Can't validate recursive refs without definition
        }
    }
  }
  
  // Validation helper methods (similar to Validator but with definitions parameter)
  
  private def validateString(json: JsonNode, path: String, constraints: List[Constraint]): List[ValidationError] = {
    if (json.isTextual) {
      val value = json.asText()
      constraints.flatMap {
        case MinLength(len) if value.length < len =>
          Some(ValidationError.constraintViolation(path, s"minLength: $len", s"length ${value.length}"))
        case MaxLength(len) if value.length > len =>
          Some(ValidationError.constraintViolation(path, s"maxLength: $len", s"length ${value.length}"))
        case Pattern(regex) if !value.matches(regex) =>
          Some(ValidationError.constraintViolation(path, s"pattern: $regex", value))
        case Format(fmt) => validateFormat(fmt, value, path)
        case _ => None
      }
    } else {
      List(ValidationError.typeMismatch(path, "string", json.getNodeType.toString))
    }
  }
  
  private def validateFormat(format: String, value: String, path: String): Option[ValidationError] = {
    format match {
      case "email" if !value.matches("""^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""") =>
        Some(ValidationError.constraintViolation(path, "format: email", value))
      case "url" if !value.matches("""^https?://.*""") =>
        Some(ValidationError.constraintViolation(path, "format: url", value))
      case "uuid" if !value.matches("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""") =>
        Some(ValidationError.constraintViolation(path, "format: uuid", value))
      case "iso8601" if !value.matches("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$""") =>
        Some(ValidationError.constraintViolation(path, "format: iso8601", value))
      case "alphaNoWhitespace" if !value.matches("""^[a-zA-Z]+$""") =>
        Some(ValidationError.constraintViolation(path, "format: alphaNoWhitespace", value))
      case "majorAndMinor" if !value.matches("""^[0-9]+\.[0-9]+$""") =>
        Some(ValidationError.constraintViolation(path, "format: majorAndMinor", value))
      case _ => None
    }
  }
  
  private def validateNumber(json: JsonNode, path: String, constraints: List[Constraint]): List[ValidationError] = {
    if (json.isNumber) {
      validateNumberConstraints(json.asDouble(), path, constraints)
    } else {
      List(ValidationError.typeMismatch(path, "number", json.getNodeType.toString))
    }
  }
  
  private def validateNumberConstraints(value: Double, path: String, constraints: List[Constraint]): List[ValidationError] = {
    constraints.flatMap {
      case Min(min) if value < min =>
        Some(ValidationError.constraintViolation(path, s"min: $min", value.toString))
      case Max(max) if value > max =>
        Some(ValidationError.constraintViolation(path, s"max: $max", value.toString))
      case MultipleOf(mult) if value % mult != 0 =>
        Some(ValidationError.constraintViolation(path, s"multipleOf: $mult", value.toString))
      case _ => None
    }
  }
  
  private def validateBoolean(json: JsonNode, path: String): List[ValidationError] = {
    if (json.isBoolean) {
      List.empty
    } else {
      List(ValidationError.typeMismatch(path, "boolean", json.getNodeType.toString))
    }
  }
  
  private def validateArray(
    elementType: TemplateType,
    json: JsonNode,
    path: String,
    definitions: Map[String, TemplateDefinition]
  ): List[ValidationError] = {
    if (json.isArray) {
      json.elements().asScala.toList.zipWithIndex.flatMap { case (element, idx) =>
        validateTypeWithRefs(elementType, element, s"$path[$idx]", definitions)
      }
    } else {
      List(ValidationError.typeMismatch(path, "array", json.getNodeType.toString))
    }
  }
  
  private def validateObject(
    fields: Map[String, FieldDef],
    json: JsonNode,
    path: String,
    definitions: Map[String, TemplateDefinition]
  ): List[ValidationError] = {
    if (json.isObject) {
      val jsonFieldMap = json.fields().asScala.toList.map(entry => entry.getKey -> entry.getValue).toMap
      
      // Check required fields
      val missingFields = fields.collect {
        case (fieldName, fieldDef) if !fieldDef.optional && !jsonFieldMap.contains(fieldName) =>
          ValidationError.missingField(path, fieldName)
      }.toList
      
      // Validate present fields
      val fieldErrors = fields.flatMap { case (fieldName, fieldDef) =>
        jsonFieldMap.get(fieldName) match {
          case Some(value) =>
            validateTypeWithRefs(fieldDef.fieldType, value, s"$path.$fieldName", definitions)
          case None if fieldDef.optional =>
            List.empty
          case None =>
            List.empty // Already handled in missingFields
        }
      }.toList
      
      // Check for extra fields not in template
      val extraFields = jsonFieldMap.keySet.diff(fields.keySet).toList.map { extraField =>
        ValidationError(
          s"$path.$extraField",
          s"Unexpected field '$extraField' not defined in template",
          s"one of: ${fields.keySet.mkString(", ")}",
          extraField
        )
      }
      
      missingFields ++ fieldErrors ++ extraFields
    
    } else {
      List(ValidationError.typeMismatch(path, "object", json.getNodeType.toString))
    }
  }
  
  private def validateDiscriminator(
    fieldName: String,
    variants: Map[String, ObjectType],
    commonFields: Map[String, FieldDef],
    json: JsonNode,
    path: String,
    definitions: Map[String, TemplateDefinition]
  ): List[ValidationError] = {
    if (json.isObject) {
      val jsonFieldMap = json.fields().asScala.toList.map(entry => entry.getKey -> entry.getValue).toMap
      
      // Get discriminator value
      jsonFieldMap.get(fieldName) match {
        case Some(discriminatorNode) if discriminatorNode.isTextual =>
          val discriminatorValue = discriminatorNode.asText()
          variants.get(discriminatorValue) match {
            case Some(variantType) =>
              // Validate common fields
              val commonErrors = commonFields.flatMap { case (cfName, cfDef) =>
                jsonFieldMap.get(cfName) match {
                  case Some(value) =>
                    validateTypeWithRefs(cfDef.fieldType, value, s"$path.$cfName", definitions)
                  case None if !cfDef.optional =>
                    List(ValidationError.missingField(path, cfName))
                  case None =>
                    List.empty
                }
              }.toList
              
              // Validate variant-specific fields
              val variantErrors = variantType.fields.flatMap { case (vfName, vfDef) =>
                jsonFieldMap.get(vfName) match {
                  case Some(value) =>
                    validateTypeWithRefs(vfDef.fieldType, value, s"$path.$vfName", definitions)
                  case None if !vfDef.optional =>
                    List(ValidationError.missingField(path, vfName))
                  case None =>
                    List.empty
                }
              }.toList
              
              // Check for extra fields
              val allowedFields = Set(fieldName) ++ commonFields.keySet ++ variantType.fields.keySet
              val extraFields = jsonFieldMap.keySet.diff(allowedFields).toList.map { extraField =>
                ValidationError(
                  s"$path.$extraField",
                  s"Unexpected field '$extraField' not defined in template",
                  s"one of: ${allowedFields.mkString(", ")}",
                  extraField
                )
              }
              
              commonErrors ++ variantErrors ++ extraFields
            
            case None =>
              List(ValidationError.invalidDiscriminator(path, fieldName, variants.keySet, discriminatorValue))
          }
        
        case Some(_) =>
          List(ValidationError(path, s"Discriminator field '$fieldName' must be a string", "string", "non-string"))
        
        case None =>
          List(ValidationError.missingField(path, fieldName))
      }
    
    } else {
      List(ValidationError.typeMismatch(path, "object", json.getNodeType.toString))
    }
  }
}

object MultiValidator {
  /**
   * Creates a validator from a multi-template
   */
  def apply(multiTemplate: MultiTemplate): MultiValidator = new MultiValidator(multiTemplate)
}