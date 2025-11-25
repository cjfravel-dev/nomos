package dev.cjfravel.nomos.model

import scala.collection.immutable.ListMap

/**
 * Represents the different types that can be defined in a Nomos template.
 */
sealed trait TemplateType

/**
 * String type with optional constraints
 */
case class StringType(constraints: List[Constraint] = List.empty) extends TemplateType

/**
 * Number type (maps to Double in Scala) with optional constraints
 */
case class NumberType(constraints: List[Constraint] = List.empty) extends TemplateType

/**
 * Boolean type
 */
case class BooleanType() extends TemplateType

/**
 * Array type containing elements of a specific type
 */
case class ArrayType(elementType: TemplateType) extends TemplateType

/**
 * Object type with named fields
 *
 * @param fields The fields in this object (order-preserving)
 */
case class ObjectType(
  fields: ListMap[String, FieldDef]
) extends TemplateType

/**
 * Type discriminator that allows different object structures based on a discriminator field value.
 * This creates a sealed trait with case class variants in the generated code.
 *
 * @param fieldName The name of the discriminator field (e.g., "type", "kind")
 * @param variants Map of discriminator values to their respective object types (order-preserving)
 * @param commonFields Fields that are common across all variants (order-preserving)
 * @param includeInOutput Whether to include the discriminator field in generated case classes
 * @param variantNames Optional mapping from variant keys to custom class names (e.g., "String" -> "StringDataContractColumn")
 */
case class TypeDiscriminator(
  fieldName: String,
  variants: ListMap[String, ObjectType],
  commonFields: ListMap[String, FieldDef] = ListMap.empty,
  includeInOutput: Boolean = true,
  variantNames: Map[String, String] = Map.empty
) extends TemplateType

/**
 * Reference to another named type definition in the same template file.
 * Used for $ref:TypeName syntax to enable type reuse and composition.
 */
case class ReferenceType(typeName: String) extends TemplateType

/**
 * Reference to a recursive type (self-reference).
 * Used for recursive structures like trees.
 */
case class RecursiveRef(typeName: String) extends TemplateType

/**
 * Represents a field definition in an object type
 *
 * @param fieldType The type of the field
 * @param optional Whether the field is optional (becomes Option[T] in Scala)
 */
case class FieldDef(
  fieldType: TemplateType,
  optional: Boolean = false
)