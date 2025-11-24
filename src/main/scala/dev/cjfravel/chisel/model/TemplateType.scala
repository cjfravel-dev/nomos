package dev.cjfravel.chisel.model

/**
 * Represents the different types that can be defined in a Chisel template.
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
 */
case class ObjectType(fields: Map[String, FieldDef]) extends TemplateType

/**
 * Type discriminator that allows different object structures based on a discriminator field value.
 * This creates a sealed trait with case class variants in the generated code.
 *
 * @param fieldName The name of the discriminator field (e.g., "type", "kind")
 * @param variants Map of discriminator values to their respective object types
 * @param commonFields Fields that are common across all variants
 * @param includeInOutput Whether to include the discriminator field in generated case classes
 */
case class TypeDiscriminator(
  fieldName: String,
  variants: Map[String, ObjectType],
  commonFields: Map[String, FieldDef] = Map.empty,
  includeInOutput: Boolean = true
) extends TemplateType

/**
 * Reference to another named type, used for recursive structures
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