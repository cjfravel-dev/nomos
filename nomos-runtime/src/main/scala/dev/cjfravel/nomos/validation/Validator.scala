package dev.cjfravel.nomos.validation

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.json.{Json, JsonValue, JsonNull, JsonNumber}

/**
 * Validates JSON data against multi-template definitions with reference resolution
 */
class MultiValidator(multiTemplate: MultiTemplate) {

  /** The JSON type name used in error messages (string, number, object, ...). */
  private def jsonType(json: JsonValue): String = json.typeName

  /**
   * Validates a JSON string against a specific definition in the multi-template
   *
   * @param jsonString The JSON string to validate
   * @param definitionName The name of the definition to validate against
   * @return Either a list of validation errors or the parsed JSON
   */
  def validate(jsonString: String, definitionName: String): Either[List[ValidationError], JsonValue] = {
    Json.parse(jsonString) match {
      case Right(json) => validateJson(json, definitionName)
      case Left(msg) =>
        Left(List(ValidationError("root", s"Invalid JSON: $msg", "valid JSON", jsonString)))
    }
  }

  /**
   * Validates a parsed JSON value against a specific definition
   */
  def validateJson(json: JsonValue, definitionName: String): Either[List[ValidationError], JsonValue] = {
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
    json: JsonValue,
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
      case DateType() => validateTemporal(json, path, "date", s => java.time.LocalDate.parse(s))
      case DateTimeType() => validateTemporal(json, path, "datetime", s => parseFlexibleDateTime(s))
      case ArrayType(elementType, constraints) => validateArray(elementType, json, path, definitions, constraints)
      case MapType(valueType) => validateMap(valueType, json, path, definitions)
      case UnionType(types) =>
        if (types.exists(t => validateTypeWithRefs(t, json, path, definitions).isEmpty)) List.empty
        else List(ValidationError.typeMismatch(path, "one of union types", jsonType(json)))
      case ObjectType(fields, additional) => validateObject(fields, json, path, definitions, additional)
      case TypeDiscriminator(fieldName, variants, commonFields, _, _, variantMatch, _, fallbackVariant, _) =>
        validateDiscriminator(fieldName, variants, commonFields, json, path, definitions, variantMatch, fallbackVariant.isDefined)
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
      case ExternalType(_, _) =>
        List.empty
      case EnumType(_, values) =>
        json.asString match {
          case Some(s) if values.contains(s) => List.empty
          case Some(s) => List(ValidationError.constraintViolation(path, s"enum: ${values.mkString(", ")}", s))
          case None => List(ValidationError.constraintViolation(path, s"enum: ${values.mkString(", ")}", jsonType(json)))
        }
    }
  }
  
  // Validation helper methods (similar to Validator but with definitions parameter)
  
  private def validateString(json: JsonValue, path: String, constraints: List[Constraint]): List[ValidationError] = {
    json.asString match {
      case Some(value) =>
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
      case None =>
        List(ValidationError.typeMismatch(path, "string", jsonType(json)))
    }
  }
  
  private def validateFormat(format: String, value: String, path: String): Option[ValidationError] = {
    if (FormatRegistry.validate(format, value)) None
    else Some(ValidationError.constraintViolation(path, s"format: $format", value))
  }
  
  private def validateNumber(json: JsonValue, path: String, constraints: List[Constraint]): List[ValidationError] = {
    json.asNumber match {
      case Some(n) => validateNumberConstraints(n, path, constraints)
      case None => List(ValidationError.typeMismatch(path, "number", jsonType(json)))
    }
  }
  
  private def validateWholeNumber(json: JsonValue, path: String, expected: String, constraints: List[Constraint]): List[ValidationError] = {
    json.asNumber match {
      case None =>
        List(ValidationError.typeMismatch(path, expected, jsonType(json)))
      case Some(n) if !n.isIntegral =>
        List(ValidationError.typeMismatch(path, expected, "fractional number"))
      case Some(n) =>
        val inRange = expected match {
          case "int" => n.fitsInt
          case "long" => n.fitsLong
          case _ => true
        }
        if (!inRange) {
          List(ValidationError.constraintViolation(path, s"$expected range", n.raw))
        } else {
          validateNumberConstraints(n, path, constraints)
        }
    }
  }
  
  // Numeric constraints are evaluated in BigDecimal so precise decimals validate exactly; in
  // Double, `0.3 % 0.1` is nonzero and would falsely reject valid tenths/cents. Constraint bounds
  // are stored as Double, so they are lifted to BigDecimal via their canonical string form.
  private def validateNumberConstraints(n: JsonNumber, path: String, constraints: List[Constraint]): List[ValidationError] = {
    n.asBigDecimalOption match {
      case None => List.empty // magnitude beyond BigDecimal's range; constraints cannot be evaluated exactly
      case Some(value) =>
        constraints.flatMap {
          case Min(min) if value < BigDecimal(min.toString) =>
            Some(ValidationError.constraintViolation(path, s"min: $min", n.raw))
          case Max(max) if value > BigDecimal(max.toString) =>
            Some(ValidationError.constraintViolation(path, s"max: $max", n.raw))
          case MultipleOf(mult) if !isMultipleOf(value, BigDecimal(mult.toString)) =>
            Some(ValidationError.constraintViolation(path, s"multipleOf: $mult", n.raw))
          case _ => None
        }
    }
  }

  private def isMultipleOf(value: BigDecimal, mult: BigDecimal): Boolean =
    if (mult.signum == 0) value.signum == 0
    else value.remainder(mult).signum == 0
  
  private def validateBoolean(json: JsonValue, path: String): List[ValidationError] = {
    json.asBoolean match {
      case Some(_) => List.empty
      case None => List(ValidationError.typeMismatch(path, "boolean", jsonType(json)))
    }
  }
  
  private def validateTemporal(json: JsonValue, path: String, expected: String, parse: String => Any): List[ValidationError] = {
    json.asString match {
      case None =>
        List(ValidationError.typeMismatch(path, expected, jsonType(json)))
      case Some(value) =>
        try { parse(value); List.empty }
        catch { case _: Exception => List(ValidationError.constraintViolation(path, s"format: $expected", value)) }
    }
  }

  /**
   * Accepts the common ISO-8601 datetime forms so validation agrees with the generated codecs and
   * with real data: UTC instants (trailing `Z`), explicit offsets, and naive local date-times, all
   * with optional fractional seconds. Throws if none parse (handled by validateTemporal).
   */
  private def parseFlexibleDateTime(s: String): Unit = {
    try { java.time.OffsetDateTime.parse(s); () }
    catch { case _: Exception => java.time.LocalDateTime.parse(s); () }
  }
  
  private def validateArray(
    elementType: TemplateType,
    json: JsonValue,
    path: String,
    definitions: Map[String, TemplateDefinition],
    constraints: List[Constraint] = List.empty
  ): List[ValidationError] = {
    json.asArray match {
      case Some(array) =>
        val elements = array.values.toList
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
      case None =>
        List(ValidationError.typeMismatch(path, "array", jsonType(json)))
    }
  }
  
  private def validateMap(
    valueType: TemplateType,
    json: JsonValue,
    path: String,
    definitions: Map[String, TemplateDefinition]
  ): List[ValidationError] = {
    json.asObject match {
      case Some(obj) =>
        obj.fields.toList.flatMap { case (key, value) =>
          validateTypeWithRefs(valueType, value, s"$path.$key", definitions)
        }
      case None =>
        List(ValidationError.typeMismatch(path, "object", jsonType(json)))
    }
  }
  
  private def validateObject(
    fields: Map[String, FieldDef],
    json: JsonValue,
    path: String,
    definitions: Map[String, TemplateDefinition],
    additional: AdditionalProperties = ForbidExtra
  ): List[ValidationError] = {
    json.asObject match {
      case Some(obj) =>
        val jsonFieldMap = obj.fieldMap
        
        // Check required fields
        val missingFields = fields.collect {
          case (fieldName, fieldDef) if fieldDef.required && !jsonFieldMap.contains(fieldName) =>
            ValidationError.missingField(path, fieldName)
        }.toList
        
        // Validate present fields
        val fieldErrors = fields.flatMap { case (fieldName, fieldDef) =>
          jsonFieldMap.get(fieldName) match {
            case Some(JsonNull) if fieldDef.acceptsNull =>
              List.empty // present null is accepted for optional/nullable/defaulted fields
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
      
      case None =>
        List(ValidationError.typeMismatch(path, "object", jsonType(json)))
    }
  }
  
  private def validateDiscriminator(
    fieldName: String,
    variants: Map[String, ObjectType],
    commonFields: Map[String, FieldDef],
    json: JsonValue,
    path: String,
    definitions: Map[String, TemplateDefinition],
    variantMatch: String = "exact",
    hasFallback: Boolean = false
  ): List[ValidationError] = {
    json.asObject match {
      case Some(obj) =>
        val jsonFieldMap = obj.fieldMap
        
        // Get discriminator value
        jsonFieldMap.get(fieldName) match {
          case Some(discriminatorNode) if discriminatorNode.isString =>
            val discriminatorValue = discriminatorNode.asString.get
            val matched = variants.get(discriminatorValue).orElse {
              if (variantMatch == "prefix") variants.find { case (k, _) => discriminatorValue.startsWith(k) }.map(_._2)
              else None
            }
            matched match {
              case Some(variantType) =>
                // Validate common fields
                val commonErrors = commonFields.flatMap { case (cfName, cfDef) =>
                  jsonFieldMap.get(cfName) match {
                    case Some(JsonNull) if cfDef.acceptsNull =>
                      List.empty
                    case Some(value) =>
                      validateTypeWithRefs(cfDef.fieldType, value, s"$path.$cfName", definitions)
                    case None if cfDef.required =>
                      List(ValidationError.missingField(path, cfName))
                    case None =>
                      List.empty
                  }
                }.toList
                
                // Validate variant-specific fields
                val variantErrors = variantType.fields.flatMap { case (vfName, vfDef) =>
                  jsonFieldMap.get(vfName) match {
                    case Some(JsonNull) if vfDef.acceptsNull =>
                      List.empty
                    case Some(value) =>
                      validateTypeWithRefs(vfDef.fieldType, value, s"$path.$vfName", definitions)
                    case None if vfDef.required =>
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
                if (hasFallback) {
                  // Unrecognized variant decodes into the fallback case class (preserving the raw
                  // payload). Common fields are shared by every variant, so still enforce them;
                  // the unknown variant-specific shape is intentionally not validated.
                  commonFields.flatMap { case (cfName, cfDef) =>
                    jsonFieldMap.get(cfName) match {
                      case Some(JsonNull) if cfDef.acceptsNull =>
                        List.empty
                      case Some(value) =>
                        validateTypeWithRefs(cfDef.fieldType, value, s"$path.$cfName", definitions)
                      case None if cfDef.required =>
                        List(ValidationError.missingField(path, cfName))
                      case None =>
                        List.empty
                    }
                  }.toList
                } else {
                  List(ValidationError.invalidDiscriminator(path, fieldName, variants.keySet, discriminatorValue))
                }
            }
          
          case Some(_) =>
            List(ValidationError(path, s"Discriminator field '$fieldName' must be a string", "string", "non-string"))
          
          case None =>
            List(ValidationError.missingField(path, fieldName))
        }
      
      case None =>
        List(ValidationError.typeMismatch(path, "object", jsonType(json)))
    }
  }
}

object MultiValidator {
  /**
   * Creates a validator from a multi-template
   */
  def apply(multiTemplate: MultiTemplate): MultiValidator = new MultiValidator(multiTemplate)
}