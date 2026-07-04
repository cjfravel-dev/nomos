package dev.cjfravel.nomos.validation

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.json.{Json, JsonValue, JsonNull, JsonNumber}

/**
 * Validates JSON data against multi-template definitions with reference resolution
 */
class MultiValidator(multiTemplate: MultiTemplate) {

  private val basePackage = multiTemplate.basePackage

  // Definitions grouped by simple name (same-named types in different packages are kept, not
  // collapsed). References resolve with the referrer's package context, mirroring the generator's
  // imports: prefer a same-package definition, else the single candidate if unambiguous.
  private val definitionsBySimpleName: Map[String, List[TemplateDefinition]] =
    multiTemplate.definitions.groupBy(_.name)

  private def resolveRef(name: String, fromPackage: String): Option[TemplateDefinition] =
    if (name.contains('.')) multiTemplate.definitions.find(d => multiTemplate.fqn(d) == name)
    else {
      val candidates = definitionsBySimpleName.getOrElse(name, Nil)
      candidates.find(_.fullPackage(basePackage) == fromPackage)
        .orElse(if (candidates.lengthCompare(1) == 0) candidates.headOption else None)
    }

  // Whether a template type's subtree can reach any custom validator, given a snapshot of
  // per-definition reachability. Walks the (small, static) type structure, resolving references
  // against `state`; used both to compute reachability and, once final, to prune the phase-two walk.
  private def typeReaches(tt: TemplateType, pkg: String, state: Map[String, Boolean]): Boolean = tt match {
    case ObjectType(fields, additional) =>
      fields.values.exists(f => typeReaches(f.fieldType, pkg, state)) ||
        (additional match { case TypedExtra(t) => typeReaches(t, pkg, state); case _ => false })
    case ArrayType(e, _) => typeReaches(e, pkg, state)
    case MapType(v) => typeReaches(v, pkg, state)
    case UnionType(ts) => ts.exists(t => typeReaches(t, pkg, state))
    case TypeDiscriminator(_, variants, commonFields, _, _, _, _, _, _) =>
      commonFields.values.exists(f => typeReaches(f.fieldType, pkg, state)) ||
        variants.values.exists(v => typeReaches(v, pkg, state))
    case ReferenceType(n) => resolveRef(n, pkg).exists(rd => state.getOrElse(multiTemplate.fqn(rd), false))
    case RecursiveRef(n) => resolveRef(n, pkg).exists(rd => state.getOrElse(multiTemplate.fqn(rd), false))
    case _ => false
  }

  // Per-definition (by FQN): does the definition declare validators, or reach one through its type's
  // references? Computed once via a fixpoint so it terminates on reference cycles. The phase-two
  // custom-validator walk is skipped/pruned for definitions and subtrees that reach no validator, so
  // a validator-free template pays no second traversal.
  private val defReaches: Map[String, Boolean] = {
    var state = multiTemplate.definitions.map(d => multiTemplate.fqn(d) -> d.validators.nonEmpty).toMap
    var changed = true
    while (changed) {
      changed = false
      multiTemplate.definitions.foreach { d =>
        val fqn = multiTemplate.fqn(d)
        if (!state(fqn) && typeReaches(d.templateType, d.fullPackage(basePackage), state)) {
          state = state.updated(fqn, true)
          changed = true
        }
      }
    }
    state
  }

  /** Whether the phase-two custom-validator traversal does anything for `definitionName` — i.e. its
   *  reachable subtree declares any validators. Package-private for tests. */
  private[nomos] def reachesAnyValidator(definitionName: String): Boolean =
    multiTemplate.getDefinition(definitionName).exists(d => defReaches.getOrElse(multiTemplate.fqn(d), false))

  // Date/datetime validation must accept exactly what the generated decoder accepts, so it parses
  // with the same type the codec uses (from the embedded dateType/dateTimeType). java.util.Date
  // bridges through java.time (LocalDate for dates, Instant for datetimes) exactly as the codec
  // does; every other (java.time.*) type is parsed via its static parse. This keeps validate and
  // fromJson in agreement (e.g. a trailing-Z value is rejected for LocalDateTime, as the codec does).
  private def temporalParser(typeName: String, isDate: Boolean): String => Unit = {
    if (typeName == "java.util.Date") {
      if (isDate) (s: String) => { java.time.LocalDate.parse(s); () }
      else (s: String) => { java.time.Instant.parse(s); () }
    } else {
      try {
        val method = Class.forName(typeName).getMethod("parse", classOf[CharSequence])
        (s: String) => { method.invoke(null, s); () }
      } catch {
        case _: Throwable =>
          if (isDate) (s: String) => { java.time.LocalDate.parse(s); () }
          else (s: String) => { java.time.LocalDateTime.parse(s); () }
      }
    }
  }

