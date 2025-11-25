package dev.cjfravel.nomos.generation

import dev.cjfravel.nomos.model._

/**
 * Generates Scala case classes from Nomos templates
 */
class CodeGenerator(config: GeneratorConfig) {

  /**
   * Generates code from a multi-template with multiple definitions
   */
  def generateMulti(multiTemplate: MultiTemplate): Either[GeneratorError, List[GeneratedFile]] = {
    // Validate the multi-template
    multiTemplate.validate() match {
      case errors if errors.nonEmpty =>
        return Left(GeneratorError.TemplateError(errors.mkString(", ")))
      case _ =>
    }
    
    // Get definitions map for reference resolution
    val definitionsMap = multiTemplate.definitionsMap
    
    // Generate a file for each definition
    val fileResults = multiTemplate.definitions.map { definition =>
      generateFromDefinition(definition, multiTemplate.basePackage, definitionsMap)
    }
    
    // Check for errors
    val errors = fileResults.collect { case Left(err) => err }
    if (errors.nonEmpty) {
      Left(errors.head) // Return first error
    } else {
      val generatedFiles = fileResults.collect { case Right(file) => file }
      
      // Always generate NomosFormats with Jackson serialization
      val chiselFormatsFile = generateNomosFormats(multiTemplate.basePackage)
      Right(chiselFormatsFile :: generatedFiles)
    }
  }

  /**
   * Generates code from a template
   */
  def generate(template: Template): Either[GeneratorError, List[GeneratedFile]] = {
    // Validate configuration
    config.validate() match {
      case errors if errors.nonEmpty =>
        return Left(GeneratorError.ConfigError(errors.mkString(", ")))
      case _ =>
    }

    // Validate template name
    template.validateName() match {
      case Some(error) =>
        return Left(GeneratorError.TemplateError(error))
      case None =>
    }

    // Get the full package path
    val packageName = template.fullPackage(config.basePackage)

    // Generate code based on template type
    // Track nested types to generate them all
    val nestedTypes = scala.collection.mutable.Map.empty[String, ObjectType]
    
    template.templateType match {
      case disc: TypeDiscriminator =>
        generateDiscriminatorWithNested(template.name, disc, packageName, nestedTypes)
      case obj: ObjectType =>
        generateObjectWithNested(template.name, obj, packageName, None, nestedTypes)
      case _ =>
        Left(GeneratorError.TemplateError(
          s"Root template must be an ObjectType or TypeDiscriminator, got ${template.templateType.getClass.getSimpleName}"
        ))
    }
  }

  /**
   * Generates code for a type discriminator (sealed trait + case classes)
   */
  private def generateDiscriminator(
    name: String,
    discriminator: TypeDiscriminator,
    packageName: String
  ): Either[GeneratorError, List[GeneratedFile]] = {
    
    // Check for ambiguity if discriminator is not included
    if (!discriminator.includeInOutput) {
      detectAmbiguity(discriminator) match {
        case Some(error) => return Left(GeneratorError.AmbiguityError(error))
        case None =>
      }
    }

    val builder = ScalaCodeBuilder()
    
    // Package declaration
    builder.line(s"package $packageName")
    builder.emptyLine()

    // Generate sealed trait
    builder.sealedTrait(name)
    builder.emptyLine()

    // Generate case classes for each variant
    discriminator.variants.foreach { case (variantName, variantType) =>
      val caseClassName = ScalaCodeBuilder.toPascalCase(variantName)
      
      // Combine common fields with variant-specific fields
      val allFields = discriminator.commonFields ++ variantType.fields
      
      // Add discriminator field if requested
      val fieldsWithDiscriminator = if (discriminator.includeInOutput) {
        Map(discriminator.fieldName -> FieldDef(StringType(), optional = false)) ++ allFields
      } else {
        allFields
      }
      
      // Convert to field list
      val fieldList = fieldsWithDiscriminator.map { case (fieldName, fieldDef) =>
        (ScalaCodeBuilder.escapeKeyword(fieldName), scalaType(fieldDef.fieldType, fieldDef.optional))
      }.toList

      builder.caseClass(caseClassName, fieldList, Some(name))
      builder.emptyLine()
    }

    val content = builder.build()
    val file = GeneratedFile(packageName, name, content)
    
    Right(List(file))
  }

