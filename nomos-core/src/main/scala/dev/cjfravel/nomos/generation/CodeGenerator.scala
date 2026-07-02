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

    // Reject template-derived names that are not valid Scala identifiers before emitting any
    // source. This prevents uncompilable output and closes path-traversal/code-injection vectors
    // where a name (e.g. an enum name used as a file name) flows into generated paths or source.
    collectNameErrors(multiTemplate) match {
      case errors if errors.nonEmpty =>
        return Left(GeneratorError.TemplateError(errors.mkString(", ")))
      case _ =>
    }

    // Reject inline (anonymous) objects/discriminators used as a field's type: multi-definition
    // codegen has no name to emit for them, so fail with a clear message instead of producing
    // uncompilable `???`. Give the nested shape its own definition and reference it with $ref.
    collectInlineTypeErrors(multiTemplate) match {
      case errors if errors.nonEmpty =>
        return Left(GeneratorError.TemplateError(errors.mkString(", ")))
      case _ =>
    }
    
    // Get definitions map for reference resolution
    val definitionsMap = multiTemplate.definitionsMap

    // Access modifier prepended to every generated top-level definition (validated safe above).
    val visibility = multiTemplate.visibility.map(_ + " ").getOrElse("")
    
    // Generate a file for each definition
    val fileResults = multiTemplate.definitions.map { definition =>
      generateFromDefinition(definition, multiTemplate.basePackage, definitionsMap, multiTemplate.definitions, visibility)
    }
    
    // Check for errors
    val errors = fileResults.collect { case Left(err) => err }
    if (errors.nonEmpty) {
      Left(errors.head) // Return first error
    } else {
      val generatedFiles = fileResults.collect { case Right(file) => file }
      
      // Generate a file per unique enum type, in its owning definition's package
      val enumFiles = multiTemplate.definitions.flatMap { definition =>
        val pkg = definition.fullPackage(multiTemplate.basePackage)
        collectEnums(definition.templateType).map { case (enumName, values, withUnknown) =>
          generateEnum(pkg, enumName, values, withUnknown, definition.sourcePath, visibility)
        }
      }.groupBy(_.relativePath).values.map(_.head).toList
      
      // NomosFormats holds the embedded template and a validator for runtime validation.
      val nomosFormatsFile = generateNomosFormats(multiTemplate.basePackage, multiTemplate, visibility)
      Right(nomosFormatsFile :: generatedFiles ::: enumFiles)
    }
  }

  /**
   * Renders a field's Scala type. A nullable field becomes its raw type (no Option); when
   * withNullDefault is set, nullable adds a "= null" default and a plain default is appended.
   */
  private def renderFieldType(
    fieldDef: FieldDef,
    definitionsMap: Map[String, TemplateDefinition],
    withNullDefault: Boolean
  ): String = {
    if (fieldDef.nullable) {
      val raw = boxIfPrimitive(scalaTypeForDefinition(fieldDef.fieldType, optional = false, definitionsMap))
      if (withNullDefault) s"$raw = null" else raw
    } else {
      val t = scalaTypeForDefinition(fieldDef.fieldType, fieldDef.optional, definitionsMap)
      if (withNullDefault) fieldDef.default.map(d => s"$t = $d").getOrElse(t) else t
    }
  }

  /**
   * Boxes Scala value types so a nullable field can default to null (value types cannot be null).
   */
  private def boxIfPrimitive(scalaType: String): String = scalaType match {
    case "Int"     => "java.lang.Integer"
    case "Long"    => "java.lang.Long"
    case "Double"  => "java.lang.Double"
    case "Boolean" => "java.lang.Boolean"
    case other     => other
  }

  // --- Dependency-free codec generation ---------------------------------------------------------
  // The following helpers emit Scala expressions that build/read the first-party JsonValue model,
  // so generated fromJson/toJson never depend on a third-party JSON library.

  /** A `Codecs.Decoder[T]` expression that decodes a JSON value into the field's Scala type. */
  private def decoderExpr(tt: TemplateType): String = tt match {
    case StringType(_) => "Codecs.string"
    case NumberType(_) => "Codecs.double"
    case IntType(_) => "Codecs.int"
    case LongType(_) => "Codecs.long"
    case DecimalType(_) => "Codecs.bigDecimal"
    case BooleanType() => "Codecs.boolean"
    case DateType() => s"""Codecs.temporal[${config.dateType}]("date", ${temporalParseLambda(config.dateType, isDate = true)})"""
    case DateTimeType() => s"""Codecs.temporal[${config.dateTimeType}]("datetime", ${temporalParseLambda(config.dateTimeType, isDate = false)})"""
    case ArrayType(elem, _) =>
      if (config.listType == "Array")
        s"((j: JsonValue) => Codecs.list(${decoderExpr(elem)})(j).right.map(_.toArray))"
      else s"Codecs.list(${decoderExpr(elem)})"
    case MapType(v) => s"Codecs.map(${decoderExpr(v)})"
    // Inline empty object with an additionalProperties policy renders as a Map field.
    case ObjectType(f, TypedExtra(vt)) if f.isEmpty => s"Codecs.map(${decoderExpr(vt)})"
    case ObjectType(f, AllowExtra) if f.isEmpty => "Codecs.map[Any]((j: JsonValue) => Right(j))"
    case UnionType(_) => "Codecs.any"
    case ReferenceType(n) => s"$n.decode"
    case RecursiveRef(n) => s"$n.decode"
    case ExternalType(qn, true) => s"$qn.decode"
    case ExternalType(qn, false) => s"""((j: JsonValue) => CodecRegistry.decode[$qn]("$qn", j))"""
    case EnumType(n, _) => s"$n.decode"
    case _ => "Codecs.any"
  }

  /** A boxed `Codecs.Decoder` for a nullable value-typed field (so it can hold null). */
  private def boxedDecoderExpr(tt: TemplateType): String = tt match {
    case IntType(_) => "Codecs.boxedInt"
    case LongType(_) => "Codecs.boxedLong"
    case NumberType(_) => "Codecs.boxedDouble"
    case BooleanType() => "Codecs.boxedBoolean"
    case other => decoderExpr(other)
  }

  /** A JsonValue expression that encodes `v` (an expression of the field's Scala type). */
  private def encodeValueExpr(tt: TemplateType, v: String): String = tt match {
    case StringType(_) => s"JsonString($v)"
    case NumberType(_) => s"JsonNumber.fromDouble($v)"
    case IntType(_) => s"JsonNumber.fromInt($v)"
    case LongType(_) => s"JsonNumber.fromLong($v)"
    case DecimalType(_) => s"JsonNumber.fromBigDecimal($v)"
    case BooleanType() => s"JsonBoolean($v)"
    case DateType() => temporalEncodeExpr(config.dateType, isDate = true, v)
    case DateTimeType() => temporalEncodeExpr(config.dateTimeType, isDate = false, v)
    case ArrayType(elem, _) => s"JsonArray($v.iterator.map(x => ${encodeValueExpr(elem, "x")}).toVector)"
    case MapType(vt) => s"JsonObject.fromFields($v.iterator.map { case (k, x) => (k, ${encodeValueExpr(vt, "x")}) }.toSeq)"
    // Inline empty object with an additionalProperties policy renders as a Map field.
    case ObjectType(f, TypedExtra(vt)) if f.isEmpty => s"JsonObject.fromFields($v.iterator.map { case (k, x) => (k, ${encodeValueExpr(vt, "x")}) }.toSeq)"
    case ObjectType(f, AllowExtra) if f.isEmpty => s"""JsonObject.fromFields($v.iterator.map { case (k, x) => (k, (x match { case jv: JsonValue => jv; case o => JsonString(String.valueOf(o)) })) }.toSeq)"""
    case UnionType(_) => s"($v match { case jv: JsonValue => jv; case o => JsonString(String.valueOf(o)) })"
    case ReferenceType(n) => s"$n.encode($v)"
    case RecursiveRef(n) => s"$n.encode($v)"
    case ExternalType(qn, true) => s"$qn.encode($v)"
    case ExternalType(qn, false) => s"""CodecRegistry.encode("$qn", $v)"""
    case EnumType(n, _) => s"$n.encode($v)"
    case _ => "JsonNull"
  }

  /** Like [[encodeValueExpr]] but unboxes a nullable value-typed field before encoding. */
  private def encodeNullableValueExpr(tt: TemplateType, v: String): String = tt match {
    case IntType(_) => s"JsonNumber.fromInt($v.intValue)"
    case LongType(_) => s"JsonNumber.fromLong($v.longValue)"
    case NumberType(_) => s"JsonNumber.fromDouble($v.doubleValue)"
    case BooleanType() => s"JsonBoolean($v.booleanValue)"
    case other => encodeValueExpr(other, v)
  }

  /**
   * A `String => T` parser lambda for a temporal field of the configured type. `java.time.*`
   * types have a static `parse` that returns the type, so they are emitted directly; other
   * types (e.g. `java.util.Date`, whose `parse` returns a `long`) are bridged through `java.time`.
   */
  private def temporalParseLambda(typeName: String, isDate: Boolean): String = typeName match {
    case "java.util.Date" if isDate =>
      "s => java.util.Date.from(java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneOffset.UTC).toInstant)"
    case "java.util.Date" =>
      "s => java.util.Date.from(java.time.Instant.parse(s))"
    case other => s"s => $other.parse(s)"
  }

  /** A JsonValue expression that encodes a temporal value of the configured type as a string. */
  private def temporalEncodeExpr(typeName: String, isDate: Boolean, v: String): String = typeName match {
    case "java.util.Date" if isDate =>
      s"JsonString($v.toInstant.atZone(java.time.ZoneOffset.UTC).toLocalDate.toString)"
    case "java.util.Date" =>
      s"JsonString($v.toInstant.toString)"
    case _ => s"JsonString($v.toString)"
  }

  /** The decode binding `name <- <expr>` for one field of an object/variant. */
  private def fieldDecodeBinding(fieldName: String, fieldDef: FieldDef): String = {
    val valName = ScalaCodeBuilder.escapeKeyword(fieldName)
    val keyLit = ScalaCodeBuilder.escapeStringLiteral(fieldName)
    // A field adapter maps the wire string through the named registry adapter; it only attaches to
    // required string fields (enforced by the parser), so it replaces the plain string decoder.
    val decoder = fieldDef.adapter match {
      case Some(name) => s"""Codecs.adapted("${ScalaCodeBuilder.escapeStringLiteral(name)}")"""
      case None => decoderExpr(fieldDef.fieldType)
    }
    // Order mirrors renderFieldType: a nullable field is a raw (boxed) type, not an Option,
    // even when also marked optional, so the nullable codec must take precedence.
    val rhs =
      if (fieldDef.nullable) {
        s"""Codecs.nullable(o, "$keyLit", ${boxedDecoderExpr(fieldDef.fieldType)})"""
      } else if (fieldDef.optional) {
        s"""Codecs.optional(o, "$keyLit", $decoder)"""
      } else if (fieldDef.default.isDefined) {
        s"""Codecs.optional(o, "$keyLit", $decoder).right.map(_.getOrElse(${fieldDef.default.get}))"""
      } else {
        s"""Codecs.required(o, "$keyLit", $decoder)"""
      }
    s"$valName <- $rhs"
  }

  /** The encode entry `Option[(String, JsonValue)]` for one field of an object/variant. */
  private def fieldEncodeEntry(fieldName: String, fieldDef: FieldDef, accessorPrefix: String): String = {
    val accessor = s"$accessorPrefix.${ScalaCodeBuilder.escapeKeyword(fieldName)}"
    val keyLit = ScalaCodeBuilder.escapeStringLiteral(fieldName)
    def encode(v: String): String = fieldDef.adapter match {
      case Some(name) => s"""Codecs.adaptedEncode("${ScalaCodeBuilder.escapeStringLiteral(name)}", $v)"""
      case None => encodeValueExpr(fieldDef.fieldType, v)
    }
    if (fieldDef.nullable) {
      s"""Option($accessor).map(v => "$keyLit" -> ${encodeNullableValueExpr(fieldDef.fieldType, "v")})"""
    } else if (fieldDef.optional) {
      s"""$accessor.map(v => "$keyLit" -> ${encode("v")})"""
    } else {
      s"""Some("$keyLit" -> ${encode(accessor)})"""
    }
  }

  /** Emits `decode`, `encode`, `fromJson`, `toJson` for a record with ordered fields. */
  private def emitRecordCodec(
    builder: ScalaCodeBuilder,
    typeName: String,
    constructor: String,
    fields: List[(String, FieldDef)]
  ): Unit = {
    // decode
    builder.line(s"def decode(json: JsonValue): Either[String, $typeName] = json match {")
    builder.indent()
    builder.line("case o: JsonObject =>")
    builder.indent()
    if (fields.isEmpty) {
      builder.line(s"Right($constructor())")
    } else {
      builder.line("for {")
      builder.indent()
      fields.foreach { case (fn, fd) => builder.line(fieldDecodeBinding(fn, fd)) }
      builder.dedent()
      val args = fields.map { case (fn, _) => ScalaCodeBuilder.escapeKeyword(fn) }.mkString(", ")
      builder.line(s"} yield $constructor($args)")
    }
    builder.dedent()
    builder.line(s"""case other => Left("$typeName: expected object, got " + other.typeName)""")
    builder.dedent()
    builder.line("}")
    builder.emptyLine()
    // encode
    builder.line(s"def encode(obj: $typeName): JsonValue = JsonObject.fromFields(List(")
    builder.indent()
    fields.zipWithIndex.foreach { case ((fn, fd), idx) =>
      val comma = if (idx < fields.length - 1) "," else ""
      builder.line(fieldEncodeEntry(fn, fd, "obj") + comma)
    }
    builder.dedent()
    builder.line(").flatten)")
    builder.emptyLine()
    emitFromToJson(builder, typeName)
  }

  /** Emits the public `fromJson`/`toJson` wrappers over `decode`/`encode`. */
  private def emitFromToJson(builder: ScalaCodeBuilder, typeName: String): Unit = {
    if (config.throwingFromJson) {
      builder.line(s"def fromJson(json: String): $typeName = Json.parse(json).right.flatMap(decode) match {")
      builder.indent()
      builder.line("case Right(v) => v")
      builder.line("case Left(e) => throw new IllegalArgumentException(e)")
      builder.dedent()
      builder.line("}")
    } else {
      builder.line(s"def fromJson(json: String): Either[String, $typeName] = Json.parse(json).right.flatMap(decode)")
    }
    builder.emptyLine()
    builder.line(s"def toJson(obj: $typeName): String = Json.write(encode(obj))")
  }

  /** Emits the `validate` method that runs the embedded-template validator then parses. */
  private def emitValidate(builder: ScalaCodeBuilder, typeName: String, fqn: String): Unit = {
    builder.line("/**")
    builder.line(" * Validates JSON against the embedded template and returns a parsed instance.")
    builder.line(" * This allows validation without needing the original template file.")
    builder.line(" */")
    builder.line(s"def validate(json: String): Either[List[ValidationError], $typeName] = {")
    builder.indent()
    builder.line(s"""validator.validate(json, "$fqn") match {""")
    builder.indent()
    if (config.throwingFromJson) {
      builder.line("""case Right(_) => try Right(fromJson(json)) catch { case e: Exception => Left(List(ValidationError("root", e.getMessage, "valid JSON", json))) }""")
    } else {
      builder.line("""case Right(_) => fromJson(json).left.map(err => List(ValidationError("root", err, "valid JSON", json)))""")
    }
    builder.line("case Left(errors) => Left(errors)")
    builder.dedent()
    builder.line("}")
    builder.dedent()
    builder.line("}")
  }

  /** Common imports for a generated file's companion: runtime JSON, codecs, validation, formats. */
  private def emitCodecImports(builder: ScalaCodeBuilder, basePackage: String, currentPackage: String): Unit = {
    if (currentPackage != basePackage) {
      builder.line(s"import $basePackage.NomosFormats")
    }
    builder.line("import NomosFormats._")
    builder.line("import dev.cjfravel.nomos.json._")
    builder.line("import dev.cjfravel.nomos.serialization.{Codecs, CodecRegistry}")
    builder.line("import dev.cjfravel.nomos.validation.ValidationError")
  }

  /**
   * Header prepended to every generated file. Includes a Source pointer when the template
   * file is known, otherwise just the generic generated-by notice.
   */
  private def headerLines(sourcePath: Option[String]): List[String] = {
    val base = List("// Code generated by Nomos. DO NOT EDIT.")
    sourcePath match {
      case Some(p) => base :+ s"// Source: $p"
      case None => base
    }
  }


  /**
   * Collects every template-derived name that would be emitted as a Scala identifier or
   * qualified name but is not a valid one. Running this before generation turns what would be
   * uncompilable (or injectable) output into a clean, actionable error.
   */
  private def collectNameErrors(multiTemplate: MultiTemplate): List[String] = {
    def ident(value: String, context: String): List[String] =
      if (ScalaCodeBuilder.isSimpleIdentifier(value)) Nil
      else List(s"$context is not a valid Scala identifier: '$value'")

    def qualified(value: String, context: String): List[String] =
      if (value.isEmpty || ScalaCodeBuilder.isQualifiedName(value)) Nil
      else List(s"$context is not a valid package or type name: '$value'")

    // External types are emitted verbatim as a Scala type, so generics/tuples/function types are
    // allowed; only reject characters that could break out of the field/type declaration.
    def externalType(value: String, context: String): List[String] = {
      val forbidden = Set('"', '\\', ';', '{', '}', '\n', '\r', '\t')
      if (value.nonEmpty && !value.exists(forbidden.contains)) Nil
      else List(s"$context is not a safe external type name: '$value'")
    }

    def walk(tt: TemplateType, ctx: String): List[String] = tt match {
      case ObjectType(fields, additional) =>
        val fieldErrs = fields.toList.flatMap { case (fieldName, fieldDef) =>
          ident(fieldName, s"$ctx field name") ++ walk(fieldDef.fieldType, s"$ctx.$fieldName")
        }
        val extraErrs = additional match {
          case TypedExtra(vt) => walk(vt, s"$ctx.<additionalProperties>")
          case _ => Nil
        }
        fieldErrs ++ extraErrs
      case ArrayType(elementType, _) => walk(elementType, s"$ctx[]")
      case MapType(valueType) => walk(valueType, s"$ctx{}")
      case UnionType(types) => types.flatMap(walk(_, ctx))
      case EnumType(enumName, values) =>
        ident(enumName, s"$ctx enum name") ++
          values.flatMap(v => ident(ScalaCodeBuilder.toPascalCase(v), s"$ctx enum value '$v' (case object name)"))
      case ExternalType(qn, _) => externalType(qn, s"$ctx external type")
      case TypeDiscriminator(fieldName, variants, commonFields, includeInOutput, variantNames, _, variantSubPackage, fallbackVariant, discriminatorEnum) =>
        // The discriminator field becomes a generated Scala field only when it is included in the
        // output or when variantNames forces a trait val; otherwise it is purely a JSON key.
        val fieldErrs =
          if (includeInOutput || variantNames.nonEmpty) ident(fieldName, s"$ctx discriminator field") else Nil
        // A discriminator enum generates a sealed-trait type named discriminatorEnum with one case
        // object per variant value, so validate both the enum name and the derived case names.
        val discEnumErrs = discriminatorEnum.toList.flatMap { en =>
          ident(en, s"$ctx discriminatorEnum name") ++
            variants.keys.toList.flatMap(v => ident(ScalaCodeBuilder.toPascalCase(v), s"$ctx discriminatorEnum value '$v' (case object name)"))
        }
        fieldErrs ++ discEnumErrs ++
          variantSubPackage.toList.flatMap(qualified(_, s"$ctx variantSubPackage")) ++
          fallbackVariant.toList.flatMap(ident(_, s"$ctx fallbackVariant (class name)")) ++
          commonFields.toList.flatMap { case (n, fd) => ident(n, s"$ctx common field") ++ walk(fd.fieldType, s"$ctx.$n") } ++
          variants.toList.flatMap { case (key, obj) =>
            val className = variantNames.getOrElse(key, ScalaCodeBuilder.toPascalCase(key))
            ident(className, s"$ctx variant '$key' (class name)") ++ walk(obj, s"$ctx.$key")
          }
      case _ => Nil
    }

    multiTemplate.definitions.flatMap { d =>
      val ctx = s"Definition '${d.name}'"
      d.subPackage.toList.flatMap(qualified(_, s"$ctx subPackage")) ++ walk(d.templateType, ctx)
    }
  }


  /**
   * Generates code for a single definition within a multi-template
   */
  private def generateFromDefinition(
    definition: TemplateDefinition,
    basePackage: String,
    definitionsMap: Map[String, TemplateDefinition],
    allDefinitions: List[TemplateDefinition],
    visibility: String
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
    // For a discriminator that relocates its variants into a sub-package, a referenced type that
    // lives in that same sub-package is visible to the variants without an import; a file-level
    // import of it would be ambiguous inside the nested package block, so drop it here.
    val variantSubExcluded: Set[String] = definition.templateType match {
      case d: TypeDiscriminator if d.variantSubPackage.isDefined =>
        val subPkg = if (packageName.nonEmpty) s"$packageName.${d.variantSubPackage.get}" else d.variantSubPackage.get
        referencedTypes.filter(rt => allDefinitions.find(_.name == rt).exists(_.fullPackage(basePackage) == subPkg))
      case _ => Set.empty
    }
    val imports = generateImports(packageName, basePackage, referencedTypes -- variantSubExcluded, allDefinitions)
    
    val builder = ScalaCodeBuilder(visibility)
    
    // Generated-file header with optional source pointer
    headerLines(definition.sourcePath).foreach(builder.line)
    
    // Package declaration
    builder.line(s"package $packageName")
    builder.emptyLine()
    
    // File-level imports are cross-package references; each companion emits its own codec imports.
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
        // Reject ambiguous variants up front so the failure surfaces through the Either API
        // rather than as an uncaught exception thrown from deep in generation.
        if (!disc.includeInOutput) {
          detectAmbiguity(disc) match {
            case Some(error) => return Left(GeneratorError.AmbiguityError(error))
            case None =>
          }
        }
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
      (ScalaCodeBuilder.escapeKeyword(fieldName), renderFieldType(fieldDef, definitionsMap, withNullDefault = true))
    }.toList
    
    builder.caseClass(name, fieldList, None)
    
    // Companion object with dependency-free codecs and validation
    builder.emptyLine()
    builder.companionObject(name) {
      emitCodecImports(builder, basePackage, currentPackage)
      builder.emptyLine()
      emitRecordCodec(builder, name, name, objectType.fields.toList)
      builder.emptyLine()
      emitValidate(builder, name, s"$currentPackage.$name")
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
    // Ambiguity (when the discriminator field is omitted from output) is checked by the caller
    // so the failure can be returned through the Either API.
    
    // If variantNames is provided, use custom naming and grouping logic
    if (discriminator.variantNames.nonEmpty) {
      generateDiscriminatorWithVariantNames(name, discriminator, builder, definitionsMap, basePackage, currentPackage)
    } else {
      generateDiscriminatorOriginal(name, discriminator, builder, definitionsMap, basePackage, currentPackage)
    }
  }

  /** The discriminator field's rendered Scala type (the enum name, or `String`). */
  private def discScalaType(d: TypeDiscriminator): String = d.discriminatorEnum.getOrElse("String")

  /** Decode expression for the discriminator field value of a matched variant key. */
  private def discDecodeArg(d: TypeDiscriminator, variantKey: String): String =
    d.discriminatorEnum match {
      case Some(en) => s"$en.${ScalaCodeBuilder.toPascalCase(variantKey)}"
      case None => "d"
    }

  /** Encode expression mapping the discriminator field value to its JSON string. */
  private def discEncodeExpr(d: TypeDiscriminator, accessor: String): String =
    d.discriminatorEnum match {
      case Some(en) => s"$en.encode($accessor)"
      case None => s"JsonString($accessor)"
    }

  /**
   * Decode expression for the discriminator field value of the *fallback* (unmatched) variant.
   * With a discriminatorEnum the unknown string is wrapped in the enum's open-ended `Unknown`
   * member; otherwise it is the raw string.
   */
  private def discFallbackArg(d: TypeDiscriminator, valueExpr: String): String =
    d.discriminatorEnum match {
      case Some(en) => s"$en.Unknown($valueExpr)"
      case None => valueExpr
    }

  /** The fixed discriminator value for a known variant key: an enum constant or a string literal. */
  private def discFixedValue(d: TypeDiscriminator, variantKey: String): String =
    d.discriminatorEnum match {
      case Some(en) => s"$en.${ScalaCodeBuilder.toPascalCase(variantKey)}"
      case None => "\"" + ScalaCodeBuilder.escapeStringLiteral(variantKey) + "\""
    }

  /**
   * The fixed-override member for a known variant's discriminator: the value is determined by the
   * variant, so it is a body `override val` rather than a primary-constructor parameter (kept out
   * of the constructor and `unapply`, and preventing inconsistent construction).
   */
  private def discOverrideVal(d: TypeDiscriminator, variantKey: String): String =
    s"override val ${ScalaCodeBuilder.escapeKeyword(d.fieldName)}: ${discScalaType(d)} = ${discFixedValue(d, variantKey)}"

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
    // The discriminator is a fixed per-variant override only for exact matching. With "prefix"
    // matching the on-the-wire value is parameterized (e.g. "Decimal(28,8)" matches key "Decimal"),
    // so it must stay a constructor field to preserve the actual value on round-trip.
    val discFixed = discriminator.includeInOutput && discriminator.variantMatch != "prefix"
    val discAsCtor = discriminator.includeInOutput && !discFixed  // prefix: keep as ctor field

    // Non-discriminator ctor fields of a variant case class: common, then variant.
    def variantCtorFields(variantType: ObjectType): List[(String, FieldDef)] =
      discriminator.commonFields.toList ++ variantType.fields.toList

    // Generate sealed trait. When the discriminator is emitted it is an abstract member here, so
    // each variant's fixed override (or constructor value) satisfies one contract.
    if (discriminator.includeInOutput)
      builder.sealedTraitWithFields(name, List((ScalaCodeBuilder.escapeKeyword(discriminator.fieldName), discScalaType(discriminator))))
    else
      builder.sealedTrait(name)
    builder.emptyLine()

    val variantPkg = discriminator.variantSubPackage.map(s => if (currentPackage.nonEmpty) s"$currentPackage.$s" else s)
    // The block uses the *relative* sub-package name so it nests correctly under the file's
    // package clause (an FQN here would double the prefix); imports are _root_-anchored.
    discriminator.variantSubPackage.foreach { sub =>
      builder.line(s"package $sub {")
      builder.indent()
    }

    // Generate case classes for each variant. For exact matching the discriminator is a fixed
    // `override val` in the body (out of the constructor / unapply); for prefix matching it is a
    // leading constructor field (the value is parameterized, so it must be preserved).
    discriminator.variants.foreach { case (variantName, variantType) =>
      val caseClassName = ScalaCodeBuilder.toPascalCase(variantName)
      val discCtor = if (discAsCtor) List(discriminator.fieldName -> FieldDef(StringType(), optional = false)) else Nil
      val fieldList = (discCtor ++ variantCtorFields(variantType)).map { case (fieldName, fieldDef) =>
        (ScalaCodeBuilder.escapeKeyword(fieldName), renderFieldType(fieldDef, definitionsMap, withNullDefault = true))
      }
      val body = if (discFixed) List(discOverrideVal(discriminator, variantName)) else Nil
      builder.caseClass(caseClassName, fieldList, Some(name), body)
      builder.emptyLine()
    }

    // Catch-all variant: preserves the unrecognized discriminator value and the raw object so an
    // unknown variant can be read and re-emitted verbatim (forward compatibility). With a
    // discriminatorEnum the value is held as the enum's open-ended Unknown member.
    discriminator.fallbackVariant.foreach { fb =>
      builder.caseClass(fb, List(
        (ScalaCodeBuilder.escapeKeyword(discriminator.fieldName), discScalaType(discriminator)),
        ("raw", "dev.cjfravel.nomos.json.JsonObject")
      ), Some(name))
      builder.emptyLine()
    }

    variantPkg.foreach { _ =>
      builder.dedent()
      builder.line("}")
      builder.emptyLine()
    }

    val variantMap = discriminator.variants.map { case (variantName, vt) =>
      (variantName, ScalaCodeBuilder.toPascalCase(variantName), vt)
    }.toList
    val fieldKeyLit = ScalaCodeBuilder.escapeStringLiteral(discriminator.fieldName)

    builder.companionObject(name) {
      emitCodecImports(builder, basePackage, currentPackage)
      variantPkg.foreach(p => builder.line(s"import _root_.$p._"))
      builder.emptyLine()

      // decode: dispatch on the discriminator value, then decode the matched variant's fields
      builder.line(s"def decode(json: JsonValue): Either[String, $name] = json match {")
      builder.indent()
      builder.line("case o: JsonObject =>")
      builder.indent()
      builder.line(s"""o.field("$fieldKeyLit") match {""")
      builder.indent()
      builder.line("case Some(JsonString(d)) =>")
      builder.indent()
      builder.line("d match {")
      builder.indent()
      variantMap.foreach { case (variantKey, className, vt) =>
        val keyLit = ScalaCodeBuilder.escapeStringLiteral(variantKey)
        val matchPat = if (discriminator.variantMatch == "prefix") s"""case d2 if d2.startsWith("$keyLit") =>""" else s"""case "$keyLit" =>"""
        builder.line(matchPat)
        builder.indent()
        // Prefix variants keep the (parameterized) matched value as a constructor arg; exact
        // variants fix it via an override, so no arg is passed.
        val discCtorArg = if (discAsCtor) Some("d") else None
        emitVariantDecode(builder, className, discriminator, vt, discCtorArg)
        builder.dedent()
      }
      discriminator.fallbackVariant match {
        case Some(fb) =>
          builder.line(s"""case other => Right($fb(${discFallbackArg(discriminator, "other")}, o))""")
        case None =>
          builder.line(s"""case other => Left("Unknown $fieldKeyLit value: " + other)""")
      }
      builder.dedent()
      builder.line("}")
      builder.dedent()
      builder.line(s"""case Some(_) => Left("$fieldKeyLit: expected string")""")
      builder.line(s"""case None => Left("missing required field '$fieldKeyLit'")""")
      builder.dedent()
      builder.line("}")
      builder.dedent()
      builder.line(s"""case other => Left("$name: expected object, got " + other.typeName)""")
      builder.dedent()
      builder.line("}")
      builder.emptyLine()

      // encode: pattern-match the trait value to its variant and build the JSON object. The
      // discriminator is a fixed member, read back off the value (`v.<field>`).
      builder.line(s"def encode(obj: $name): JsonValue = obj match {")
      builder.indent()
      variantMap.foreach { case (_, className, vt) =>
        val discEntry = if (discriminator.includeInOutput)
          List(s"""Some("$fieldKeyLit" -> ${discEncodeExpr(discriminator, "v." + ScalaCodeBuilder.escapeKeyword(discriminator.fieldName))})""")
          else Nil
        val entries = discEntry ++ variantCtorFields(vt).map { case (fn, fd) => fieldEncodeEntry(fn, fd, "v") }
        builder.line(s"case v: $className => JsonObject.fromFields(List(${entries.mkString(", ")}).flatten)")
      }
      discriminator.fallbackVariant.foreach { fb =>
        builder.line(s"case v: $fb => v.raw")
      }
      builder.dedent()
      builder.line("}")
      builder.emptyLine()

      emitFromToJson(builder, name)
      builder.emptyLine()
      emitValidate(builder, name, s"$currentPackage.$name")
    }
    builder.emptyLine()
  }

  /**
   * Emits the body of a variant's decode branch: binds the non-discriminator fields and constructs
   * the variant case class. `discCtorArg` is the discriminator constructor argument for prefix
   * variants (the matched value); for exact variants the discriminator is a fixed override, so it
   * is not a constructor argument.
   */
  private def emitVariantDecode(
    builder: ScalaCodeBuilder,
    className: String,
    discriminator: TypeDiscriminator,
    variantType: ObjectType,
    discCtorArg: Option[String]
  ): Unit = {
    val nonDisc = discriminator.commonFields.toList ++ variantType.fields.toList
    val discArgs = discCtorArg.toList
    if (nonDisc.isEmpty) {
      builder.line(s"Right($className(${discArgs.mkString(", ")}))")
    } else {
      builder.line("for {")
      builder.indent()
      nonDisc.foreach { case (fn, fd) => builder.line(fieldDecodeBinding(fn, fd)) }
      builder.dedent()
      val args = (discArgs ++ nonDisc.map { case (fn, _) => ScalaCodeBuilder.escapeKeyword(fn) }).mkString(", ")
      builder.line(s"} yield $className($args)")
    }
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
    // Generate sealed trait with common fields as abstract vals
    if (discriminator.commonFields.nonEmpty) {
      val commonFieldList = discriminator.commonFields.map { case (fieldName, fieldDef) =>
        (ScalaCodeBuilder.escapeKeyword(fieldName), renderFieldType(fieldDef, definitionsMap, withNullDefault = false))
      }.toList
      
      // Always include discriminator field on trait
      val allTraitFields = (ScalaCodeBuilder.escapeKeyword(discriminator.fieldName), discScalaType(discriminator)) :: commonFieldList
      builder.sealedTraitWithFields(name, allTraitFields)
    } else {
      // Just discriminator field on trait
      builder.sealedTraitWithFields(name, List((ScalaCodeBuilder.escapeKeyword(discriminator.fieldName), discScalaType(discriminator))))
    }
    builder.emptyLine()
    
    // Group variants by their mapped class names, preserving first-appearance order and merging
    // each class's variant fields in order (so generated case classes, decode, and encode agree).
    var classFields = scala.collection.immutable.ListMap.empty[String, scala.collection.immutable.ListMap[String, FieldDef]]
    var classKeys = scala.collection.immutable.ListMap.empty[String, List[String]]
    discriminator.variants.foreach { case (variantKey, vt) =>
      val cn = discriminator.variantNames.getOrElse(variantKey, ScalaCodeBuilder.toPascalCase(variantKey))
      val merged = classFields.getOrElse(cn, scala.collection.immutable.ListMap.empty[String, FieldDef]) ++ vt.fields
      classFields = classFields.updated(cn, merged)
      classKeys = classKeys.updated(cn, classKeys.getOrElse(cn, Nil) :+ variantKey)
    }
    // A class discriminated by exactly one value gets a fixed override; a class grouping several
    // discriminator values must keep the value as a constructor field to round-trip which it was.
    // Prefix matching also keeps a constructor field (the on-the-wire value is parameterized).
    def classIsFixed(className: String): Boolean =
      discriminator.variantMatch != "prefix" && classKeys.getOrElse(className, Nil).size == 1

    // Variant case classes (and the fallback) may be relocated into a sub-package while the trait
    // stays put; mirror the inline-variants path so variantSubPackage works here too.
    val variantPkg = discriminator.variantSubPackage.map(s => if (currentPackage.nonEmpty) s"$currentPackage.$s" else s)
    discriminator.variantSubPackage.foreach { sub =>
      builder.line(s"package $sub {")
      builder.indent()
    }

    // Generate one case class per unique mapped name. A single-value class carries the
    // discriminator as a fixed `override val` (out of the constructor / unapply); a grouped class
    // keeps it as an override constructor field.
    classFields.foreach { case (caseClassName, variantFields) =>
      val commonFieldsList = discriminator.commonFields.map { case (fieldName, fieldDef) =>
        (ScalaCodeBuilder.escapeKeyword(fieldName), renderFieldType(fieldDef, definitionsMap, withNullDefault = false), true)  // override = true
      }.toList
      val variantFieldsList = variantFields.map { case (fieldName, fieldDef) =>
        (ScalaCodeBuilder.escapeKeyword(fieldName), renderFieldType(fieldDef, definitionsMap, withNullDefault = false), false)  // override = false
      }.toList

      if (classIsFixed(caseClassName)) {
        val body = List(discOverrideVal(discriminator, classKeys(caseClassName).head))
        builder.caseClassWithOverride(caseClassName, commonFieldsList ++ variantFieldsList, Some(name), body)
      } else {
        val discriminatorField = (ScalaCodeBuilder.escapeKeyword(discriminator.fieldName), discScalaType(discriminator), true)
        builder.caseClassWithOverride(caseClassName, discriminatorField :: (commonFieldsList ++ variantFieldsList), Some(name))
      }
      builder.emptyLine()
    }

    // Catch-all variant: overrides the trait's discriminator (and any common fields, which every
    // variant shares) and preserves the raw object so an unknown variant round-trips verbatim.
    discriminator.fallbackVariant.foreach { fb =>
      val discriminatorField = (ScalaCodeBuilder.escapeKeyword(discriminator.fieldName), discScalaType(discriminator), true)
      val commonFieldsList = discriminator.commonFields.map { case (fieldName, fieldDef) =>
        (ScalaCodeBuilder.escapeKeyword(fieldName), renderFieldType(fieldDef, definitionsMap, withNullDefault = false), true)
      }.toList
      val rawField = ("raw", "dev.cjfravel.nomos.json.JsonObject", false)
      builder.caseClassWithOverride(fb, discriminatorField :: (commonFieldsList :+ rawField), Some(name))
      builder.emptyLine()
    }

    variantPkg.foreach { _ =>
      builder.dedent()
      builder.line("}")
      builder.emptyLine()
    }
    // Map each discriminator key to its mapped class name (order preserved)
    val variantMap = discriminator.variants.toList.map { case (variantKey, _) =>
      (variantKey, discriminator.variantNames.getOrElse(variantKey, ScalaCodeBuilder.toPascalCase(variantKey)))
    }
    val fieldKeyLit = ScalaCodeBuilder.escapeStringLiteral(discriminator.fieldName)

    builder.companionObject(name) {
      emitCodecImports(builder, basePackage, currentPackage)
      variantPkg.foreach(p => builder.line(s"import _root_.$p._"))
      builder.emptyLine()

      // decode: dispatch on the discriminator value, build the mapped class (common + variant fields)
      builder.line(s"def decode(json: JsonValue): Either[String, $name] = json match {")
      builder.indent()
      builder.line("case o: JsonObject =>")
      builder.indent()
      builder.line(s"""o.field("$fieldKeyLit") match {""")
      builder.indent()
      builder.line("case Some(JsonString(d)) =>")
      builder.indent()
      builder.line("d match {")
      builder.indent()
      variantMap.foreach { case (variantKey, className) =>
        val keyLit = ScalaCodeBuilder.escapeStringLiteral(variantKey)
        val matchPat = if (discriminator.variantMatch == "prefix") s"""case d2 if d2.startsWith("$keyLit") =>""" else s"""case "$keyLit" =>"""
        builder.line(matchPat)
        builder.indent()
        val nonDisc = discriminator.commonFields.toList ++ classFields.getOrElse(className, scala.collection.immutable.ListMap.empty[String, FieldDef]).toList
        // A grouped class carries the discriminator as a constructor field, so pass the matched
        // value; a single-value class fixes it via an override, so it is not a constructor arg.
        val discArgs = if (classIsFixed(className)) Nil else List(discDecodeArg(discriminator, variantKey))
        if (nonDisc.isEmpty) {
          builder.line(s"Right($className(${discArgs.mkString(", ")}))")
        } else {
          builder.line("for {")
          builder.indent()
          nonDisc.foreach { case (fn, fd) => builder.line(fieldDecodeBinding(fn, fd)) }
          builder.dedent()
          val args = (discArgs ++ nonDisc.map { case (fn, _) => ScalaCodeBuilder.escapeKeyword(fn) }).mkString(", ")
          builder.line(s"} yield $className($args)")
        }
        builder.dedent()
      }
      discriminator.fallbackVariant match {
        case Some(fb) =>
          val common = discriminator.commonFields.toList
          val discArg = discFallbackArg(discriminator, "d")
          if (common.isEmpty) {
            builder.line(s"case _ => Right($fb($discArg, o))")
          } else {
            builder.line("case _ =>")
            builder.indent()
            builder.line("for {")
            builder.indent()
            common.foreach { case (fn, fd) => builder.line(fieldDecodeBinding(fn, fd)) }
            builder.dedent()
            val args = (discArg :: common.map { case (fn, _) => ScalaCodeBuilder.escapeKeyword(fn) } ::: List("o")).mkString(", ")
            builder.line(s"} yield $fb($args)")
            builder.dedent()
          }
        case None =>
          builder.line(s"""case other => Left("Unknown $fieldKeyLit value: " + other)""")
      }
      builder.dedent()
      builder.line("}")
      builder.dedent()
      builder.line(s"""case Some(_) => Left("$fieldKeyLit: expected string")""")
      builder.line(s"""case None => Left("missing required field '$fieldKeyLit'")""")
      builder.dedent()
      builder.line("}")
      builder.dedent()
      builder.line(s"""case other => Left("$name: expected object, got " + other.typeName)""")
      builder.dedent()
      builder.line("}")
      builder.emptyLine()

      // encode: pattern-match the trait value to its mapped class and build the JSON object
      builder.line(s"def encode(obj: $name): JsonValue = obj match {")
      builder.indent()
      classFields.foreach { case (className, variantFields) =>
        val discEntry = s"""Some("$fieldKeyLit" -> ${discEncodeExpr(discriminator, "v." + ScalaCodeBuilder.escapeKeyword(discriminator.fieldName))})"""
        val commonEntries = discriminator.commonFields.toList.map { case (fn, fd) => fieldEncodeEntry(fn, fd, "v") }
        val variantEntries = variantFields.toList.map { case (fn, fd) => fieldEncodeEntry(fn, fd, "v") }
        val entries = (discEntry :: commonEntries) ++ variantEntries
        builder.line(s"case v: $className => JsonObject.fromFields(List(${entries.mkString(", ")}).flatten)")
      }
      discriminator.fallbackVariant.foreach { fb =>
        builder.line(s"case v: $fb => v.raw")
      }
      builder.dedent()
      builder.line("}")
      builder.emptyLine()

      emitFromToJson(builder, name)
      builder.emptyLine()
      emitValidate(builder, name, s"$currentPackage.$name")
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
      case DateType() => config.dateType
      case DateTimeType() => config.dateTimeType
      case ArrayType(elementType, _) =>
        s"${config.listType}[${scalaTypeForDefinition(elementType, optional = false, definitionsMap)}]"
      case MapType(valueType) =>
        s"${config.mapType}[String, ${scalaTypeForDefinition(valueType, optional = false, definitionsMap)}]"
      case UnionType(_) => "Any"
      case ReferenceType(typeName) => typeName
      case RecursiveRef(typeName) => typeName
      case ExternalType(qn, _) => qn
      case EnumType(enumName, _) => enumName
      case ObjectType(fields, AllowExtra) if fields.isEmpty => "Map[String, Any]"
      case ObjectType(fields, TypedExtra(vt)) if fields.isEmpty =>
        s"Map[String, ${scalaTypeForDefinition(vt, optional = false, definitionsMap)}]"
      case ObjectType(_, _) =>
        "???" // Inline objects not supported in multi-definition mode
      case TypeDiscriminator(_, _, _, _, _, _, _, _, _) =>
        "???" // Inline discriminators not supported in multi-definition mode
    }
    
    if (optional && config.useOptionTypes) {
      s"Option[$baseType]"
    } else {
      baseType
    }
  }

  /**
   * Collects all enum (name, values, openEnded) declarations from a template type. `openEnded`
   * marks a discriminator enum whose union also has a `fallbackVariant`, so the enum needs an
   * `Unknown(value)` member to carry an unrecognized discriminator value.
   */
  private def collectEnums(templateType: TemplateType): List[(String, List[String], Boolean)] = {
    templateType match {
      case EnumType(name, values) => List((name, values, false))
      case ArrayType(elementType, _) => collectEnums(elementType)
      case MapType(valueType) => collectEnums(valueType)
      case UnionType(types) => types.flatMap(collectEnums)
      case ObjectType(fields, _) => fields.values.flatMap(f => collectEnums(f.fieldType)).toList
      case TypeDiscriminator(_, variants, commonFields, _, _, _, _, fallbackVariant, discriminatorEnum) =>
        val v = variants.values.flatMap(o => o.fields.values.flatMap(f => collectEnums(f.fieldType)))
        val c = commonFields.values.flatMap(f => collectEnums(f.fieldType))
        // A discriminator enum is one case object per variant value (the discriminator strings);
        // it is open-ended when the union also has a fallback variant.
        val d = discriminatorEnum.toList.map(en => (en, variants.keys.toList, fallbackVariant.isDefined))
        (v ++ c).toList ++ d
      case _ => Nil
    }
  }

  /**
   * Errors for inline (anonymous) objects or discriminators used as a field's type. Multi-definition
   * codegen cannot name them, so they would otherwise emit uncompilable `???`. An empty object with
   * an `$additionalProperties` policy (an open map) is allowed.
   */
  private def collectInlineTypeErrors(multiTemplate: MultiTemplate): List[String] = {
    def fieldTypeError(tt: TemplateType, ctx: String): List[String] = tt match {
      case ObjectType(fields, AllowExtra) if fields.isEmpty => Nil
      case ObjectType(fields, TypedExtra(_)) if fields.isEmpty => Nil
      case _: ObjectType =>
        List(s"$ctx: inline nested objects are not supported as a field type; define a separate type and reference it with $$ref")
      case _: TypeDiscriminator =>
        List(s"$ctx: inline discriminators are not supported as a field type; define a separate type and reference it with $$ref")
      case ArrayType(elem, _) => fieldTypeError(elem, ctx)
      case MapType(v) => fieldTypeError(v, ctx)
      case _ => Nil
    }
    def walkFields(tt: TemplateType, ctx: String): List[String] = tt match {
      case ObjectType(fields, _) =>
        fields.toList.flatMap { case (n, fd) => fieldTypeError(fd.fieldType, s"$ctx.$n") }
      case TypeDiscriminator(_, variants, commonFields, _, _, _, _, _, _) =>
        commonFields.toList.flatMap { case (n, fd) => fieldTypeError(fd.fieldType, s"$ctx.$n") } ++
          variants.toList.flatMap { case (k, obj) =>
            obj.fields.toList.flatMap { case (n, fd) => fieldTypeError(fd.fieldType, s"$ctx[$k].$n") }
          }
      case _ => Nil
    }
    multiTemplate.definitions.flatMap(d => walkFields(d.templateType, s"Definition '${d.name}'"))
  }

  /**
   * Generates a sealed-trait enum type whose case objects (de)serialize via the on-the-wire
   * string values, using only the first-party JSON model. When `withUnknown` is set the enum is
   * open-ended: it also gets a `case class Unknown(value: String)` member so an unrecognized
   * string can be represented (used by a union's `fallbackVariant`), and `fromString` is total.
   */
  private def generateEnum(packageName: String, enumName: String, values: List[String], withUnknown: Boolean = false, sourcePath: Option[String] = None, visibility: String = ""): GeneratedFile = {
    val builder = ScalaCodeBuilder(visibility)
    val cases = values.map(v => (v, ScalaCodeBuilder.toPascalCase(v)))
    
    headerLines(sourcePath).foreach(builder.line)
    builder.line(s"package $packageName")
    builder.emptyLine()
    builder.line("import dev.cjfravel.nomos.json.{JsonValue, JsonString}")
    builder.emptyLine()
    builder.line(s"${visibility}sealed trait $enumName")
    builder.emptyLine()
    builder.line(s"${visibility}object $enumName {")
    builder.indent()
    cases.foreach { case (_, obj) => builder.line(s"case object $obj extends $enumName") }
    if (withUnknown) builder.line(s"final case class Unknown(value: String) extends $enumName")
    builder.emptyLine()
    builder.line(s"val values: List[$enumName] = List(${cases.map(_._2).mkString(", ")})")
    builder.emptyLine()
    builder.line(s"def fromString(s: String): Option[$enumName] = s match {")
    builder.indent()
    cases.foreach { case (raw, obj) => builder.line("case \"" + ScalaCodeBuilder.escapeStringLiteral(raw) + "\" => Some(" + obj + ")") }
    builder.line(if (withUnknown) "case other => Some(Unknown(other))" else "case _ => None")
    builder.dedent()
    builder.line("}")
    builder.emptyLine()
    builder.line(s"def asString(v: $enumName): String = v match {")
    builder.indent()
    cases.foreach { case (raw, obj) => builder.line("case " + obj + " => \"" + ScalaCodeBuilder.escapeStringLiteral(raw) + "\"") }
    if (withUnknown) builder.line("case Unknown(value) => value")
    builder.dedent()
    builder.line("}")
    builder.emptyLine()
    builder.line(s"def decode(json: JsonValue): Either[String, $enumName] = json match {")
    builder.indent()
    builder.line(s"""case JsonString(s) => fromString(s).toRight("invalid $enumName: " + s)""")
    builder.line(s"""case other => Left("expected string, got " + other.typeName)""")
    builder.dedent()
    builder.line("}")
    builder.emptyLine()
    builder.line(s"def encode(v: $enumName): JsonValue = JsonString(asString(v))")
    builder.dedent()
    builder.line("}")
    
    GeneratedFile(packageName, enumName, builder.build())
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
      case TypeDiscriminator(_, variants, commonFields, _, _, _, _, _, _) =>
        val variantRefs = variants.values.flatMap(v => v.fields.values.flatMap(f => collectReferences(f.fieldType)))
        val commonRefs = commonFields.values.flatMap(f => collectReferences(f.fieldType))
        variantRefs.toSet ++ commonRefs
      case _ => Set.empty
    }
  }

  /**
   * Generates import statements for referenced types in different packages. A simple-name
   * reference resolves to a definition in the referrer's own package when one exists (no import
   * needed); otherwise to the unique definition with that name elsewhere. Identical simple names
   * in distinct packages are therefore supported as long as each is referenced from its own
   * package (ambiguous references are rejected earlier by `MultiTemplate.validate`).
   */
  private def generateImports(
    currentPackage: String,
    basePackage: String,
    referencedTypes: Set[String],
    allDefinitions: List[TemplateDefinition]
  ): List[String] = {
    val byName = allDefinitions.groupBy(_.name)
    referencedTypes.flatMap { refTypeName =>
      val candidates = byName.getOrElse(refTypeName, Nil)
      val resolved =
        candidates.find(_.fullPackage(basePackage) == currentPackage)
          .orElse(if (candidates.lengthCompare(1) == 0) candidates.headOption else None)
      resolved.flatMap { refDef =>
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
   * Generates the NomosFormats object: the embedded MultiTemplate and a MultiValidator built from
   * it for runtime validation.
   */
  private def generateNomosFormats(basePackage: String, multiTemplate: MultiTemplate, visibility: String): GeneratedFile = {
    val builder = ScalaCodeBuilder(visibility)
    
    headerLines(None).foreach(builder.line)
    builder.line(s"package $basePackage")
    builder.emptyLine()
    builder.line("import dev.cjfravel.nomos.model._")
    builder.line("import dev.cjfravel.nomos.validation.MultiValidator")
    builder.line("import scala.collection.immutable.ListMap")
    builder.emptyLine()
    builder.line("/**")
    builder.line(" * Holds the embedded template and a validator for runtime validation.")
    builder.line(" * Generated companions import this to validate without the original template file.")
    builder.line(" */")
    builder.line(s"${visibility}object NomosFormats {")
    builder.indent()
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