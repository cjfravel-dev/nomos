package dev.cjfravel.chisel.generation

import dev.cjfravel.chisel.model._

/**
 * Generates Scala case classes from Chisel templates
 */
class CodeGenerator(config: GeneratorConfig) {

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
    template.templateType match {
      case disc: TypeDiscriminator =>
        generateDiscriminator(template.name, disc, packageName)
      case obj: ObjectType =>
        generateObject(template.name, obj, packageName, None)
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
   * Generates code for an object type (case class)
   */
  private def generateObject(
    name: String,
    objectType: ObjectType,
    packageName: String,
    parent: Option[String]
  ): Either[GeneratorError, List[GeneratedFile]] = {
    
    val builder = ScalaCodeBuilder()
    
    // Package declaration
    builder.line(s"package $packageName")
    builder.emptyLine()

    // Generate case class
    val fieldList = objectType.fields.map { case (fieldName, fieldDef) =>
      (ScalaCodeBuilder.escapeKeyword(fieldName), scalaType(fieldDef.fieldType, fieldDef.optional))
    }.toList

    builder.caseClass(name, fieldList, parent)

    val content = builder.build()
    val file = GeneratedFile(packageName, name, content)
    
    Right(List(file))
  }

  /**
   * Converts a TemplateType to its Scala type representation
   */
  private def scalaType(templateType: TemplateType, optional: Boolean): String = {
    val baseType = templateType match {
      case StringType(_) => "String"
      case NumberType(_) => "Double"
      case BooleanType() => "Boolean"
      case ArrayType(elementType) => s"List[${scalaType(elementType, optional = false)}]"
      case ObjectType(_) => "???" // This would need nested type generation
      case RecursiveRef(typeName) => typeName
      case TypeDiscriminator(_, _, _, _) => "???" // This would need nested type generation
    }

    if (optional) {
      s"Option[$baseType]"
    } else {
      baseType
    }
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