  /**
   * Generates code for an object type with nested type tracking
   */
  private def generateObjectWithNested(
    name: String,
    objectType: ObjectType,
    packageName: String,
    parent: Option[String],
    nestedTypes: scala.collection.mutable.Map[String, ObjectType]
  ): Either[GeneratorError, List[GeneratedFile]] = {
    
    // Collect nested object types
    collectNestedTypes(objectType, name, nestedTypes)
    
    val builder = ScalaCodeBuilder()
    
    // Package declaration
    builder.line(s"package $packageName")
    builder.emptyLine()

    // Generate case class
    val fieldList = objectType.fields.map { case (fieldName, fieldDef) =>
      val typeName = scalaTypeWithNested(fieldDef.fieldType, fieldName, fieldDef.optional, name)
      (ScalaCodeBuilder.escapeKeyword(fieldName), typeName)
    }.toList

    builder.caseClass(name, fieldList, parent)

    // Generate nested case classes
    nestedTypes.foreach { case (nestedName, nestedObj) =>
      builder.emptyLine()
      val nestedFieldList = nestedObj.fields.map { case (fieldName, fieldDef) =>
        val typeName = scalaTypeWithNested(fieldDef.fieldType, fieldName, fieldDef.optional, nestedName)
        (ScalaCodeBuilder.escapeKeyword(fieldName), typeName)
      }.toList
      builder.caseClass(nestedName, nestedFieldList, None)
    }

    val content = builder.build()
    val file = GeneratedFile(packageName, name, content)
    
    Right(List(file))
  }

  /**
   * Generates code for a discriminator with nested type tracking
   */
  private def generateDiscriminatorWithNested(
    name: String,
    discriminator: TypeDiscriminator,
    packageName: String,
    nestedTypes: scala.collection.mutable.Map[String, ObjectType]
  ): Either[GeneratorError, List[GeneratedFile]] = {
    
    // Check for ambiguity if discriminator is not included
    if (!discriminator.includeInOutput) {
      detectAmbiguity(discriminator) match {
        case Some(error) => return Left(GeneratorError.AmbiguityError(error))
        case None =>
      }
    }

    // Collect nested types from all variants
    discriminator.variants.values.foreach { variantType =>
      collectNestedTypes(variantType, name, nestedTypes)
    }

    val builder = ScalaCodeBuilder()
    
    // Package declaration
    builder.line(s"package $packageName")
    builder.emptyLine()

    // Generate sealed trait
    builder.sealedTrait(name)
    builder.emptyLine()

    // Generate case classes for each variant
    discriminator.variants.foreach { case (variantName, variantType) =>
      val caseClassName = ScalaCodeBuilder.toPascalCase(variantName)
      
      // Combine common fields with variant-specific fields
      val allFields = discriminator.commonFields ++ variantType.fields
      
      // Add discriminator field if requested
      val fieldsWithDiscriminator = if (discriminator.includeInOutput) {
        Map(discriminator.fieldName -> FieldDef(StringType(), optional = false)) ++ allFields
      } else {
        allFields
      }
      
      // Convert to field list
      val fieldList = fieldsWithDiscriminator.map { case (fieldName, fieldDef) =>
        val typeName = scalaTypeWithNested(fieldDef.fieldType, fieldName, fieldDef.optional, caseClassName)
        (ScalaCodeBuilder.escapeKeyword(fieldName), typeName)
      }.toList

      builder.caseClass(caseClassName, fieldList, Some(name))
      builder.emptyLine()
    }

    // Generate nested case classes
    nestedTypes.foreach { case (nestedName, nestedObj) =>
      val nestedFieldList = nestedObj.fields.map { case (fieldName, fieldDef) =>
        val typeName = scalaTypeWithNested(fieldDef.fieldType, fieldName, fieldDef.optional, nestedName)
        (ScalaCodeBuilder.escapeKeyword(fieldName), typeName)
      }.toList
      builder.caseClass(nestedName, nestedFieldList, None)
      builder.emptyLine()
    }

    val content = builder.build()
    val file = GeneratedFile(packageName, name, content)
    
    Right(List(file))
  }

