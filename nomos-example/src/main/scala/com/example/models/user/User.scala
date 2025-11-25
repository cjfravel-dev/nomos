package com.example.models.user

case class User(
  email: String,
  username: String,
  age: Option[Double],
  id: String,
  roles: List[String]
)

object User {
  import com.example.models.NomosFormats
  import NomosFormats._

  def fromJson(json: String): Either[String, User] = {
    try {
      Right(mapper.readValue(json, classOf[User]))
    } catch {
      case e: Exception => Left(s"Failed to parse JSON: ${e.getMessage}")
    }
  }

  def toJson(obj: User): String = {
    mapper.writeValueAsString(obj)
  }
}
