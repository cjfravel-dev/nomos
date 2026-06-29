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
      
      // Always generate NomosFormats with Jackson serialization and embedded template
      val nomosFormatsFile = generateNomosFormats(multiTemplate.basePackage, multiTemplate)
      Right(nomosFormatsFile :: generatedFiles)
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
      val withDefault = fieldDef.default.map(d => s"$typeName = $d").getOrElse(typeName)
      (ScalaCodeBuilder.escapeKeyword(fieldName), withDefault)
    }.toList
    
    builder.caseClass(name, fieldList, None)
    
    // Always generate companion object with Jackson serialization and validation
    builder.emptyLine()
    builder.companionObject(name) {
      // Only add import if not in basePackage
      if (currentPackage != basePackage) {
        builder.line(s"import $basePackage.NomosFormats")
      }
      builder.line("import NomosFormats._")
      builder.line("import dev.cjfravel.nomos.validation.ValidationError")
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
      
      builder.emptyLine()
      builder.line("/**")
      builder.line(" * Validates JSON against the embedded template and returns a parsed instance.")
      builder.line(" * This allows validation without needing the original template file.")
      builder.line(" */")
      builder.line("def validate(json: String): Either[List[ValidationError], " + name + "] = {")
      builder.indent()
      builder.line("validator.validate(json, \"" + currentPackage + "." + name + "\") match {")
      builder.indent()
      builder.line("case Right(_) => fromJson(json).left.map(err => List(ValidationError(\"root\", err, \"valid JSON\", json)))")
      builder.line("case Left(errors) => Left(errors)")
      builder.dedent()
      builder.line("}")
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
      generateDiscriminatorOriginal(name, discriminator, builder, definitionsMap, basePackage, currentPackage)
    }
  }

  /**
   * Generates a sealed trait with one case class per variant.
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
    
    // Generate companion object with simple Jackson Scala module deserialization and validation
    builder.companionObject(name) {
      // Only add import if not in basePackage
      if (currentPackage != basePackage) {
        builder.line(s"import $basePackage.NomosFormats")
      }
      builder.line("import NomosFormats._")
      builder.line("import dev.cjfravel.nomos.validation.ValidationError")
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
      builder.emptyLine()
      builder.line("/**")
      builder.line(" * Validates JSON against the embedded template and returns a parsed instance.")
      builder.line(" * This allows validation without needing the original template file.")
      builder.line(" */")
      builder.line("def validate(json: String): Either[List[ValidationError], " + name + "] = {")
      builder.indent()
      builder.line("validator.validate(json, \"" + currentPackage + "." + name + "\") match {")
      builder.indent()
      builder.line("case Right(_) => fromJson(json).left.map(err => List(ValidationError(\"root\", err, \"valid JSON\", json)))")
      builder.line("case Left(errors) => Left(errors)")
      builder.dedent()
      builder.line("}")
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
    
    // Generate companion object with Jackson deserialization and validation
    builder.companionObject(name) {
      // Only add import if not in basePackage
      if (currentPackage != basePackage) {
        builder.line(s"import $basePackage.NomosFormats")
      }
      builder.line("import NomosFormats._")
      builder.line("import dev.cjfravel.nomos.validation.ValidationError")
      builder.emptyLine()
      builder.customSerializer(name, discriminator.fieldName, variantMap)
      builder.emptyLine()
      builder.line("/**")
      builder.line(" * Validates JSON against the embedded template and returns a parsed instance.")
      builder.line(" * This allows validation without needing the original template file.")
      builder.line(" */")
      builder.line("def validate(json: String): Either[List[ValidationError], " + name + "] = {")
      builder.indent()
      builder.line("validator.validate(json, \"" + currentPackage + "." + name + "\") match {")
      builder.indent()
      builder.line("case Right(_) => fromJson(json).left.map(err => List(ValidationError(\"root\", err, \"valid JSON\", json)))")
      builder.line("case Left(errors) => Left(errors)")
      builder.dedent()
      builder.line("}")
      builder.dedent()
      builder.line("}")
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
      case IntType(_) => "Int"
      case LongType(_) => "Long"
      case DecimalType(_) => "BigDecimal"
      case BooleanType() => "Boolean"
      case ArrayType(elementType, _) =>
        s"${config.listType}[${scalaTypeForDefinition(elementType, optional = false, definitionsMap)}]"
      case MapType(valueType) =>
        s"Map[String, ${scalaTypeForDefinition(valueType, optional = false, definitionsMap)}]"
      case ReferenceType(typeName) => typeName
      case RecursiveRef(typeName) => typeName
      case ObjectType(_, _) =>
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
      case ArrayType(elementType, _) => collectReferences(elementType)
      case ObjectType(fields, _) =>
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
   * Generates the NomosFormats object: a shared Jackson ObjectMapper, the embedded
   * MultiTemplate, and a MultiValidator built from it for runtime validation.
   */
  private def generateNomosFormats(basePackage: String, multiTemplate: MultiTemplate): GeneratedFile = {
    val builder = ScalaCodeBuilder()
    
    builder.line(s"package $basePackage")
    builder.emptyLine()
    builder.line("import com.fasterxml.jackson.databind.ObjectMapper")
    builder.line("import com.fasterxml.jackson.module.scala.DefaultScalaModule")
    builder.line("import dev.cjfravel.nomos.model._")
    builder.line("import dev.cjfravel.nomos.validation.{MultiValidator, ValidationError}")
    builder.line("import com.fasterxml.jackson.databind.JsonNode")
    builder.line("import scala.collection.immutable.ListMap")
    builder.emptyLine()
    builder.line("/**")
    builder.line(" * Provides Jackson ObjectMapper with Scala module support.")
    builder.line(" * The Scala module enables automatic serialization/deserialization of case classes.")
    builder.line(" * Also contains the embedded template for runtime validation.")
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
    builder.emptyLine()
    builder.line("// Embedded template for runtime validation")
    builder.line("lazy val embeddedTemplate: MultiTemplate = {")
    builder.indent()
    val serializedTemplate = TemplateSerializer.serializeMultiTemplate(multiTemplate)
    serializedTemplate.split("\n").foreach(line => builder.line(line))
    builder.dedent()
    builder.line("}")
    builder.emptyLine()
    builder.line("// Validator instance using the embedded template")
    builder.line("lazy val validator: MultiValidator = new MultiValidator(embeddedTemplate)")
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