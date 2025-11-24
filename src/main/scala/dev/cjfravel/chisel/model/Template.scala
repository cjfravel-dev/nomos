package dev.cjfravel.chisel.model

/**
 * Represents a complete Chisel template definition
 *
 * @param name The name of the type to generate (must be a valid Scala identifier)
 * @param subPackage Optional sub-package relative to the base package
 * @param templateType The root type definition
 * @param description Optional description of the template
 * @param version Optional version string
 */
case class Template(
  name: String,
  subPackage: Option[String],
  templateType: TemplateType,
  description: Option[String] = None,
  version: Option[String] = None
) {
  /**
   * Validates that the template name is a valid Scala identifier
   */
  def validateName(): Option[String] = {
    if (name.isEmpty) {
      Some("Template name cannot be empty")
    } else if (!name.head.isUpper) {
      Some(s"Template name must start with an uppercase letter: $name")
    } else if (!name.forall(c => c.isLetterOrDigit || c == '_')) {
      Some(s"Template name must contain only letters, digits, and underscores: $name")
    } else {
      None
    }
  }

  /**
   * Gets the full package path by combining base package with sub-package
   */
  def fullPackage(basePackage: String): String = {
    subPackage match {
      case Some(sub) if sub.nonEmpty => s"$basePackage.$sub"
      case _ => basePackage
    }
  }
}

object Template {
  /**
   * Creates a simple template with just a name and type
   */
  def simple(name: String, templateType: TemplateType): Template = {
    Template(name, None, templateType, None, None)
  }
}