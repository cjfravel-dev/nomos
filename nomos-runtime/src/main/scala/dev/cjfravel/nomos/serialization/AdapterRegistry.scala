package dev.cjfravel.nomos.serialization

/**
 * Registry of named (de)serialization adapters. A field may declare an adapter to convert between its on-the-wire
 * representation and its in-model representation, keeping output byte-compatible with existing payloads. Decode runs on
 * parse, encode on output.
 */
object AdapterRegistry {
  // Decode/encode are stored as one value so a registration is atomic: a concurrent reader never
  // sees the decoder present while the encoder is still missing.
  private val adapters = scala.collection.concurrent.TrieMap.empty[String, (String => String, String => String)]

  def register(name: String)(decode: String => String, encode: String => String): Unit =
    adapters(name) = (decode, encode)

  def isRegistered(name: String): Boolean = adapters.contains(name)

  /** Applies the named adapter's decode (wire -> model), or fails closed if none is registered. */
  def decode(name: String, value: String): String =
    adapters.get(name) match {
      case Some((d, _)) => d(value)
      case None => throw new IllegalStateException(unregistered(name))
    }

  /** Applies the named adapter's encode (model -> wire), or fails closed if none is registered. */
  def encode(name: String, value: String): String =
    adapters.get(name) match {
      case Some((_, e)) => e(value)
      case None => throw new IllegalStateException(unregistered(name))
    }

  private def unregistered(name: String): String =
    s"No adapter registered for '$name'. " +
      s"""Register one at startup with AdapterRegistry.register("$name")(decode, encode)."""

  def clear(): Unit = adapters.clear()
}
