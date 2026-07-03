package dev.cjfravel.nomos.serialization

import dev.cjfravel.nomos.json.JsonValue

/**
 * Registry of codecs for externally-defined types referenced by templates via `$extern:`.
 *
 * Generated code decodes/encodes an external field by looking up the codec registered under the
 * type's fully-qualified name. Applications register a codec at startup; an unregistered name
 * fails closed with a descriptive error rather than guessing.
 */
object CodecRegistry {
  private val codecs = scala.collection.concurrent.TrieMap.empty[String, JsonCodec[Any]]

  /** Registers a codec for the given fully-qualified external type name. */
  def register[A](qualifiedName: String)(codec: JsonCodec[A]): Unit =
    codecs(qualifiedName) = codec.asInstanceOf[JsonCodec[Any]]

  def isRegistered(qualifiedName: String): Boolean = codecs.contains(qualifiedName)

  /** Decodes a value of an external type, or fails if no codec is registered. */
  def decode[A](qualifiedName: String, json: JsonValue): Either[String, A] =
    codecs.get(qualifiedName) match {
      case Some(codec) => codec.decode(json).asInstanceOf[Either[String, A]]
      case None => Left(unregistered(qualifiedName))
    }

  /** Encodes a value of an external type, or throws if no codec is registered. */
  def encode(qualifiedName: String, value: Any): JsonValue =
    codecs.get(qualifiedName) match {
      case Some(codec) => codec.encode(value)
      case None => throw new IllegalStateException(unregistered(qualifiedName))
    }

  private def unregistered(qualifiedName: String): String =
    s"No JSON codec registered for external type '$qualifiedName'. " +
      s"""Register one at startup with CodecRegistry.register("$qualifiedName")(codec), """ +
      s"or, if '$qualifiedName' is itself a nomos-generated type, reference it with " +
      s""""$$gen:$qualifiedName" instead of "$$extern:$qualifiedName" to call its decode/encode directly."""

  def clear(): Unit = codecs.clear()
}