  /**
   * Collects nested ObjectTypes and assigns them names
   */
  private def collectNestedTypes(
    objectType: ObjectType,
    parentName: String,
    nestedTypes: scala.collection.mutable.Map[String, ObjectType]
  ): Unit = {
    objectType.fields.foreach { case (fieldName, fieldDef) =>
      fieldDef.fieldType match {
        case nested @ ObjectType(_) =>
          val nestedName = ScalaCodeBuilder.toPascalCase(fieldName)
          if (!nestedTypes.contains(nestedName)) {
            nestedTypes(nestedName) = nested
            // Recursively collect nested types
            collectNestedTypes(nested, nestedName, nestedTypes)
          }
        case ArrayType(nested @ ObjectType(_)) =>
          val nestedName = ScalaCodeBuilder.toPascalCase(fieldName).stripSuffix("s")
          if (!nestedTypes.contains(nestedName)) {
            nestedTypes(nestedName) = nested
            collectNestedTypes(nested, nestedName, nestedTypes)
          }
        case _ => // Other types don't need nested generation
      }
    }
  }

  /**
   * Converts a TemplateType to its Scala type with nested type names
   */
  private def scalaTypeWithNested(templateType: TemplateType, fieldName: String, optional: Boolean, parentName: String): String = {
    val baseType = templateType match {
      case StringType(_) => "String"
      case NumberType(_) => "Double"
      case BooleanType() => "Boolean"
      case ArrayType(obj @ ObjectType(_)) =>
        val elementTypeName = ScalaCodeBuilder.toPascalCase(fieldName).stripSuffix("s")
        s"${config.listType}[$elementTypeName]"
      case ArrayType(elementType) =>
        s"${config.listType}[${scalaTypeWithNested(elementType, fieldName, optional = false, parentName)}]"
      case obj @ ObjectType(_) =>
        ScalaCodeBuilder.toPascalCase(fieldName)
      case RecursiveRef(typeName) => typeName
      case ReferenceType(typeName) => typeName
      case TypeDiscriminator(_, _, _, _, _) =>
        ScalaCodeBuilder.toPascalCase(fieldName)
    }

    if (optional && config.useOptionTypes) {
      s"Option[$baseType]"
    } else {
      baseType
    }
  }

  /**
   * Legacy method for backward compatibility
   */
  private def generateObject(
    name: String,
    objectType: ObjectType,
    packageName: String,
    parent: Option[String]
  ): Either[GeneratorError, List[GeneratedFile]] = {
    val nestedTypes = scala.collection.mutable.Map.empty[String, ObjectType]
    generateObjectWithNested(name, objectType, packageName, parent, nestedTypes)
  }

  /**
   * Converts a TemplateType to its Scala type representation
   */
  private def scalaType(templateType: TemplateType, optional: Boolean): String = {
    val baseType = templateType match {
      case StringType(_) => "String"
      case NumberType(_) => "Double"
      case BooleanType() => "Boolean"
      case ArrayType(elementType) => s"${config.listType}[${scalaType(elementType, optional = false)}]"
      case ObjectType(_) => "???" // This would need nested type generation
      case RecursiveRef(typeName) => typeName
      case ReferenceType(typeName) => typeName
      case TypeDiscriminator(_, _, _, _, _) => "???" // This would need nested type generation
    }

    if (optional && config.useOptionTypes) {
      s"Option[$baseType]"
    } else {
      baseType
    }
  }

