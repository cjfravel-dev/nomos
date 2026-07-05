package dev.cjfravel.nomos.model

import scala.collection.immutable.ListMap

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MultiTemplateSpec extends AnyFlatSpec with Matchers {

  def userDef: TemplateDefinition =
    TemplateDefinition(
      name = "User",
      templateType = ObjectType(ListMap("id" -> FieldDef(StringType(), optional = false))),
      subPackage = Some("user"))

  "MultiTemplate" should "construct from basePackage and definitions only" in {
    val t = MultiTemplate("com.example.models", List(userDef))
    t.basePackage shouldBe "com.example.models"
    t.definitions should have size 1
  }

  it should "compute fully-qualified name from basePackage + subPackage + name" in {
    val t = MultiTemplate("com.example.models", List(userDef))
    t.fqn(userDef) shouldBe "com.example.models.user.User"
  }

  it should "use basePackage directly when no subPackage" in {
    val d = userDef.copy(subPackage = None)
    val t = MultiTemplate("com.example.models", List(d))
    t.fqn(d) shouldBe "com.example.models.User"
  }

  it should "look up a definition by fully-qualified name" in {
    val t = MultiTemplate("com.example.models", List(userDef))
    t.getDefinition("com.example.models.user.User").map(_.name) shouldBe Some("User")
  }

  it should "look up a definition by simple name for references" in {
    val t = MultiTemplate("com.example.models", List(userDef))
    t.getDefinition("User").map(_.name) shouldBe Some("User")
  }

  it should "validate JSON via the fully-qualified name through MultiValidator" in {
    val t = MultiTemplate("com.example.models", List(userDef))
    val v = new dev.cjfravel.nomos.validation.MultiValidator(t)
    v.validate("""{"id":"x"}""", "com.example.models.user.User") shouldBe a[Right[_, _]]
    v.validate("""{}""", "com.example.models.user.User") shouldBe a[Left[_, _]]
  }

  it should "report no errors for a valid template" in {
    MultiTemplate("com.example.models", List(userDef)).validate() shouldBe empty
  }

  it should "flag duplicate definition names and empty basePackage" in {
    MultiTemplate("", List(userDef, userDef)).validate() should not be empty
  }
}
