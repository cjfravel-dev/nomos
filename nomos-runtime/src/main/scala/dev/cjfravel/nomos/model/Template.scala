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
 * @param listType The collection type to use for arrays: "List" or "Array" (default: "List")
 * @param visibility Optional access modifier (e.g. "private[incentives]") prepended to every
 *   generated top-level definition; absent means public
 */
case class MultiTemplate(
  basePackage: String,
  definitions: List[TemplateDefinition],
  listType: String = "List",
  fromJsonStyle: String = "either",
  dateType: String = "java.time.LocalDate",
  dateTimeType: String = "java.time.LocalDateTime",
  visibility: Option[String] = None
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
    
    // Validate discriminator option consistency (e.g. discriminatorEnum requires an emitted,
    // fixed-value discriminator field).
    definitions.foreach { definition =>
      definition.templateType match {
        case d: TypeDiscriminator if d.discriminatorEnum.isDefined =>
          val ctx = s"Definition '${definition.name}'"
          if (!d.includeInOutput)
            errors = s"$ctx: discriminatorEnum requires includeDiscriminator to be true" :: errors
          if (d.variantMatch == "prefix")
            errors = s"$ctx: discriminatorEnum is incompatible with variantMatch 'prefix' (parameterized values are not a fixed enum set)" :: errors
        case _ =>
      }
    }
    
    // Validate the optional visibility modifier is a safe Scala access modifier (it is emitted
    // verbatim into generated source, so it must not be able to break out of that position).
    visibility.foreach { v =>
      if (MultiTemplate.ValidVisibility.findFirstIn(v).isEmpty)
        errors = s"Invalid visibility modifier: '$v' (expected e.g. private, protected, private[pkg])" :: errors
    }

    // Validate the array collection type resolves to a decoder the generator actually emits. Field
    // types honor listType, but the emitted decoders only produce List/Array for arrays; any other
    // value yields a case-class field whose type does not match its decoder, so the generated code
    // fails to compile. Reject unsupported values here with a clear message.
    if (!MultiTemplate.SupportedListTypes.contains(listType))
      errors = s"Unsupported listType: '$listType' (supported: ${MultiTemplate.SupportedListTypes.toList.sorted.mkString(", ")})" :: errors

    errors.reverse
  }

  /**
   * Collects every simple type-reference name used within a template type (ReferenceType and
   * RecursiveRef), so build-time validation can flag an unresolved or ambiguous reference.
   */
  private def collectReferenceNames(templateType: TemplateType): Set[String] = {
    def findInType(tt: TemplateType): Set[String] = tt match {
      case ReferenceType(typeName) => Set(typeName)
      case RecursiveRef(typeName) => Set(typeName)
      case ArrayType(elementType, _) => findInType(elementType)
      case MapType(valueType) => findInType(valueType)
      case ObjectType(fields, _) => fields.values.flatMap(f => findInType(f.fieldType)).toSet
      case TypeDiscriminator(_, variants, commonFields, _, _, _, _, _, _) =>
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
   * A safe Scala access modifier: `private` / `protected`, optionally qualified with `[this]` or a
   * (dotted) enclosing package/type name. Restricts what may be emitted verbatim as a definition
   * prefix so a template cannot inject arbitrary source.
   */
  val ValidVisibility = "^(private|protected)(\\[(this|[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*)\\])?$".r

  /**
   * Array collection types the generator can emit a matching decoder for. Field types honor
   * `listType`, but the decoder only produces `List` (default) or `Array`, so only these compile.
   */
  val SupportedListTypes: Set[String] = Set("List", "Array")

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
   *
   * Generation settings (listType, dateType, fromJsonStyle, ...) are project-wide: each may be set
   * in one file and applies to the whole project. A setting given conflicting non-default values in
   * different files is a build error, so the merged result is unambiguous regardless of
   * file-discovery order.
   */
  def combine(templates: List[MultiTemplate]): Either[String, MultiTemplate] = {
    val absolute = templates.flatMap { t =>
      t.definitions.map(d => d.copy(subPackage = Some(d.fullPackage(t.basePackage))))
    }
    val base = commonPrefix(templates.map(_.basePackage).filter(_.nonEmpty))
    val defs = absolute.map { d =>
      val full = d.subPackage.getOrElse("")
      val sub = if (base.nonEmpty && full == base) "" else if (base.nonEmpty) full.stripPrefix(base + ".") else full
      d.copy(subPackage = if (sub.isEmpty) None else Some(sub))
    }
    // A project-wide setting resolves to its single non-default value (or the default if none set).
    // Two files giving it different non-default values is a conflict.
    def resolve[A](name: String, values: List[A], default: A): Either[String, A] =
      values.filter(_ != default).distinct match {
        case Nil            => Right(default)
        case single :: Nil  => Right(single)
        case many           => Left(s"$name (${many.mkString(" vs ")})")
      }
    val listType = resolve("listType", templates.map(_.listType), "List")
    val fromJsonStyle = resolve("fromJsonStyle", templates.map(_.fromJsonStyle), "either")
    val dateType = resolve("dateType", templates.map(_.dateType), "java.time.LocalDate")
    val dateTimeType = resolve("dateTimeType", templates.map(_.dateTimeType), "java.time.LocalDateTime")
    val visibility = resolve("visibility", templates.map(_.visibility), None)
    val conflicts = List(listType, fromJsonStyle, dateType, dateTimeType, visibility)
      .collect { case Left(msg) => msg }
    if (conflicts.nonEmpty)
      Left("Conflicting project-wide settings across template files: " + conflicts.mkString("; ") +
        ". Each setting must resolve to a single value across all files.")
    else
      Right(MultiTemplate(base, defs, listType.right.get,
        fromJsonStyle.right.get, dateType.right.get, dateTimeType.right.get,
        visibility.right.get))
  }

  private def commonPrefix(pkgs: List[String]): String = {
    if (pkgs.isEmpty) ""
    else pkgs.map(_.split('.').toList).reduce { (a, b) =>
      a.zip(b).takeWhile { case (x, y) => x == y }.map(_._1)
    }.mkString(".")
  }
}