  /**
   * Generates code for a single definition within a multi-template
   */
  private def generateFromDefinition(
    definition: TemplateDefinition,
    basePackage: String,
    definitionsMap: Map[String, TemplateDefinition]
  ): Either[GeneratorError, GeneratedFile] = {
    
    // Validate definition name
    definition.validateName() match {
      case Some(error) =>
        return Left(GeneratorError.TemplateError(error))
      case None =>
    }
    
    // Get the full package path
    val packageName = definition.fullPackage(basePackage)
    
    // Collect all referenced types to generate imports
    val referencedTypes = collectReferences(definition.templateType)
    val imports = generateImports(packageName, basePackage, referencedTypes, definitionsMap)
    
    val builder = ScalaCodeBuilder()
    
    // Package declaration
    builder.line(s"package $packageName")
    builder.emptyLine()
    
    // No Jackson imports needed in generated code - NomosFormats provides everything
    val allImports = imports
    
    if (allImports.nonEmpty) {
      allImports.foreach(builder.line)
      builder.emptyLine()
    }
    
    // Generate based on template type
    definition.templateType match {
      case obj: ObjectType =>
        generateObjectForDefinition(definition.name, obj, builder, definitionsMap, basePackage, packageName)
      
      case disc: TypeDiscriminator =>
        generateDiscriminatorForDefinition(definition.name, disc, builder, definitionsMap, basePackage, packageName)
      
      case _ =>
        return Left(GeneratorError.TemplateError(
          s"Definition '${definition.name}' must be an ObjectType or TypeDiscriminator, got ${definition.templateType.getClass.getSimpleName}"
        ))
    }
    
    val content = builder.build()
    Right(GeneratedFile(packageName, definition.name, content))
  }

  /**
   * Generates an object type for a definition (without nested types - those are separate definitions)
   */
  private def generateObjectForDefinition(
    name: String,
    objectType: ObjectType,
    builder: ScalaCodeBuilder,
    definitionsMap: Map[String, TemplateDefinition],
    basePackage: String,
    currentPackage: String
  ): Unit = {
    val fieldList = objectType.fields.map { case (fieldName, fieldDef) =>
      val typeName = scalaTypeForDefinition(fieldDef.fieldType, fieldDef.optional, definitionsMap)
      (ScalaCodeBuilder.escapeKeyword(fieldName), typeName)
    }.toList
    
    builder.caseClass(name, fieldList, None)
    
    // Always generate companion object with Jackson serialization
    builder.emptyLine()
    builder.companionObject(name) {
      // Only add import if not in basePackage
      if (currentPackage != basePackage) {
        builder.line(s"import $basePackage.NomosFormats")
      }
      builder.line("import NomosFormats._")
      builder.emptyLine()
      
      // Generate simple fromJson using Jackson Scala module
      builder.line("def fromJson(json: String): Either[String, " + name + "] = {")
      builder.indent()
      builder.line("try {")
      builder.indent()
      builder.line("Right(mapper.readValue(json, classOf[" + name + "]))")
      builder.dedent()
      builder.line("} catch {")
      builder.indent()
      builder.line("case e: Exception => Left(s\"Failed to parse JSON: ${e.getMessage}\")")
      builder.dedent()
      builder.line("}")
      builder.dedent()
      builder.line("}")
      
      builder.emptyLine()
      builder.line("def toJson(obj: " + name + "): String = {")
      builder.indent()
      builder.line("mapper.writeValueAsString(obj)")
      builder.dedent()
      builder.line("}")
    }
  }


  /**
   * Generates a discriminator for a definition
   */
  private def generateDiscriminatorForDefinition(
    name: String,
    discriminator: TypeDiscriminator,
    builder: ScalaCodeBuilder,
    definitionsMap: Map[String, TemplateDefinition],
    basePackage: String,
    currentPackage: String
  ): Unit = {
    // Check for ambiguity if discriminator is not included
    if (!discriminator.includeInOutput) {
      detectAmbiguity(discriminator) match {
        case Some(error) => throw new Exception(error) // This will be caught by caller
        case None =>
      }
    }
    
    // If variantNames is provided, use custom naming and grouping logic
    if (discriminator.variantNames.nonEmpty) {
      generateDiscriminatorWithVariantNames(name, discriminator, builder, definitionsMap, basePackage, currentPackage)
    } else {
      // Original behavior: one case class per variant
      generateDiscriminatorOriginal(name, discriminator, builder, definitionsMap, basePackage, currentPackage)
    }
  }

