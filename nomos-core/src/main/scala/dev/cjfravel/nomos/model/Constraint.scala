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

// Number constraints
case class Min(value: Double) extends Constraint
case class Max(value: Double) extends Constraint
case class MultipleOf(value: Double) extends Constraint

// Array constraints
case class MinItems(count: Int) extends Constraint
case class MaxItems(count: Int) extends Constraint
case class UniqueItems(unique: Boolean) extends Constraint