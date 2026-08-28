package dev.cjfravel.nomos.model

/**
 * Represents validation constraints that can be applied to template types
 */
sealed trait Constraint

// String constraints
case class MinLength(length: Int) extends Constraint
case class MaxLength(length: Int) extends Constraint
case class Pattern(regex: String) extends Constraint
case class Format(formatType: String) extends Constraint // e.g., "email", "url", "date"
case class Enum(values: List[String]) extends Constraint

// Number constraints
case class Min(value: Double) extends Constraint
case class Max(value: Double) extends Constraint
case class MultipleOf(value: Double) extends Constraint

// Array constraints
case class MinItems(count: Int) extends Constraint
case class MaxItems(count: Int) extends Constraint
case class UniqueItems(unique: Boolean) extends Constraint

/**
 * Requires the named field(s) to be unique across an array of objects. Unlike [[UniqueItems]], which compares whole
 * elements, this compares only the listed fields, so two elements sharing an identifier but differing elsewhere are
 * rejected. Several field names form a composite key.
 */
case class UniqueBy(fields: List[String]) extends Constraint