  /**
   * Generates discriminator with original behavior (one case class per variant)
   */
  private def generateDiscriminatorOriginal(
    name: String,
    discriminator: TypeDiscriminator,
    builder: ScalaCodeBuilder,
    definitionsMap: Map[String, TemplateDefinition],
    basePackage: String,
    currentPackage: String
  ): Unit = {
    // Generate sealed trait
    builder.sealedTrait(name)
    builder.emptyLine()
    
    // Generate case classes for each variant
    discriminator.variants.foreach { case (variantName, variantType) =>
      val caseClassName = ScalaCodeBuilder.toPascalCase(variantName)
      
      // Combine common fields with variant-specific fields
      val allFields = discriminator.commonFields ++ variantType.fields
      
      // Add discriminator field if requested
      val fieldsWithDiscriminator = if (discriminator.includeInOutput) {
        Map(discriminator.fieldName -> FieldDef(StringType(), optional = false)) ++ allFields
      } else {
        allFields
      }
      
      // Convert to field list
      val fieldList = fieldsWithDiscriminator.map { case (fieldName, fieldDef) =>
        val typeName = scalaTypeForDefinition(fieldDef.fieldType, fieldDef.optional, definitionsMap)
        (ScalaCodeBuilder.escapeKeyword(fieldName), typeName)
      }.toList
      
      builder.caseClass(caseClassName, fieldList, Some(name))
      builder.emptyLine()
    }
    
    // Always generate companion object with Jackson deserialization
    val variantMap = discriminator.variants.map { case (variantName, _) =>
      (variantName, ScalaCodeBuilder.toPascalCase(variantName))
    }
    
    // Generate companion object with simple Jackson Scala module deserialization
    builder.companionObject(name) {
      // Only add import if not in basePackage
      if (currentPackage != basePackage) {
        builder.line(s"import $basePackage.NomosFormats")
      }
      builder.line("import NomosFormats._")
      builder.line("import com.fasterxml.jackson.databind.JsonNode")
      builder.emptyLine()
      builder.line("def fromJson(json: String): Either[String, " + name + "] = {")
      builder.indent()
      builder.line("try {")
      builder.indent()
      builder.line("val jsonNode = mapper.readTree(json)")
      builder.line("val discriminatorValue = jsonNode.get(\"" + discriminator.fieldName + "\").asText()")
      builder.line("discriminatorValue match {")
      builder.indent()
      variantMap.foreach { case (variantKey, className) =>
        builder.line("case \"" + variantKey + "\" => Right(mapper.treeToValue(jsonNode, classOf[" + className + "]))")
      }
      builder.line("case other => Left(s\"Unknown " + discriminator.fieldName + " value: $other\")")
      builder.dedent()
      builder.line("}")
      builder.dedent()
      builder.line("} catch {")
      builder.indent()
      builder.line("case e: Exception => Left(s\"Failed to parse JSON: ${e.getMessage}\")")
      builder.dedent()
      builder.line("}")
      builder.dedent()
      builder.line("}")
      builder.emptyLine()
      builder.line("def toJson(obj: " + name + "): String = {")
      builder.indent()
      builder.line("mapper.writeValueAsString(obj)")
      builder.dedent()
      builder.line("}")
    }
    builder.emptyLine()
  }