  private val dateParse: String => Unit = temporalParser(multiTemplate.dateType, isDate = true)
  private val dateTimeParse: String => Unit = temporalParser(multiTemplate.dateTimeType, isDate = false)

  // Upper bound on validation recursion so adversarial/cyclic templates (e.g. a reference cycle
  // that consumes no payload) fail with an error instead of a StackOverflowError. Well above the
  // parser's input-nesting cap so it never trips on valid input.
  private val MaxValidationDepth = 4096

  // Constraint patterns are compiled once and reused; String.matches recompiles the regex on every
  // value (slow for array-heavy input and a template-authored ReDoS surface).
  private val compiledPatterns = scala.collection.concurrent.TrieMap.empty[String, java.util.regex.Pattern]
  private def patternFor(regex: String): java.util.regex.Pattern =
    compiledPatterns.getOrElseUpdate(regex, java.util.regex.Pattern.compile(regex))

  /** The JSON type name used in error messages (string, number, object, ...). */
  private def jsonType(json: JsonValue): String = json.typeName

  /**
   * Validates a JSON string against a specific definition in the multi-template.
   *
   * Structural fields are validated against the template; external-typed fields (`$gen:`/`$extern:`)
   * are not schema-validated here — their validation is delegated to the referenced type's generated
   * decode or the application-registered codec.
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
   * Validates a parsed JSON value against a specific definition.
   *
   * Validation runs in two phases. Phase one checks structure (types, constraints, references);
   * this recursion is reference-tail-optimized and depth-bounded, so cyclic schemas fail with an
   * error rather than a StackOverflowError. Phase two runs custom validators, and only if phase one
   * found no structural errors anywhere in the document — so validators never see structurally
   * invalid data. A definition's validators run wherever the definition appears (top level and at
   * every `$ref`), each receiving a [[ValidatorContext]] with the node at that level, the document
   * root, and the JSON path.
   */
  def validateJson(json: JsonValue, definitionName: String): Either[List[ValidationError], JsonValue] = {
    multiTemplate.getDefinition(definitionName) match {
      case Some(definition) =>
        val pkg = definition.fullPackage(multiTemplate.basePackage)
        validateTypeWithRefs(definition.templateType, json, "root", pkg, 0) match {
          case Nil =>
            val topLevel = definition.validators.flatMap(name => ValidatorRegistry.run(name, ValidatorContext(json, json, "root")))
            topLevel ++ collectValidatorErrors(definition.templateType, json, json, "root", pkg, 0) match {
              case Nil => Right(json)
              case customErrors => Left(customErrors)
            }
          case errors => Left(errors)
        }
      case None =>
        Left(List(ValidationError("root", s"Definition '$definitionName' not found", "valid definition name", definitionName)))
    }
  }

  // Phase two: runs custom validators over an already structurally-valid document. Because phase one
  // validated the structure (bounding cycles via the depth guard), this walk terminates on the same
  // finite structure; it descends only through containers and references, running each referenced
  // definition's validators with a ValidatorContext. `root` is passed explicitly (not held in state)
  // so a validator that re-enters validation cannot disturb an in-progress walk.
  private def collectValidatorErrors(
    templateType: TemplateType,
    json: JsonValue,
    root: JsonValue,
    path: String,
    currentPackage: String,
    depth: Int
  ): List[ValidationError] = {
    if (depth > MaxValidationDepth) return Nil
    // Prune: don't descend into a subtree whose type reaches no validator (this also short-circuits
    // the whole walk for a validator-free document and skips the union re-validation below).
    if (!typeReaches(templateType, currentPackage, defReaches)) return Nil
    templateType match {
      case ObjectType(fields, additional) =>
        json.asObject match {
          case Some(obj) =>
            val fieldErrors = fields.toList.flatMap { case (fieldName, fieldDef) =>
              obj.field(fieldName) match {
                case Some(JsonNull) => Nil
                case Some(value) => collectValidatorErrors(fieldDef.fieldType, value, root, s"$path.$fieldName", currentPackage, depth + 1)
                case None => Nil
              }
            }
            val extraErrors = additional match {
              case TypedExtra(t) => obj.keys.filterNot(fields.contains).toList.flatMap { k =>
                obj.field(k).toList.flatMap(v => collectValidatorErrors(t, v, root, s"$path.$k", currentPackage, depth + 1))
              }
              case _ => Nil
            }
            fieldErrors ++ extraErrors
          case None => Nil
        }
      case ArrayType(elementType, _) =>
        json.asArray match {
          case Some(array) => array.values.toList.zipWithIndex.flatMap { case (element, idx) =>
            collectValidatorErrors(elementType, element, root, s"$path[$idx]", currentPackage, depth + 1)
          }
          case None => Nil
        }
      case MapType(valueType) =>
        json.asObject match {
          case Some(obj) => obj.fields.toList.flatMap { case (key, value) =>
            collectValidatorErrors(valueType, value, root, s"$path.$key", currentPackage, depth + 1)
          }
          case None => Nil
        }
      case UnionType(types) =>
        // Run validators only for the branch that structurally matched (phase one guaranteed one
        // does), so validators never fire on a discarded branch.
        types.find(t => validateTypeWithRefs(t, json, path, currentPackage, depth + 1).isEmpty)
          .toList.flatMap(t => collectValidatorErrors(t, json, root, path, currentPackage, depth + 1))
      case TypeDiscriminator(fieldName, variants, commonFields, _, _, variantMatch, _, _, _) =>
        collectDiscriminatorValidatorErrors(fieldName, variants, commonFields, json, root, path, currentPackage, depth, variantMatch)
      case ReferenceType(typeName) => collectRefValidatorErrors(typeName, json, root, path, currentPackage, depth)
      case RecursiveRef(typeName) => collectRefValidatorErrors(typeName, json, root, path, currentPackage, depth)
      case _ => Nil // scalars, enums, and external types carry no descendant definitions to validate
    }
  }

