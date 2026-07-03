package dev.cjfravel.nomos.generation

import dev.cjfravel.nomos.model._
import scala.collection.immutable.ListMap

/**
 * Serializes MultiTemplate and related model objects into Scala source code
 * that can be embedded in generated files to reconstruct the template at runtime.
 */
object TemplateSerializer {
  
  /**
   * Generates Scala code to reconstruct a MultiTemplate
   */
  def serializeMultiTemplate(template: MultiTemplate): String = {
    val definitions = template.definitions.map(serializeDefinition).mkString(",\n      ")
    val visibility = template.visibility.map(s => s"""Some("${escapeString(s)}")""").getOrElse("None")

    s"""MultiTemplate(
      basePackage = "${escapeString(template.basePackage)}",
      definitions = List(
      $definitions
      ),
      useOptionTypes = ${template.useOptionTypes},
      listType = "${escapeString(template.listType)}",
      fromJsonStyle = "${escapeString(template.fromJsonStyle)}",
      dateType = "${escapeString(template.dateType)}",
      dateTimeType = "${escapeString(template.dateTimeType)}",
      mapType = "${escapeString(template.mapType)}",
      visibility = $visibility
    )"""
  }
  
  /**
   * Generates Scala code to reconstruct a TemplateDefinition
   */
  def serializeDefinition(definition: TemplateDefinition): String = {
    val subPackage = definition.subPackage.map(s => s"""Some("${escapeString(s)}")""").getOrElse("None")
    val description = definition.description.map(s => s"""Some("${escapeString(s)}")""").getOrElse("None")
    
    s"""TemplateDefinition(
        name = "${escapeString(definition.name)}",
        templateType = ${serializeTemplateType(definition.templateType)},
        subPackage = $subPackage,
        description = $description,
        validators = List(${definition.validators.map(v => s""""${escapeString(v)}"""").mkString(", ")})
      )"""
  }
  