  /**
   * Generates discriminator with custom variant names
   * Groups variants by their mapped names and puts common fields on the trait
   */
  private def generateDiscriminatorWithVariantNames(
    name: String,
    discriminator: TypeDiscriminator,
    builder: ScalaCodeBuilder,
    definitionsMap: Map[String, TemplateDefinition],
    basePackage: String,
    currentPackage: String
  ): Unit = {
    // Get the trait fields (discriminator + common fields)
    val traitFieldNames = Set(discriminator.fieldName) ++ discriminator.commonFields.keys
    
    // Generate sealed trait with common fields as abstract vals
    if (discriminator.commonFields.nonEmpty) {
      val commonFieldList = discriminator.commonFields.map { case (fieldName, fieldDef) =>
        val typeName = scalaTypeForDefinition(fieldDef.fieldType, fieldDef.optional, definitionsMap)
        (ScalaCodeBuilder.escapeKeyword(fieldName), typeName)
      }.toList
      
      // Always include discriminator field on trait
      val allTraitFields = (ScalaCodeBuilder.escapeKeyword(discriminator.fieldName), "String") :: commonFieldList
      builder.sealedTraitWithFields(name, allTraitFields)
    } else {
      // Just discriminator field on trait
      builder.sealedTraitWithFields(name, List((ScalaCodeBuilder.escapeKeyword(discriminator.fieldName), "String")))
    }
    builder.emptyLine()
    
    // Group variants by their mapped class names
    val variantsByClassName = discriminator.variants.groupBy { case (variantKey, _) =>
      discriminator.variantNames.getOrElse(variantKey, ScalaCodeBuilder.toPascalCase(variantKey))
    }
    
    // Generate one case class per unique mapped name
    variantsByClassName.foreach { case (caseClassName, variants) =>
      // Merge all variant-specific fields (they should be compatible if mapping to same class)
      val variantFields = variants.values.flatMap(_.fields).toMap
      
      // Build field list with override for trait fields
      val discriminatorField = (ScalaCodeBuilder.escapeKeyword(discriminator.fieldName), "String", true)  // override = true
      val commonFieldsList = discriminator.commonFields.map { case (fieldName, fieldDef) =>
        val typeName = scalaTypeForDefinition(fieldDef.fieldType, fieldDef.optional, definitionsMap)
        (ScalaCodeBuilder.escapeKeyword(fieldName), typeName, true)  // override = true
      }.toList
      val variantFieldsList = variantFields.map { case (fieldName, fieldDef) =>
        val typeName = scalaTypeForDefinition(fieldDef.fieldType, fieldDef.optional, definitionsMap)
        (ScalaCodeBuilder.escapeKeyword(fieldName), typeName, false)  // override = false
      }.toList
      
      val allFields = discriminatorField :: (commonFieldsList ++ variantFieldsList)
      
      builder.caseClassWithOverride(caseClassName, allFields, Some(name))
      builder.emptyLine()
    }
    
    // Always generate companion object with custom serializer
    // Map discriminator values to class names (respecting variantNames mapping)
    val variantMap = discriminator.variants.map { case (variantKey, _) =>
      val className = discriminator.variantNames.getOrElse(variantKey, ScalaCodeBuilder.toPascalCase(variantKey))
      (variantKey, className)
    }
    
    // Generate companion object with Jackson deserialization
    builder.companionObject(name) {
      // Only add import if not in basePackage
      if (currentPackage != basePackage) {
        builder.line(s"import $basePackage.NomosFormats")
      }
      builder.line("import NomosFormats._")
      builder.emptyLine()
      builder.customSerializer(name, discriminator.fieldName, variantMap)
    }
    builder.emptyLine()
  }

  /**
   * Converts a TemplateType to Scala type for multi-definition mode
   */
  private def scalaTypeForDefinition(
    templateType: TemplateType,
    optional: Boolean,
    definitionsMap: Map[String, TemplateDefinition]
  ): String = {
    val baseType = templateType match {
      case StringType(_) => "String"
      case NumberType(_) => "Double"
      case BooleanType() => "Boolean"
      case ArrayType(elementType) =>
        s"${config.listType}[${scalaTypeForDefinition(elementType, optional = false, definitionsMap)}]"
      case ReferenceType(typeName) => typeName
      case RecursiveRef(typeName) => typeName
      case ObjectType(_) =>
        "???" // Inline objects not supported in multi-definition mode
      case TypeDiscriminator(_, _, _, _, _) =>
        "???" // Inline discriminators not supported in multi-definition mode
    }
    
    if (optional && config.useOptionTypes) {
      s"Option[$baseType]"
    } else {
      baseType
    }
  }