  // Runs a referenced definition's validators at this level, then descends into its structure so
  // validators on transitively-referenced definitions also run.
  private def collectRefValidatorErrors(
    typeName: String,
    json: JsonValue,
    root: JsonValue,
    path: String,
    currentPackage: String,
    depth: Int
  ): List[ValidationError] =
    resolveRef(typeName, currentPackage) match {
      case Some(refDef) =>
        val here = refDef.validators.flatMap(name => ValidatorRegistry.run(name, ValidatorContext(json, root, path)))
        here ++ collectValidatorErrors(refDef.templateType, json, root, path, refDef.fullPackage(basePackage), depth + 1)
      case None => Nil // phase one already reported unresolved references
    }

  private def collectDiscriminatorValidatorErrors(
    fieldName: String,
    variants: Map[String, ObjectType],
    commonFields: Map[String, FieldDef],
    json: JsonValue,
    root: JsonValue,
    path: String,
    currentPackage: String,
    depth: Int,
    variantMatch: String
  ): List[ValidationError] =
    json.asObject.flatMap(obj => obj.field(fieldName).flatMap(_.asString)).toList.flatMap { discriminatorValue =>
      val matched = variants.get(discriminatorValue).orElse {
        if (variantMatch == "prefix") variants.find { case (k, _) => discriminatorValue.startsWith(k) }.map(_._2) else None
      }
      matched.toList.flatMap { variantType =>
        val obj = json.asObject.get
        val commonErrors = commonFields.toList.flatMap { case (cfName, cfDef) =>
          obj.field(cfName).filterNot(_ == JsonNull).toList.flatMap(v => collectValidatorErrors(cfDef.fieldType, v, root, s"$path.$cfName", currentPackage, depth + 1))
        }
        val variantErrors = variantType.fields.toList.flatMap { case (vfName, vfDef) =>
          obj.field(vfName).filterNot(_ == JsonNull).toList.flatMap(v => collectValidatorErrors(vfDef.fieldType, v, root, s"$path.$vfName", currentPackage, depth + 1))
        }
        commonErrors ++ variantErrors
      }
    }
  
  
  /**
   * Validates a type against JSON with reference resolution
   */
  private def validateTypeWithRefs(
    templateType: TemplateType,
    json: JsonValue,
    path: String,
    currentPackage: String,
    depth: Int
  ): List[ValidationError] = {
    if (depth > MaxValidationDepth)
      return List(ValidationError(path,
        s"validation exceeded the maximum depth of $MaxValidationDepth (a reference cycle?)",
        "bounded nesting", "too deeply nested"))
    templateType match {
      case StringType(constraints) => validateString(json, path, constraints)
      case NumberType(constraints) => validateNumber(json, path, constraints)
      case IntType(constraints) => validateWholeNumber(json, path, "int", constraints)
      case LongType(constraints) => validateWholeNumber(json, path, "long", constraints)
      case DecimalType(constraints) => validateNumber(json, path, constraints)
      case BooleanType() => validateBoolean(json, path)
      case DateType() => validateTemporal(json, path, "date", dateParse)
      case DateTimeType() => validateTemporal(json, path, "datetime", dateTimeParse)
      case ArrayType(elementType, constraints) => validateArray(elementType, json, path, currentPackage, depth, constraints)
      case MapType(valueType) => validateMap(valueType, json, path, currentPackage, depth)
      case UnionType(types) =>
        if (types.exists(t => validateTypeWithRefs(t, json, path, currentPackage, depth + 1).isEmpty)) List.empty
        else List(ValidationError.typeMismatch(path, "one of union types", jsonType(json)))
      case ObjectType(fields, additional) => validateObject(fields, json, path, currentPackage, depth, additional)
      case TypeDiscriminator(fieldName, variants, commonFields, _, _, variantMatch, _, fallbackVariant, _) =>
        validateDiscriminator(fieldName, variants, commonFields, json, path, currentPackage, depth, variantMatch, fallbackVariant.isDefined)
      case ReferenceType(typeName) =>
        resolveRef(typeName, currentPackage) match {
          case Some(refDef) =>
            validateTypeWithRefs(refDef.templateType, json, path, refDef.fullPackage(basePackage), depth + 1)
          case None =>
            List(ValidationError(path, s"Unresolved reference: $typeName", "valid definition", typeName))
        }
      case RecursiveRef(typeName) =>
        resolveRef(typeName, currentPackage) match {
          case Some(refDef) =>
            validateTypeWithRefs(refDef.templateType, json, path, refDef.fullPackage(basePackage), depth + 1)
          case None =>
            List(ValidationError(path, s"Unresolved recursive reference: $typeName", "valid definition", typeName))
        }
      case ExternalType(_, _) =>
        // External-typed fields are not schema-validated here: a `$gen:` type's schema lives in
        // another module, and a `$extern:` type is (de)serialized by an application-registered
        // codec. Validation of these values is delegated to the referenced type's generated decode
        // (`$gen:`) or the registered codec (`$extern:`); validate() intentionally does not re-check
        // their internal structure.
        List.empty
      case EnumType(_, values) =>
        json.asString match {
          case Some(s) if values.contains(s) => List.empty
          case Some(s) => List(ValidationError.constraintViolation(path, s"enum: ${values.mkString(", ")}", s))
          case None => List(ValidationError.constraintViolation(path, s"enum: ${values.mkString(", ")}", jsonType(json)))
        }
    }
  }
  
