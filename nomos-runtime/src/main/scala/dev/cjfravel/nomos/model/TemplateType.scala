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
 * Integer type (maps to Int in Scala) with optional constraints. Requires whole numbers.
 */
case class IntType(constraints: List[Constraint] = List.empty) extends TemplateType

/**
 * Long type (maps to Long in Scala) with optional constraints. Requires whole numbers.
 */
case class LongType(constraints: List[Constraint] = List.empty) extends TemplateType

/**
 * Decimal type (maps to BigDecimal in Scala) with optional constraints.
 */
case class DecimalType(constraints: List[Constraint] = List.empty) extends TemplateType

/**
 * Boolean type
 */
case class BooleanType() extends TemplateType

/**
 * Date type (maps to java.time.LocalDate).
 */
case class DateType() extends TemplateType

/**
 * Date-time type (maps to java.time.LocalDateTime).
 */
case class DateTimeType() extends TemplateType

/**
 * Array type containing elements of a specific type, with optional item constraints.
 */
case class ArrayType(elementType: TemplateType, constraints: List[Constraint] = List.empty) extends TemplateType

/**
 * Map type with string keys and values of a specific type. Keys are unrestricted.
 */
case class MapType(valueType: TemplateType) extends TemplateType

/**
 * Union of alternative types; a value is valid if it matches any member.
 */
case class UnionType(types: List[TemplateType]) extends TemplateType

/**
 * Policy for keys not declared in an object type.
 */
sealed trait AdditionalProperties
case object ForbidExtra extends AdditionalProperties
case object AllowExtra extends AdditionalProperties
case class TypedExtra(valueType: TemplateType) extends AdditionalProperties

/**
 * Object type with named fields
 *
 * @param fields The fields in this object (order-preserving)
 * @param additional Policy for keys not declared in fields (default: forbid)
 */
case class ObjectType(
  fields: ListMap[String, FieldDef],
  additional: AdditionalProperties = ForbidExtra
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
 * @param variantMatch How a discriminator value is matched to a variant (default "exact")
 * @param variantSubPackage Optional sub-package (relative to the trait's package) for the generated variant case classes; the trait stays in its own package
 * @param fallbackVariant Optional name for a catch-all variant: an unrecognized discriminator value decodes into this case class (carrying the value and the raw object) and re-emits the preserved payload on encode, instead of failing
 */
case class TypeDiscriminator(
  fieldName: String,
  variants: ListMap[String, ObjectType],
  commonFields: ListMap[String, FieldDef] = ListMap.empty,
  includeInOutput: Boolean = true,
  variantNames: Map[String, String] = Map.empty,
  variantMatch: String = "exact",
  variantSubPackage: Option[String] = None,
  fallbackVariant: Option[String] = None
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
 * Reference to an external, hand-written type that nomos does not generate or validate.
 * Used for $extern:fully.qualified.Name syntax.
 *
 * @param qualifiedName the fully-qualified Scala type name, emitted verbatim
 * @param generated when true, the target is another nomos-generated type (referenced via
 *   `$gen:`): its companion's `decode`/`encode` are called directly, so no runtime codec
 *   registration is needed. When false (`$extern:`), the type is opaque and (de)serialized
 *   through the runtime CodecRegistry.
 */
case class ExternalType(qualifiedName: String, generated: Boolean = false) extends TemplateType

/**
 * Closed enum generated as a named sealed-trait type with string (de)serialization.
 */
case class EnumType(name: String, values: List[String]) extends TemplateType

/**
 * Represents a field definition in an object type
 *
 * @param fieldType The type of the field
 * @param optional Whether the field is optional (becomes Option[T] in Scala)
 * @param default Optional default value rendered as a Scala literal (e.g. "false", "\"x\"")
 * @param adapter Optional named (de)serialization adapter for the field
 * @param nullable When true, an optional field generates a raw nullable type (no Option wrapper)
 */
case class FieldDef(
  fieldType: TemplateType,
  optional: Boolean = false,
  default: Option[String] = None,
  adapter: Option[String] = None,
  nullable: Boolean = false
)