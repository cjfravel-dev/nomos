package dev.cjfravel.nomos.model

/**
 * Represents a single type definition within a multi-definition template
 *
 * @param name The name of the type to generate (must be a valid Scala identifier)
 * @param templateType The type definition
 * @param subPackage Optional sub-package relative to the base package
 * @param description Optional description of this definition
 * @param validators Names of registered custom validators to run after schema validation
 * @param sourcePath Generation-time pointer to the template file this definition came from
 */
case class TemplateDefinition(
  name: String,
  templateType: TemplateType,
  subPackage: Option[String] = None,
  description: Option[String] = None,
  validators: List[String] = List.empty,
  sourcePath: Option[String] = None
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
      case Some(sub) if sub.nonEmpty && basePackage.nonEmpty => s"$basePackage.$sub"
      case Some(sub) if sub.nonEmpty => sub
      case _ => basePackage
    }
  }
}

/**
 * Represents a complete template file with multiple type definitions.
 *
 * @param basePackage Base package for all generated code (derived from template path)
 * @param definitions List of type definitions
 * @param useOptionTypes Whether to use Option[T] for optional fields (default: true)
 * @param listType The collection type to use for arrays: "List" or "Array" (default: "List")
 */
case class MultiTemplate(
  basePackage: String,
  definitions: List[TemplateDefinition],
  useOptionTypes: Boolean = true,
  listType: String = "List",
  fromJsonStyle: String = "either",
  dateType: String = "java.time.LocalDate",
  dateTimeType: String = "java.time.LocalDateTime",
  mapType: String = "Map"
) {
  /**
   * Fully-qualified name of a definition (basePackage + subPackage + name)
   */
  def fqn(definition: TemplateDefinition): String =
    s"${definition.fullPackage(basePackage)}.${definition.name}"
  
  /**
   * Get definition by fully-qualified name or simple name
   */
  def getDefinition(name: String): Option[TemplateDefinition] =
    definitions.find(d => d.name == name || fqn(d) == name)
  
  /**
   * Get all definitions as a map for quick lookup
   */
  def definitionsMap: Map[String, TemplateDefinition] =
    definitions.map(d => d.name -> d).toMap
  
  /**
   * Validates the multi-template structure
   */
  def validate(checkRefs: Boolean = true): List[String] = {
    var errors = List.empty[String]
    
    // Validate that all definition names are valid
    definitions.foreach { definition =>
      definition.validateName() match {
        case Some(error) => errors = s"Definition '${definition.name}': $error" :: errors
        case None =>
      }
    }
    
    // Validate that definition names are unique within each package (identical simple names in
    // distinct sub-packages are allowed, matching normal Scala/Java package semantics).
    val nameGroups = definitions.groupBy(d => (d.fullPackage(basePackage), d.name))
    nameGroups.foreach { case ((pkg, name), defs) =>
      if (defs.length > 1) {
        val where = if (pkg.isEmpty) "" else s" in package $pkg"
        errors = s"Duplicate definition name: $name$where" :: errors
      }
    }
    
    // Validate base package is not empty unless every definition carries its own sub-package
    if (basePackage.isEmpty && !definitions.forall(_.subPackage.exists(_.nonEmpty))) {
      errors = "basePackage cannot be empty" :: errors
    }
    
    // Validate that all references point to existing definitions, and that simple-name references
    // are not ambiguous (the same simple name defined in two packages, neither being the
    // referrer's own package). Ambiguous references must be disambiguated with $gen:<FQN>.
    if (checkRefs) {
      val definitionNames = definitions.map(_.name).toSet
      val byName = definitions.groupBy(_.name)
      definitions.foreach { definition =>
        val fromPackage = definition.fullPackage(basePackage)
        val refs = collectReferenceNames(definition.templateType)
        refs.foreach { refName =>
          val candidates = byName.getOrElse(refName, Nil)
          if (candidates.isEmpty) {
            if (!definitionNames.contains(refName)) {
              errors = s"Definition '${definition.name}' references undefined type: $refName" :: errors
            }
          } else if (candidates.length > 1 && !candidates.exists(_.fullPackage(basePackage) == fromPackage)) {
            val pkgs = candidates.map(_.fullPackage(basePackage)).sorted.mkString(", ")
            errors = s"Definition '${definition.name}' references ambiguous type '$refName' " +
              s"(defined in: $pkgs); disambiguate with \\$$gen:<fully.qualified.Name>" :: errors
          }
        }
      }
    }
    
    errors.reverse
  }
  
  /**
   * Collects every simple type-reference name used within a template type (ReferenceType).
   */
  private def collectReferenceNames(templateType: TemplateType): Set[String] = {
    def findInType(tt: TemplateType): Set[String] = tt match {
      case ReferenceType(typeName) => Set(typeName)
      case ArrayType(elementType, _) => findInType(elementType)
      case MapType(valueType) => findInType(valueType)
      case ObjectType(fields, _) => fields.values.flatMap(f => findInType(f.fieldType)).toSet
      case TypeDiscriminator(_, variants, commonFields, _, _, _, _, _) =>
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
    definition: TemplateDefinition
  ): MultiTemplate = {
    MultiTemplate(basePackage, List(definition))
  }

  /**
   * Combines templates into one shared definition space so $ref resolves across files.
   * Each definition's package is flattened into an absolute subPackage; the base package is empty.
   */
  def combine(templates: List[MultiTemplate]): MultiTemplate = {
    val absolute = templates.flatMap { t =>
      t.definitions.map(d => d.copy(subPackage = Some(d.fullPackage(t.basePackage))))
    }
    val base = commonPrefix(templates.map(_.basePackage).filter(_.nonEmpty))
    val defs = absolute.map { d =>
      val full = d.subPackage.getOrElse("")
      val sub = if (base.nonEmpty && full == base) "" else if (base.nonEmpty) full.stripPrefix(base + ".") else full
      d.copy(subPackage = if (sub.isEmpty) None else Some(sub))
    }
    // Generation settings: take any non-default value across files so a setting declared in
    // any template is honored regardless of file-discovery order.
    def firstNonDefault[A](values: List[A], default: A): A = values.find(_ != default).getOrElse(default)
    val useOptionTypes = firstNonDefault(templates.map(_.useOptionTypes), true)
    val listType = firstNonDefault(templates.map(_.listType), "List")
    val fromJsonStyle = firstNonDefault(templates.map(_.fromJsonStyle), "either")
    val dateType = firstNonDefault(templates.map(_.dateType), "java.time.LocalDate")
    val dateTimeType = firstNonDefault(templates.map(_.dateTimeType), "java.time.LocalDateTime")
    val mapType = firstNonDefault(templates.map(_.mapType), "Map")
    MultiTemplate(base, defs, useOptionTypes, listType, fromJsonStyle, dateType, dateTimeType, mapType)
  }

  private def commonPrefix(pkgs: List[String]): String = {
    if (pkgs.isEmpty) ""
    else pkgs.map(_.split('.').toList).reduce { (a, b) =>
      a.zip(b).takeWhile { case (x, y) => x == y }.map(_._1)
    }.mkString(".")
  }
}