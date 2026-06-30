package dev.cjfravel.nomos.serialization

/**
 * Registry of named (de)serialization adapters. A field may declare an adapter to convert
 * between its on-the-wire representation and its in-model representation, keeping output
 * byte-compatible with existing payloads. Decode runs on parse, encode on output.
 */
object AdapterRegistry {
  private val decoders = scala.collection.concurrent.TrieMap.empty[String, String => String]
  private val encoders = scala.collection.concurrent.TrieMap.empty[String, String => String]

  def register(name: String)(decode: String => String, encode: String => String): Unit = {
    decoders(name) = decode
    encoders(name) = encode
  }

  def isRegistered(name: String): Boolean = decoders.contains(name)

  def decode(name: String, value: String): String = decoders.get(name).map(_(value)).getOrElse(value)

  def encode(name: String, value: String): String = encoders.get(name).map(_(value)).getOrElse(value)

  def clear(): Unit = { decoders.clear(); encoders.clear() }
}