  /**
   * Collects all ReferenceType names from a template type
   */
  private def collectReferences(templateType: TemplateType): Set[String] = {
    templateType match {
      case ReferenceType(typeName) => Set(typeName)
      case ArrayType(elementType) => collectReferences(elementType)
      case ObjectType(fields) =>
        fields.values.flatMap(f => collectReferences(f.fieldType)).toSet
      case TypeDiscriminator(_, variants, commonFields, _, _) =>
        val variantRefs = variants.values.flatMap(v => v.fields.values.flatMap(f => collectReferences(f.fieldType)))
        val commonRefs = commonFields.values.flatMap(f => collectReferences(f.fieldType))
        variantRefs.toSet ++ commonRefs
      case _ => Set.empty
    }
  }

  /**
   * Generates import statements for referenced types in different packages
   */
  private def generateImports(
    currentPackage: String,
    basePackage: String,
    referencedTypes: Set[String],
    definitionsMap: Map[String, TemplateDefinition]
  ): List[String] = {
    referencedTypes.flatMap { refTypeName =>
      definitionsMap.get(refTypeName).flatMap { refDef =>
        val refPackage = refDef.fullPackage(basePackage)
        if (refPackage != currentPackage) {
          Some(s"import $refPackage.$refTypeName")
        } else {
          None
        }
      }
    }.toList.sorted
  }

  /**
   * Detects if variants are ambiguous when discriminator is not included
   */
  private def detectAmbiguity(discriminator: TypeDiscriminator): Option[String] = {
    val variants = discriminator.variants.values.toList
    
    // Check if any two variants have the same field structure
    for {
      i <- variants.indices
      j <- (i + 1) until variants.length
    } {
      val variant1 = variants(i)
      val variant2 = variants(j)
      
      // Combine with common fields
      val fields1 = discriminator.commonFields ++ variant1.fields
      val fields2 = discriminator.commonFields ++ variant2.fields
      
      if (fields1.keySet == fields2.keySet) {
        // Same field names - check if types are the same
        val sameTypes = fields1.keySet.forall { key =>
          (fields1.get(key), fields2.get(key)) match {
            case (Some(f1), Some(f2)) => 
              f1.fieldType.getClass == f2.fieldType.getClass && f1.optional == f2.optional
            case _ => false
          }
        }
        
        if (sameTypes) {
          val variantNames = discriminator.variants.filter { case (_, obj) =>
            obj == variant1 || obj == variant2
          }.keys.mkString(", ")
          
          return Some(
            s"Variants [$variantNames] are indistinguishable without the discriminator field '${discriminator.fieldName}'. " +
            s"Either include the discriminator field or make the variants have different field structures."
          )
        }
      }
    }
    
    None
  }
  
  /**
   * Generates NomosFormats object with standard Jackson and Scala module.
   * With BOM approach, we can use Jackson directly without shading.
   */
  private def generateNomosFormats(basePackage: String): GeneratedFile = {
    val builder = ScalaCodeBuilder()
    
    builder.line(s"package $basePackage")
    builder.emptyLine()
    builder.line("import com.fasterxml.jackson.databind.ObjectMapper")
    builder.line("import com.fasterxml.jackson.module.scala.DefaultScalaModule")
    builder.emptyLine()
    builder.line("/**")
    builder.line(" * Provides Jackson ObjectMapper with Scala module support.")
    builder.line(" * The Scala module enables automatic serialization/deserialization of case classes.")
    builder.line(" * Import NomosFormats._ to use the mapper in your code.")
    builder.line(" */")
    builder.line("object NomosFormats {")
    builder.indent()
    builder.line("// Create a shared ObjectMapper instance with Scala module")
    builder.line("val mapper: ObjectMapper = {")
    builder.indent()
    builder.line("val m = new ObjectMapper()")
    builder.line("m.registerModule(DefaultScalaModule)")
    builder.line("m")
    builder.dedent()
    builder.line("}")
    builder.dedent()
    builder.line("}")
    
    GeneratedFile(basePackage, "NomosFormats", builder.build())
  }
}

/**
 * Errors that can occur during code generation
 */
sealed trait GeneratorError {
  def message: String
}

object GeneratorError {
  case class ConfigError(message: String) extends GeneratorError
  case class TemplateError(message: String) extends GeneratorError
  case class AmbiguityError(message: String) extends GeneratorError
  case class IOError(message: String) extends GeneratorError
}