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

/**
 * Represents a single type definition within a multi-definition template
 *
 * @param name The name of the type to generate (must be a valid Scala identifier)
 * @param templateType The type definition
 * @param subPackage Optional sub-package relative to the base package
 * @param description Optional description of this definition
 */
case class TemplateDefinition(
  name: String,
  templateType: TemplateType,
  subPackage: Option[String] = None,
  description: Option[String] = None
) {
  /**
   * Validates that the definition name is a valid Scala identifier
   */
  def validateName(): Option[String] = {
    if (name.isEmpty) {
      Some("Definition name cannot be empty")
    } else if (!name.head.isUpper) {
      Some(s"Definition name must start with an uppercase letter: $name")
    } else if (!name.forall(c => c.isLetterOrDigit || c == '_')) {
      Some(s"Definition name must contain only letters, digits, and underscores: $name")
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

/**
 * Represents a complete template file with multiple type definitions
 *
 * @param basePackage Base package for all generated code
 * @param outputDir Output directory for generated files
 * @param mainClass Name of the primary/entry type
 * @param definitions List of type definitions
 */
case class MultiTemplate(
  basePackage: String,
  outputDir: String,
  mainClass: String,
  definitions: List[TemplateDefinition]
) {
  /**
   * Get the main definition (entry point)
   */
  def mainDefinition: Option[TemplateDefinition] =
    definitions.find(_.name == mainClass)
  
  /**
   * Get definition by name
   */
  def getDefinition(name: String): Option[TemplateDefinition] =
    definitions.find(_.name == name)
  
  /**
   * Get all definitions as a map for quick lookup
   */
  def definitionsMap: Map[String, TemplateDefinition] =
    definitions.map(d => d.name -> d).toMap
  
  /**
   * Validates the multi-template structure
   */
  def validate(): List[String] = {
    var errors = List.empty[String]
    
    // Validate that mainClass references an existing definition
    if (mainDefinition.isEmpty) {
      errors = s"mainClass '$mainClass' not found in definitions" :: errors
    }
    
    // Validate that all definition names are valid
    definitions.foreach { definition =>
      definition.validateName() match {
        case Some(error) => errors = s"Definition '${definition.name}': $error" :: errors
        case None =>
      }
    }
    
    // Validate that definition names are unique
    val nameGroups = definitions.groupBy(_.name)
    nameGroups.foreach { case (name, defs) =>
      if (defs.length > 1) {
        errors = s"Duplicate definition name: $name" :: errors
      }
    }
    
    // Validate base package is not empty
    if (basePackage.isEmpty) {
      errors = "basePackage cannot be empty" :: errors
    }
    
    // Validate outputDir is not empty
    if (outputDir.isEmpty) {
      errors = "outputDir cannot be empty" :: errors
    }
    
    // Validate that all references point to existing definitions
    val definitionNames = definitions.map(_.name).toSet
    definitions.foreach { definition =>
      val unresolvedRefs = findUnresolvedReferences(definition.templateType, definitionNames)
      unresolvedRefs.foreach { refName =>
        errors = s"Definition '${definition.name}' references undefined type: $refName" :: errors
      }
    }
    
    errors.reverse
  }
  
  /**
   * Finds all unresolved references in a template type
   */
  private def findUnresolvedReferences(templateType: TemplateType, definedTypes: Set[String]): Set[String] = {
    def findInType(tt: TemplateType): Set[String] = tt match {
      case ReferenceType(typeName) if !definedTypes.contains(typeName) => Set(typeName)
      case ArrayType(elementType) => findInType(elementType)
      case ObjectType(fields) => fields.values.flatMap(f => findInType(f.fieldType)).toSet
      case TypeDiscriminator(_, variants, commonFields, _) =>
        val variantRefs = variants.values.flatMap(v => v.fields.values.flatMap(f => findInType(f.fieldType)))
        val commonRefs = commonFields.values.flatMap(f => findInType(f.fieldType))
        variantRefs.toSet ++ commonRefs
      case _ => Set.empty
    }
    findInType(templateType)
  }
}

object MultiTemplate {
  /**
   * Creates a simple multi-template with a single definition
   */
  def single(
    basePackage: String,
    outputDir: String,
    definition: TemplateDefinition
  ): MultiTemplate = {
    MultiTemplate(basePackage, outputDir, definition.name, List(definition))
  }
}