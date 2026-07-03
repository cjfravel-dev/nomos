package dev.cjfravel.nomos.parser

import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.generation.ScalaCodeBuilder
import dev.cjfravel.nomos.json.{Json, JsonValue, JsonString, JsonBoolean, JsonNumber}
import scala.collection.immutable.ListMap

/**
 * Parses JSON template definitions into Template objects using the first-party JSON parser.
 */
class TemplateParser {

  /**
   * Parses a type definition
   */
  private def parseType(json: JsonValue, path: String): Either[ParseError, TemplateType] = {
    json.asString match {
      case Some(text) =>
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
            Right(ReferenceType(ref.substring(5)))

          case ext if ext.startsWith("$extern:") =>
            Right(ExternalType(ext.substring(8)))

          // Reference to a nomos-generated type in another module (by fully-qualified name).
          // Its companion's decode/encode are called directly; no runtime registration needed.
          case gen if gen.startsWith("$gen:") =>
            Right(ExternalType(gen.substring(5), generated = true))

          case _ => Left(ParseError.InvalidType(text, path))
        }
      case None =>
        json.asArray match {
          case Some(array) =>
            if (array.size == 1) {
              parseType(array(0), s"$path[]").map(t => ArrayType(t))
            } else if (array.size > 1) {
              val parsed = array.values.toList.zipWithIndex.map { case (n, i) => parseType(n, s"$path|$i") }
              val errs = parsed.collect { case Left(e) => e }
              if (errs.nonEmpty) Left(ParseError.MultipleErrors(errs))
              else Right(UnionType(parsed.collect { case Right(t) => t }))
            } else {
              Left(ParseError.InvalidType("array", path, "Array must have at least one element type"))
            }
          case None =>
            json.asObject match {
              case Some(_) =>
                // Check for different object patterns
                if (has(json, "type")) {
                  parseComplexType(json, path)
                } else if (has(json, "$type")) {
                  parseDiscriminator(json, path)
                } else if (has(json, "$map")) {
                  for {
                    valueJson <- extractField(json, "$map", path)
                    valueType <- parseType(valueJson, s"$path{}")
                  } yield MapType(valueType)
                } else if (has(json, "$optional")) {
                  for {
                    innerType <- extractField(json, "$optional", path)
                    parsedType <- parseType(innerType, path)
                  } yield parsedType // The optional flag is handled at the FieldDef level
                } else {
                  // Plain object type
                  parseObjectType(json, path)
                }
              case None =>
                Left(ParseError.InvalidType(Json.write(json), path, "Unrecognized type format"))
            }
        }
    }
  }

  /**
   * Parses a complex type definition with constraints
   */
  private def parseComplexType(json: JsonValue, path: String): Either[ParseError, TemplateType] = {
    extractString(json, "type", path).flatMap {
      case "string" =>
        if (has(json, "enum") && extractOptionalString(json, "as").contains("enumType")) {
          val name = extractOptionalString(json, "name").getOrElse("Enum")
          val values = parseEnum(json).map(_.values).getOrElse(Nil)
          Right(EnumType(name, values))
        } else {
          Right(StringType(parseStringConstraints(json, path)))
        }

      case "number" =>
        Right(NumberType(parseNumberConstraints(json, path)))

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
  private def parseEnum(json: JsonValue): Option[Enum] = {
    json.asObject.flatMap(_.field("enum")).flatMap(_.asArray).map { arr =>
      Enum(arr.values.collect { case JsonString(s) => s }.toList)
    }
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
  private def parseStringConstraints(json: JsonValue, path: String): List[Constraint] = {
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
  private def parseArrayConstraints(json: JsonValue): List[Constraint] = {
    var constraints = List.empty[Constraint]
    extractOptionalInt(json, "minItems").foreach(n => constraints = MinItems(n) :: constraints)
    extractOptionalInt(json, "maxItems").foreach(n => constraints = MaxItems(n) :: constraints)
    extractOptionalBoolean(json, "uniqueItems").foreach(u => constraints = UniqueItems(u) :: constraints)
    constraints
  }

  /**
   * Parses number constraints
   */
  private def parseNumberConstraints(json: JsonValue, path: String): List[Constraint] = {
    var constraints = List.empty[Constraint]

    extractOptionalDouble(json, "min").foreach(v => constraints = Min(v) :: constraints)
    extractOptionalDouble(json, "max").foreach(v => constraints = Max(v) :: constraints)
    extractOptionalDouble(json, "multipleOf").foreach(v => constraints = MultipleOf(v) :: constraints)

    constraints
  }

  /**
   * Parses an object type
   */
  private def parseObjectType(json: JsonValue, path: String): Either[ParseError, ObjectType] = {
    json.asObject match {
      case Some(obj) =>
        val fields = obj.fields.toList.filterNot(_._1 == "$additionalProperties")
        val fieldResults = fields.map { case (key, fieldValue) =>
          val fieldName = unescapeKey(key)
          parseFieldDef(fieldValue, s"$path.$fieldName").map(fieldName -> _)
        }

        val errors = fieldResults.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(ParseError.MultipleErrors(errors))
        } else {
          parseAdditional(json, path).map { additional =>
            val fieldMap = ListMap(fieldResults.collect { case Right(pair) => pair }: _*)
            ObjectType(fieldMap, additional)
          }
        }
      case None =>
        Left(ParseError.InvalidType("object", path, "Expected JSON object"))
    }
  }

  /**
   * Strips surrounding backticks so a quoted key like `type` becomes the literal field name "type".
   */
  private def unescapeKey(key: String): String =
    if (key.length >= 2 && key.startsWith("`") && key.endsWith("`")) key.substring(1, key.length - 1) else key

  /**
   * Parses the $additionalProperties policy: true allows extras, false forbids, a type validates
   * extras. An invalid type value is reported as a parse error rather than silently forbidding.
   */
  private def parseAdditional(json: JsonValue, path: String): Either[ParseError, AdditionalProperties] = {
    json.asObject.flatMap(_.field("$additionalProperties")) match {
      case None => Right(ForbidExtra)
      case Some(JsonBoolean(b)) => Right(if (b) AllowExtra else ForbidExtra)
      case Some(node) => parseType(node, s"$path.$$additionalProperties").map(TypedExtra)
    }
  }

  /**
   * Parses a field definition
   */
  private def parseFieldDef(json: JsonValue, path: String): Either[ParseError, FieldDef] = {
    if (has(json, "$optional")) {
      for {
        innerType <- extractField(json, "$optional", path)
        fieldType <- parseType(innerType, path)
      } yield FieldDef(fieldType, optional = true, nullable = extractOptionalBoolean(json, "nullable").getOrElse(false))
    } else {
      parseType(json, path).flatMap { tpe =>
        extractOptionalString(json, "adapter") match {
          case Some(_) if !tpe.isInstanceOf[StringType] =>
            Left(ParseError.InvalidFieldValue("adapter", "a string-typed field", "adapter is only supported on string fields", path))
          case adapter =>
            renderDefault(json, tpe, path).right.map(default =>
              FieldDef(tpe, optional = false, default = default, adapter = adapter))
        }
      }
    }
  }

  /**
   * Renders a field's "default" as a Scala literal of the field's type, and validates it is
   * representable: string/numeric/boolean/enum defaults are supported (an enum default becomes
   * `EnumName.Value`); a default of the wrong shape, an out-of-set enum value, or a default on any
   * other type (date, datetime, reference, collection, object) is a clear parse error rather than
   * uncompilable generated source.
   */
  private def renderDefault(json: JsonValue, tpe: TemplateType, path: String): Either[ParseError, Option[String]] =
    json.asObject.flatMap(_.field("default")) match {
      case None => Right(None)
      case Some(d) => renderDefaultLiteral(d, tpe, s"$path.default").right.map(Some(_))
    }

  private def renderDefaultLiteral(d: JsonValue, tpe: TemplateType, path: String): Either[ParseError, String] = tpe match {
    case StringType(_) =>
      d.asString match {
        case Some(s) => Right("\"" + ScalaCodeBuilder.escapeStringLiteral(s) + "\"")
        case None => Left(ParseError.InvalidFieldValue("default", "a string", "default must be a string for a string field", path))
      }
    case IntType(_) | LongType(_) | NumberType(_) | DecimalType(_) =>
      d match {
        case _: JsonNumber => Right(Json.write(d))
        case _ => Left(ParseError.InvalidFieldValue("default", "a number", "default must be a number for a numeric field", path))
      }
    case BooleanType() =>
      d match {
        case _: JsonBoolean => Right(Json.write(d))
        case _ => Left(ParseError.InvalidFieldValue("default", "a boolean", "default must be a boolean for a boolean field", path))
      }
    case EnumType(enumName, values) =>
      d.asString match {
        case Some(s) if values.contains(s) => Right(s"$enumName.${ScalaCodeBuilder.toPascalCase(s)}")
        case Some(s) => Left(ParseError.InvalidFieldValue("default", s"one of: ${values.mkString(", ")}", s"'$s' is not a value of enum '$enumName'", path))
        case None => Left(ParseError.InvalidFieldValue("default", "an enum value (string)", "default must be a string naming an enum value", path))
      }
    case _ =>
      Left(ParseError.InvalidFieldValue("default", "a string, numeric, boolean, or enum field",
        "default values are only supported on string, numeric, boolean, and enum fields", path))
  }

  /**
   * Parses a type discriminator
   */
  private def parseDiscriminator(json: JsonValue, path: String): Either[ParseError, TypeDiscriminator] = {
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
      fallbackVariant = extractOptionalString(typeObj, "fallbackVariant")
      discriminatorEnum = extractOptionalString(typeObj, "discriminatorEnum")
    } yield TypeDiscriminator(fieldName, variants, commonFields, includeInOutput, variantNames, variantMatch, variantSubPackage, fallbackVariant, discriminatorEnum)
  }

  /**
   * Parses discriminator variants
   */
  private def parseVariants(json: JsonValue, path: String): Either[ParseError, ListMap[String, ObjectType]] = {
    json.asObject match {
      case Some(obj) =>
        val variantResults = obj.fields.toList.map { case (variantName, variantJson) =>
          variantJson.asString match {
            case Some(s) if s.startsWith("$ref:") =>
              Right(variantName -> ObjectType(ListMap("$ref" -> FieldDef(ReferenceType(s.substring(5))))))
            case _ =>
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
      case None =>
        Left(ParseError.InvalidDiscriminator("variants must be an object", path))
    }
  }

  /**
   * Parses common fields for discriminator
   */
  private def parseCommonFields(json: JsonValue, path: String): Either[ParseError, ListMap[String, FieldDef]] = {
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
  private def parseVariantNames(json: JsonValue, path: String): Map[String, String] = {
    extractOptionalField(json, "variantNames").flatMap(_.asObject) match {
      case Some(obj) =>
        obj.fields.collect { case (k, JsonString(v)) => k -> v }.toMap
      case None =>
        Map.empty
    }
  }

  // Helper methods for extracting JSON fields

  /** True when json is an object containing the given field. */
  private def has(json: JsonValue, fieldName: String): Boolean =
    json.asObject.exists(_.contains(fieldName))

  private def extractField(json: JsonValue, fieldName: String, path: String): Either[ParseError, JsonValue] =
    json.asObject.flatMap(_.field(fieldName)) match {
      case Some(value) => Right(value)
      case None => Left(ParseError.MissingField(fieldName, path))
    }

  private def extractOptionalField(json: JsonValue, fieldName: String): Option[JsonValue] =
    json.asObject.flatMap(_.field(fieldName))

  private def extractString(json: JsonValue, fieldName: String, path: String): Either[ParseError, String] =
    json.asObject.flatMap(_.field(fieldName)) match {
      case Some(JsonString(s)) => Right(s)
      case Some(other) => Left(ParseError.InvalidFieldValue(fieldName, "string", Json.write(other), path))
      case None => Left(ParseError.MissingField(fieldName, path))
    }

  private def extractOptionalString(json: JsonValue, fieldName: String): Option[String] =
    json.asObject.flatMap(_.field(fieldName)).flatMap(_.asString)

  private def extractStringList(json: JsonValue, fieldName: String): List[String] =
    json.asObject.flatMap(_.field(fieldName)).flatMap(_.asArray) match {
      case Some(arr) => arr.values.collect { case JsonString(s) => s }.toList
      case None => List.empty
    }

  private def extractOptionalInt(json: JsonValue, fieldName: String): Option[Int] =
    json.asObject.flatMap(_.field(fieldName)).flatMap(_.asNumber).map(_.asDouble.toInt)

  private def extractOptionalDouble(json: JsonValue, fieldName: String): Option[Double] =
    json.asObject.flatMap(_.field(fieldName)).flatMap(_.asNumber).map(_.asDouble)

  private def extractOptionalBoolean(json: JsonValue, fieldName: String): Option[Boolean] =
    json.asObject.flatMap(_.field(fieldName)).flatMap(_.asBoolean)

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

  /**
   * Parses a JSON string into a MultiTemplate. basePackage is supplied by the caller.
   */
  def parseMultiTemplate(jsonString: String, basePackage: String, validateRefs: Boolean = true): Either[ParseError, MultiTemplate] = {
    Json.parse(jsonString) match {
      case Right(json) => parseMultiTemplateJson(json, basePackage, validateRefs)
      case Left(msg) => Left(ParseError.JsonSyntaxError(msg))
    }
  }

  /**
   * Parses a multi-template JSON structure. basePackage is supplied by the caller.
   */
  private def parseMultiTemplateJson(json: JsonValue, basePackage: String, validateRefs: Boolean): Either[ParseError, MultiTemplate] = {
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
      val visibility = extractOptionalString(json, "visibility")
      val multiTemplate = MultiTemplate(basePackage, definitions, useOptionTypes, listType, fromJsonStyle, dateType, dateTimeType, mapType, visibility)

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
  private def parseDefinitions(json: JsonValue, path: String): Either[ParseError, List[TemplateDefinition]] = {
    json.asArray match {
      case Some(array) =>
        val definitionResults = array.values.toList.zipWithIndex.map { case (defJson, idx) =>
          parseDefinition(defJson, s"$path[$idx]")
        }

        val errors = definitionResults.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(ParseError.MultipleErrors(errors))
        } else {
          Right(definitionResults.collect { case Right(definition) => definition })
        }
      case None =>
        Left(ParseError.InvalidType("array", path, "definitions must be an array"))
    }
  }

  /**
   * Parses a single definition
   */
  private def parseDefinition(json: JsonValue, path: String): Either[ParseError, TemplateDefinition] = {
    for {
      name <- extractString(json, "name", path)
      subPackage = extractOptionalString(json, "subPackage")
      description = extractOptionalString(json, "description")
      validators = extractStringList(json, "validators")
      templateJson <- extractField(json, "template", path)
      templateType <- parseType(templateJson, s"$path.template")
    } yield TemplateDefinition(name, templateType, subPackage, description, validators)
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
