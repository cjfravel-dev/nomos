package dev.cjfravel.nomos.serialization

import dev.cjfravel.nomos.json.JsonValue

/**
 * A reflection-free JSON codec for a generated type. Generated companions implement this so
 * encoding/decoding never depends on a third-party JSON library.
 */
trait JsonCodec[A] {
  def encode(value: A): JsonValue
  def decode(json: JsonValue): Either[String, A]
}
