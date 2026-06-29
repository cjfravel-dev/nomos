package com.example.models.account

import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.{SerializerProvider, DeserializationContext}
import com.fasterxml.jackson.databind.annotation.{JsonSerialize, JsonDeserialize}
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

@JsonSerialize(using = classOf[TierSerializer])
@JsonDeserialize(using = classOf[TierDeserializer])
sealed trait Tier

object Tier {
  case object Free extends Tier
  case object Pro extends Tier

  val values: List[Tier] = List(Free, Pro)

  def fromString(s: String): Option[Tier] = s match {
    case "free" => Some(Free)
    case "pro" => Some(Pro)
    case _ => None
  }

  def asString(v: Tier): String = v match {
    case Free => "free"
    case Pro => "pro"
  }
}

class TierSerializer extends StdSerializer[Tier](classOf[Tier]) {
  override def serialize(value: Tier, gen: JsonGenerator, provider: SerializerProvider): Unit =
    gen.writeString(Tier.asString(value))
}

class TierDeserializer extends StdDeserializer[Tier](classOf[Tier]) {
  override def deserialize(p: JsonParser, ctxt: DeserializationContext): Tier =
    Tier.fromString(p.getValueAsString).getOrElse(throw new IllegalArgumentException("Invalid Tier: " + p.getValueAsString))
}
