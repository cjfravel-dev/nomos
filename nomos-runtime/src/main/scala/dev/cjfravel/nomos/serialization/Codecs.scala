package dev.cjfravel.nomos.serialization

import dev.cjfravel.nomos.json._

/**
 * Decoder combinators used by generated `decode` methods. Keeping these in the runtime keeps
 * generated code compact while remaining free of any third-party JSON library.
 *
 * A `Decoder[A]` turns a [[JsonValue]] into either an error message or a value of type `A`.
 */
object Codecs {
  type Decoder[A] = JsonValue => Either[String, A]

  val string: Decoder[String] = {
    case JsonString(s) => Right(s)
    case other => Left(s"expected string, got ${other.typeName}")
  }

  val double: Decoder[Double] = {
    case n: JsonNumber =>
      val d = n.asDouble
      if (d.isNaN || d.isInfinite) Left(s"number out of Double range: ${n.raw}") else Right(d)
    case other => Left(s"expected number, got ${other.typeName}")
  }

  val int: Decoder[Int] = {
    case n: JsonNumber => n.asInt.toRight(s"expected int, got ${n.raw}")
    case other => Left(s"expected int, got ${other.typeName}")
  }

  val long: Decoder[Long] = {
    case n: JsonNumber => n.asLong.toRight(s"expected long, got ${n.raw}")
    case other => Left(s"expected long, got ${other.typeName}")
  }

  val bigDecimal: Decoder[BigDecimal] = {
    case n: JsonNumber => n.asBigDecimalOption.toRight(s"number out of range: ${n.raw}")
    case other => Left(s"expected number, got ${other.typeName}")
  }

  val boolean: Decoder[Boolean] = {
    case JsonBoolean(b) => Right(b)
    case other => Left(s"expected boolean, got ${other.typeName}")
  }

  /** Passes the raw JSON value through unchanged (used for union/`Any` fields). */
  val any: Decoder[JsonValue] = (j: JsonValue) => Right(j)

  // Boxed decoders so nullable value-typed fields can hold a Java reference (or null).
  val boxedInt: Decoder[java.lang.Integer] = (j: JsonValue) => int(j).right.map(java.lang.Integer.valueOf)
  val boxedLong: Decoder[java.lang.Long] = (j: JsonValue) => long(j).right.map(java.lang.Long.valueOf)
  val boxedDouble: Decoder[java.lang.Double] = (j: JsonValue) => double(j).right.map(java.lang.Double.valueOf)
  val boxedBoolean: Decoder[java.lang.Boolean] = (j: JsonValue) => boolean(j).right.map(java.lang.Boolean.valueOf)

  /** Builds a decoder for a temporal type from a parse function (e.g. `LocalDate.parse`). */
  def temporal[A](label: String, parse: String => A): Decoder[A] = {
    case JsonString(s) =>
      try Right(parse(s)) catch { case _: Exception => Left(s"invalid $label: $s") }
    case other => Left(s"expected $label string, got ${other.typeName}")
  }

  def list[A](elem: Decoder[A]): Decoder[List[A]] = {
    case JsonArray(values) =>
      val b = List.newBuilder[A]
      val it = values.iterator
      var err: Option[String] = None
      var i = 0
      while (it.hasNext && err.isEmpty) {
        elem(it.next()) match {
          case Right(a) => b += a
          case Left(e) => err = Some(s"[$i]: $e")
        }
        i += 1
      }
      err.map(Left(_)).getOrElse(Right(b.result()))
    case other => Left(s"expected array, got ${other.typeName}")
  }

  def map[A](value: Decoder[A]): Decoder[Map[String, A]] = {
    case o: JsonObject =>
      val b = scala.collection.immutable.ListMap.newBuilder[String, A]
      val it = o.fields.iterator
      var err: Option[String] = None
      while (it.hasNext && err.isEmpty) {
        val (k, v) = it.next()
        value(v) match {
          case Right(a) => b += (k -> a)
          case Left(e) => err = Some(s"$k: $e")
        }
      }
      err.map(Left(_)).getOrElse(Right(b.result()))
    case other => Left(s"expected object, got ${other.typeName}")
  }

  /** Converts a decoded string-keyed map into an insertion-ordered `java.util.Map` (for the
   *  `mapType = "java.util.Map"` generation option). */
  def toJavaMap[A](m: Map[String, A]): java.util.Map[String, A] = {
    val jm = new java.util.LinkedHashMap[String, A]()
    m.foreach { case (k, v) => jm.put(k, v) }
    jm
  }

  /** Iterates a `java.util.Map`'s entries as key/value tuples, for encoding a `java.util.Map`
   *  field back to JSON. */
  def javaMapEntries[A](m: java.util.Map[String, A]): Iterator[(String, A)] = {
    val it = m.entrySet().iterator()
    new Iterator[(String, A)] {
      def hasNext: Boolean = it.hasNext
      def next(): (String, A) = { val e = it.next(); (e.getKey, e.getValue) }
    }
  }

  /** Decodes a required object field, prefixing the field name to any error. */
  def required[A](o: JsonObject, name: String, dec: Decoder[A]): Either[String, A] =
    o.field(name) match {
      case Some(v) => dec(v).left.map(e => s"$name: $e")
      case None => Left(s"missing required field '$name'")
    }

  /** Decodes an optional field: absent or JSON null yields None. */
  def optional[A](o: JsonObject, name: String, dec: Decoder[A]): Either[String, Option[A]] =
    o.field(name) match {
      case None | Some(JsonNull) => Right(None)
      case Some(v) => dec(v).left.map(e => s"$name: $e").right.map(Some(_))
    }

  /** Decodes a nullable field: absent or JSON null yields null, otherwise the value. */
  def nullable[A >: Null](o: JsonObject, name: String, dec: Decoder[A]): Either[String, A] =
    o.field(name) match {
      case None | Some(JsonNull) => Right(null)
      case Some(v) => dec(v).left.map(e => s"$name: $e")
    }

  /** Decodes a JSON string then maps it through the named adapter (wire -> model). */
  def adapted(name: String): Decoder[String] =
    (j: JsonValue) => string(j).right.flatMap { s =>
      if (AdapterRegistry.isRegistered(name)) Right(AdapterRegistry.decode(name, s))
      else Left(s"no adapter registered for '$name'")
    }

  /** Encodes a model string as JSON, mapping it through the named adapter first (model -> wire). */
  def adaptedEncode(name: String, value: String): JsonValue =
    JsonString(AdapterRegistry.encode(name, value))
}