  /**
   * Generates Scala code to reconstruct a TemplateType
   */
  def serializeTemplateType(templateType: TemplateType): String = {
    templateType match {
      case StringType(constraints) =>
        val constraintsList = constraints.map(serializeConstraint).mkString(", ")
        s"StringType(List($constraintsList))"
      
      case NumberType(constraints) =>
        val constraintsList = constraints.map(serializeConstraint).mkString(", ")
        s"NumberType(List($constraintsList))"
      
      case IntType(constraints) =>
        s"IntType(List(${constraints.map(serializeConstraint).mkString(", ")}))"
      
      case LongType(constraints) =>
        s"LongType(List(${constraints.map(serializeConstraint).mkString(", ")}))"
      
      case DecimalType(constraints) =>
        s"DecimalType(List(${constraints.map(serializeConstraint).mkString(", ")}))"
      
      case BooleanType() =>
        "BooleanType()"
      
      case DateType() => "DateType()"
      case DateTimeType() => "DateTimeType()"
      
      case ArrayType(elementType, constraints) =>
        s"ArrayType(${serializeTemplateType(elementType)}, List(${constraints.map(serializeConstraint).mkString(", ")}))"
      
      case MapType(valueType) =>
        s"MapType(${serializeTemplateType(valueType)})"
      
      case UnionType(types) =>
        s"UnionType(List(${types.map(serializeTemplateType).mkString(", ")}))"
      
      case ObjectType(fields, additional) =>
        val fieldsList = fields.map { case (name, fieldDef) =>
          s""""${escapeString(name)}" -> ${serializeFieldDef(fieldDef)}"""
        }.mkString(", ")
        s"ObjectType(ListMap($fieldsList), ${serializeAdditional(additional)})"
      
      case ReferenceType(typeName) =>
        s"""ReferenceType("${escapeString(typeName)}")"""
      
      case RecursiveRef(typeName) =>
        s"""RecursiveRef("${escapeString(typeName)}")"""
      
      case ExternalType(qn, generated) =>
        s"""ExternalType("${escapeString(qn)}", generated = $generated)"""
      
      case EnumType(enumName, values) =>
        s"""EnumType("${escapeString(enumName)}", List(${values.map(v => s""""${escapeString(v)}"""").mkString(", ")}))"""
      
      case TypeDiscriminator(fieldName, variants, commonFields, includeInOutput, variantNames, variantMatch, variantSubPackage, fallbackVariant, discriminatorEnum) =>
        val variantsList = variants.map { case (name, objType) =>
          s""""${escapeString(name)}" -> ${serializeObjectTypeFields(objType)}"""
        }.mkString(", ")
        val commonFieldsList = commonFields.map { case (name, fieldDef) =>
          s""""${escapeString(name)}" -> ${serializeFieldDef(fieldDef)}"""
        }.mkString(", ")
        val variantNamesMap = if (variantNames.isEmpty) {
          "Map.empty[String, String]"
        } else {
          val entries = variantNames.toList.sortBy(_._1).map { case (k, v) =>
            s""""${escapeString(k)}" -> "${escapeString(v)}""""
          }.mkString(", ")
          s"Map($entries)"
        }
        def optStr(o: Option[String]) = o.map(s => s""""${escapeString(s)}"""").map(q => s"Some($q)").getOrElse("None")
        
        s"""TypeDiscriminator(
          fieldName = "${escapeString(fieldName)}",
          variants = ListMap($variantsList),
          commonFields = ListMap($commonFieldsList),
          includeInOutput = $includeInOutput,
          variantNames = $variantNamesMap,
          variantMatch = "${escapeString(variantMatch)}",
          variantSubPackage = ${optStr(variantSubPackage)},
          fallbackVariant = ${optStr(fallbackVariant)},
          discriminatorEnum = ${optStr(discriminatorEnum)}
        )"""
    }
  }
  
  /**
   * Serializes just the ObjectType fields (for use in TypeDiscriminator variants)
   */
  private def serializeAdditional(a: AdditionalProperties): String = a match {
    case ForbidExtra => "ForbidExtra"
    case AllowExtra => "AllowExtra"
    case TypedExtra(t) => s"TypedExtra(${serializeTemplateType(t)})"
  }

  private def serializeObjectTypeFields(objType: ObjectType): String = {
    val fieldsList = objType.fields.map { case (name, fieldDef) =>
      s""""${escapeString(name)}" -> ${serializeFieldDef(fieldDef)}"""
    }.mkString(", ")
    s"ObjectType(ListMap($fieldsList))"
  }
  
  /**
   * Generates Scala code to reconstruct a FieldDef
   */
  def serializeFieldDef(fieldDef: FieldDef): String = {
    val default = fieldDef.default.map(d => s""", default = Some("${escapeString(d)}")""").getOrElse("")
    val adapter = fieldDef.adapter.map(a => s""", adapter = Some("${escapeString(a)}")""").getOrElse("")
    val nullable = if (fieldDef.nullable) ", nullable = true" else ""
    s"FieldDef(${serializeTemplateType(fieldDef.fieldType)}, optional = ${fieldDef.optional}$default$adapter$nullable)"
  }
  
  /**
   * Generates Scala code to reconstruct a Constraint
   */
  def serializeConstraint(constraint: Constraint): String = {
    constraint match {
      case MinLength(len) => s"MinLength($len)"
      case MaxLength(len) => s"MaxLength($len)"
      case Pattern(regex) => s"""Pattern("${escapeString(regex)}")"""
      case Format(fmt) => s"""Format("${escapeString(fmt)}")"""
      case Enum(values) => s"""Enum(List(${values.map(v => s""""${escapeString(v)}"""").mkString(", ")}))"""
      case Min(value) => s"Min($value)"
      case Max(value) => s"Max($value)"
      case MultipleOf(value) => s"MultipleOf($value)"
      case MinItems(count) => s"MinItems($count)"
      case MaxItems(count) => s"MaxItems($count)"
      case UniqueItems(unique) => s"UniqueItems($unique)"
    }
  }
  
  /**
   * Escapes special characters in strings for Scala source code
   */
  private def escapeString(s: String): String =
    ScalaCodeBuilder.escapeStringLiteral(s)
}