  // Validation helper methods (each carries currentPackage so references resolve in context)
  
  private def validateString(json: JsonValue, path: String, constraints: List[Constraint]): List[ValidationError] = {
    json.asString match {
      case Some(value) =>
        constraints.flatMap {
          case MinLength(len) if value.length < len =>
            Some(ValidationError.constraintViolation(path, s"minLength: $len", s"length ${value.length}"))
          case MaxLength(len) if value.length > len =>
            Some(ValidationError.constraintViolation(path, s"maxLength: $len", s"length ${value.length}"))
          case Pattern(regex) if !patternFor(regex).matcher(value).matches() =>
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

  private def validateArray(
    elementType: TemplateType,
    json: JsonValue,
    path: String,
    currentPackage: String,
    depth: Int,
    constraints: List[Constraint] = List.empty
  ): List[ValidationError] = {
    json.asArray match {
      case Some(array) =>
        val elements = array.values.toList
        val itemErrors = elements.zipWithIndex.flatMap { case (element, idx) =>
          validateTypeWithRefs(elementType, element, s"$path[$idx]", currentPackage, depth + 1)
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
    currentPackage: String,
    depth: Int
  ): List[ValidationError] = {
    json.asObject match {
      case Some(obj) =>
        obj.fields.toList.flatMap { case (key, value) =>
          validateTypeWithRefs(valueType, value, s"$path.$key", currentPackage, depth + 1)
        }
      case None =>
        List(ValidationError.typeMismatch(path, "object", jsonType(json)))
    }
  }
  
  private def validateObject(
    fields: Map[String, FieldDef],
    json: JsonValue,
    path: String,
    currentPackage: String,
    depth: Int,
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
              validateTypeWithRefs(fieldDef.fieldType, value, s"$path.$fieldName", currentPackage, depth + 1)
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
          case TypedExtra(t) => extraKeys.flatMap(k => validateTypeWithRefs(t, jsonFieldMap(k), s"$path.$k", currentPackage, depth + 1))
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
    currentPackage: String,
    depth: Int,
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
                      validateTypeWithRefs(cfDef.fieldType, value, s"$path.$cfName", currentPackage, depth + 1)
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
                      validateTypeWithRefs(vfDef.fieldType, value, s"$path.$vfName", currentPackage, depth + 1)
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
                        validateTypeWithRefs(cfDef.fieldType, value, s"$path.$cfName", currentPackage, depth + 1)
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