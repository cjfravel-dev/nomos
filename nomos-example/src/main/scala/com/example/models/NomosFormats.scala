package com.example.models

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import dev.cjfravel.nomos.model._
import dev.cjfravel.nomos.validation.{MultiValidator, ValidationError}
import com.fasterxml.jackson.databind.JsonNode
import scala.collection.immutable.ListMap

/**
 * Provides Jackson ObjectMapper with Scala module support.
 * The Scala module enables automatic serialization/deserialization of case classes.
 * Also contains the embedded template for runtime validation.
 * Import NomosFormats._ to use the mapper in your code.
 */
object NomosFormats {
  // Create a shared ObjectMapper instance with Scala module
  val mapper: ObjectMapper = {
    val m = new ObjectMapper()
    m.registerModule(DefaultScalaModule)
    m
  }

  // Embedded template for runtime validation
  lazy val embeddedTemplate: MultiTemplate = {
    MultiTemplate(
          basePackage = "com.example.models",
          definitions = List(
          TemplateDefinition(
            name = "User",
            templateType = ObjectType(ListMap("id" -> FieldDef(StringType(List()), optional = false), "username" -> FieldDef(StringType(List()), optional = false), "email" -> FieldDef(StringType(List()), optional = false), "age" -> FieldDef(NumberType(List()), optional = true), "roles" -> FieldDef(ArrayType(StringType(List()), List()), optional = false)), ForbidExtra),
            subPackage = Some("user"),
            description = Some("User model with basic information"),
            validators = List()
          )
          ),
          useOptionTypes = true,
          listType = "List"
        )
  }

  // Validator instance using the embedded template
  lazy val validator: MultiValidator = new MultiValidator(embeddedTemplate)
}
