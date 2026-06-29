package dev.cjfravel.nomos.validation

import dev.cjfravel.nomos.model._
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import scala.collection.JavaConverters._

/**
 * Validates JSON data against multi-template definitions with reference resolution
 */
class MultiValidator(multiTemplate: MultiTemplate) {
  
  private val mapper = new ObjectMapper()
  
  /**
   * Validates a JSON string against a specific definition in the multi-template
   *
   * @param jsonString The JSON string to validate
   * @param definitionName The name of the definition to validate against
   * @return Either a list of validation errors or the parsed JSON
   */
  def validate(jsonString: String, definitionName: String): Either[List[ValidationError], JsonNode] = {
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
  def validateJson(json: JsonNode, definitionName: String): Either[List[ValidationError], JsonNode] = {
    multiTemplate.getDefinition(definitionName) match {
      case Some(definition) =>
        validateTypeWithRefs(definition.templateType, json, "root", multiTemplate.definitionsMap) match {
          case Nil =>
            definition.validators.flatMap(name => ValidatorRegistry.run(name, json)) match {
              case Nil => Right(json)
              case customErrors => Left(customErrors)
            }
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
      case IntType(constraints) => validateWholeNumber(json, path, "int", constraints)
      case LongType(constraints) => validateWholeNumber(json, path, "long", constraints)
      case DecimalType(constraints) => validateNumber(json, path, constraints)
      case BooleanType() => validateBoolean(json, path)
      case ArrayType(elementType, constraints) => validateArray(elementType, json, path, definitions, constraints)
      case MapType(valueType) => validateMap(valueType, json, path, definitions)
      case UnionType(types) =>
        if (types.exists(t => validateTypeWithRefs(t, json, path, definitions).isEmpty)) List.empty
        else List(ValidationError.typeMismatch(path, "one of union types", json.getNodeType.toString))
      case ObjectType(fields, additional) => validateObject(fields, json, path, definitions, additional)
      case TypeDiscriminator(fieldName, variants, commonFields, _, _, variantMatch, _) =>
        validateDiscriminator(fieldName, variants, commonFields, json, path, definitions, variantMatch)
      case ReferenceType(typeName) =>
        // Resolve reference and validate
        definitions.get(typeName) match {
          case Some(refDef) =>
            validateTypeWithRefs(refDef.templateType, json, path, definitions)
          case None =>
            List(ValidationError(path, s"Unresolved reference: $typeName", "valid definition", typeName))
        }
      case RecursiveRef(typeName) =>
        definitions.get(typeName) match {
          case Some(refDef) =>
            validateTypeWithRefs(refDef.templateType, json, path, definitions)
          case None =>
            List.empty
        }
      case ExternalType(_) =>
        List.empty
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
        case Enum(values) if !values.contains(value) =>
          Some(ValidationError.constraintViolation(path, s"enum: ${values.mkString(", ")}", value))
        case _ => None
      }
    } else {
      List(ValidationError.typeMismatch(path, "string", json.getNodeType.toString))
    }
  }
  
  private def validateFormat(format: String, value: String, path: String): Option[ValidationError] = {
    if (FormatRegistry.validate(format, value)) None
    else Some(ValidationError.constraintViolation(path, s"format: $format", value))
  }
  
  private def validateNumber(json: JsonNode, path: String, constraints: List[Constraint]): List[ValidationError] = {
    if (json.isNumber) {
      validateNumberConstraints(json.asDouble(), path, constraints)
    } else {
      List(ValidationError.typeMismatch(path, "number", json.getNodeType.toString))
    }
  }
  
  private def validateWholeNumber(json: JsonNode, path: String, expected: String, constraints: List[Constraint]): List[ValidationError] = {
    if (!json.isNumber) {
      List(ValidationError.typeMismatch(path, expected, json.getNodeType.toString))
    } else if (json.asDouble() % 1 != 0) {
      List(ValidationError.typeMismatch(path, expected, "fractional number"))
    } else {
      validateNumberConstraints(json.asDouble(), path, constraints)
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
    definitions: Map[String, TemplateDefinition],
    constraints: List[Constraint] = List.empty
  ): List[ValidationError] = {
    if (json.isArray) {
      val elements = json.elements().asScala.toList
      val itemErrors = elements.zipWithIndex.flatMap { case (element, idx) =>
        validateTypeWithRefs(elementType, element, s"$path[$idx]", definitions)
      }
      val countErrors = constraints.flatMap {
        case MinItems(n) if elements.size < n =>
          Some(ValidationError.constraintViolation(path, s"minItems: $n", elements.size.toString))
        case MaxItems(n) if elements.size > n =>
          Some(ValidationError.constraintViolation(path, s"maxItems: $n", elements.size.toString))
        case UniqueItems(true) if elements.distinct.size != elements.size =>
          Some(ValidationError.constraintViolation(path, "uniqueItems", "duplicates"))
        case _ => None
      }
      itemErrors ++ countErrors
    } else {
      List(ValidationError.typeMismatch(path, "array", json.getNodeType.toString))
    }
  }
  
  private def validateMap(
    valueType: TemplateType,
    json: JsonNode,
    path: String,
    definitions: Map[String, TemplateDefinition]
  ): List[ValidationError] = {
    if (json.isObject) {
      json.fields().asScala.toList.flatMap { entry =>
        validateTypeWithRefs(valueType, entry.getValue, s"$path.${entry.getKey}", definitions)
      }
    } else {
      List(ValidationError.typeMismatch(path, "object", json.getNodeType.toString))
    }
  }
  
  private def validateObject(
    fields: Map[String, FieldDef],
    json: JsonNode,
    path: String,
    definitions: Map[String, TemplateDefinition],
    additional: AdditionalProperties = ForbidExtra
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
      val extraKeys = jsonFieldMap.keySet.diff(fields.keySet).toList
      val extraFields = additional match {
        case AllowExtra => List.empty
        case TypedExtra(t) => extraKeys.flatMap(k => validateTypeWithRefs(t, jsonFieldMap(k), s"$path.$k", definitions))
        case ForbidExtra => extraKeys.map { extraField =>
          ValidationError(
            s"$path.$extraField",
            s"Unexpected field '$extraField' not defined in template",
            s"one of: ${fields.keySet.mkString(", ")}",
            extraField
          )
        }
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
    definitions: Map[String, TemplateDefinition],
    variantMatch: String = "exact"
  ): List[ValidationError] = {
    if (json.isObject) {
      val jsonFieldMap = json.fields().asScala.toList.map(entry => entry.getKey -> entry.getValue).toMap
      
      // Get discriminator value
      jsonFieldMap.get(fieldName) match {
        case Some(discriminatorNode) if discriminatorNode.isTextual =>
          val discriminatorValue = discriminatorNode.asText()
          val matched = variants.get(discriminatorValue).orElse {
            if (variantMatch == "prefix") variants.find { case (k, _) => discriminatorValue.startsWith(k) }.map(_._2)
            else None
          }
          matched match {
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