package com.example.models

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

/**
 * Provides Jackson ObjectMapper with Scala module support.
 * The Scala module enables automatic serialization/deserialization of case classes.
 * Import ChiselFormats._ to use the mapper in your code.
 */
object ChiselFormats {
  // Create a shared ObjectMapper instance with Scala module
  val mapper: ObjectMapper = {
    val m = new ObjectMapper()
    m.registerModule(DefaultScalaModule)
    m
  }
}
