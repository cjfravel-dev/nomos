package dev.cjfravel.nomos.model

import scala.collection.immutable.ListMap

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues}

class RuntimeModelSpec extends AnyFlatSpec with Matchers with EitherValues with OptionValues {

  private def definition(
      name: String,
      templateType: TemplateType = ObjectType(ListMap.empty),
      subPackage: Option[String] = None): TemplateDefinition =
    TemplateDefinition(name, templateType, subPackage)

  "TemplateDefinition" should "validate generated type names" in {
    definition("").validateName().value should include("cannot be empty")
    definition("user").validateName().value should include("uppercase")
    definition("User-Model").validateName().value should include("letters, digits, and underscores")
    definition("User_Model2").validateName() shouldBe None
  }

  it should "resolve every base and sub-package combination" in {
    definition("User", subPackage = Some("auth")).fullPackage("com.example") shouldBe "com.example.auth"
    definition("User", subPackage = Some("auth")).fullPackage("") shouldBe "auth"
    definition("User", subPackage = Some("")).fullPackage("com.example") shouldBe "com.example"
    definition("User").fullPackage("com.example") shouldBe "com.example"
    definition("User").fullPackage("") shouldBe ""
  }

  "FieldDef" should "align required and null acceptance with decode behavior" in {
    val required = FieldDef(StringType())
    required.required shouldBe true
    required.acceptsNull shouldBe false

    val optional = FieldDef(StringType(), optional = true)
    optional.required shouldBe false
    optional.acceptsNull shouldBe true

    val nullable = FieldDef(StringType(), nullable = true)
    nullable.required shouldBe true
    nullable.acceptsNull shouldBe true

    val defaulted = FieldDef(StringType(), default = Some("\"x\""))
    defaulted.required shouldBe false
    defaulted.acceptsNull shouldBe true
  }

  "MultiTemplate" should "look up definitions and expose a simple-name map" in {
    val user = definition("User", subPackage = Some("model"))
    val role = definition("Role")
    val template = MultiTemplate("com.example", List(user, role))

    template.fqn(user) shouldBe "com.example.model.User"
    template.getDefinition("User") shouldBe Some(user)
    template.getDefinition("com.example.model.User") shouldBe Some(user)
    template.getDefinition("Missing") shouldBe None
    template.definitionsMap shouldBe Map("User" -> user, "Role" -> role)
  }

  it should "reject empty, malformed, duplicate, and unsupported structures" in {
    MultiTemplate("com.example", Nil).validate() should contain("Template must contain at least one definition")

    val malformed =
      MultiTemplate(
        "Bad.package",
        List(definition("user"), definition("User-Model", subPackage = Some("Bad-sub"))),
        listType = "Vector",
        mapType = "HashMap",
        visibility = Some("public"))
    val errors = malformed.validate()
    errors.exists(_.contains("basePackage")) shouldBe true
    errors.exists(_.contains("uppercase")) shouldBe true
    errors.exists(_.contains("letters, digits, and underscores")) shouldBe true
    errors.exists(_.contains("subPackage")) shouldBe true
    errors.exists(_.contains("Unsupported listType")) shouldBe true
    errors.exists(_.contains("Unsupported mapType")) shouldBe true
    errors.exists(_.contains("Invalid visibility")) shouldBe true

    MultiTemplate("com.class", List(definition("User"))).validate().exists(_.contains("basePackage")) shouldBe true
    MultiTemplate("", List(definition("User"))).validate() should contain("basePackage cannot be empty")
    MultiTemplate("", List(definition("User", subPackage = Some("model")))).validate() shouldBe empty
  }

  it should "scope duplicate names by package" in {
    val samePackage =
      MultiTemplate("com.example", List(definition("User"), definition("User")))
    samePackage.validate().exists(_.contains("Duplicate definition name")) shouldBe true
    MultiTemplate("", List(definition("User"), definition("User")))
      .validate()
      .exists(_ == "Duplicate definition name: User") shouldBe true

    val distinctPackages =
      MultiTemplate(
        "com.example",
        List(definition("User", subPackage = Some("one")), definition("User", subPackage = Some("two"))))
    distinctPackages.validate() shouldBe empty
  }

  it should "validate nested references and allow ref checks to be deferred" in {
    val nested =
      definition(
        "Root",
        ObjectType(
          ListMap(
            "direct" -> FieldDef(ReferenceType("MissingDirect")),
            "recursive" -> FieldDef(RecursiveRef("MissingRecursive")),
            "array" -> FieldDef(ArrayType(ReferenceType("MissingArray"))),
            "map" -> FieldDef(MapType(ReferenceType("MissingMap"))))))
    val discriminator =
      definition(
        "Choice",
        TypeDiscriminator(
          "kind",
          ListMap("a" -> ObjectType(ListMap("value" -> FieldDef(ReferenceType("MissingVariant"))))),
          ListMap("common" -> FieldDef(ReferenceType("MissingCommon")))))
    val template = MultiTemplate("com.example", List(nested, discriminator))

    val errors = template.validate()
    List("MissingDirect", "MissingRecursive", "MissingArray", "MissingMap", "MissingVariant", "MissingCommon")
      .foreach(name => errors.exists(_.contains(name)) shouldBe true)
    template.validate(checkRefs = false) shouldBe empty
  }

  it should "reject ambiguous references but prefer a definition in the referrer's package" in {
    val one = definition("User", subPackage = Some("one"))
    val two = definition("User", subPackage = Some("two"))
    val ambiguous =
      definition("Holder", ObjectType(ListMap("user" -> FieldDef(ReferenceType("User")))), subPackage = Some("other"))
    MultiTemplate("com.example", List(one, two, ambiguous))
      .validate()
      .exists(_.contains("ambiguous type 'User'")) shouldBe true

    val local =
      definition("Holder", ObjectType(ListMap("user" -> FieldDef(ReferenceType("User")))), subPackage = Some("one"))
    MultiTemplate("com.example", List(one, two, local)).validate() shouldBe empty
  }

  it should "enforce discriminator enum compatibility" in {
    val hidden =
      definition(
        "Choice",
        TypeDiscriminator(
          "kind",
          ListMap("a" -> ObjectType(ListMap.empty)),
          includeInOutput = false,
          discriminatorEnum = Some("ChoiceKind")))
    MultiTemplate("com.example", List(hidden))
      .validate()
      .exists(_.contains("requires includeDiscriminator")) shouldBe true

    val prefix =
      hidden.copy(templateType =
        hidden.templateType.asInstanceOf[TypeDiscriminator].copy(includeInOutput = true, variantMatch = "prefix"))
    MultiTemplate("com.example", List(prefix))
      .validate()
      .exists(_.contains("incompatible with variantMatch 'prefix'")) shouldBe true
  }

  it should "accept supported settings and visibility forms" in {
    List(None, Some("private"), Some("protected[this]"), Some("private[com.example]")).foreach { visibility =>
      MultiTemplate(
        "com.example",
        List(definition("User")),
        listType = "Array",
        mapType = "java.util.Map",
        visibility = visibility).validate() shouldBe empty
    }
  }

  "MultiTemplate.single" should "create a one-definition template" in {
    val user = definition("User")
    MultiTemplate.single("com.example", user) shouldBe MultiTemplate("com.example", List(user))
  }

  "MultiTemplate.combine" should "preserve packages and resolve project-wide settings" in {
    val one =
      MultiTemplate(
        "com.example.one",
        List(definition("One")),
        listType = "Array",
        dateType = "java.util.Date",
        visibility = Some("private[example]"))
    val two =
      MultiTemplate(
        "com.example.two",
        List(definition("Two")),
        mapType = "java.util.Map",
        dateTimeType = "java.time.OffsetDateTime",
        fromJsonStyle = "throwing")

    val combined = MultiTemplate.combine(List(one, two)).value
    combined.basePackage shouldBe "com.example"
    combined.definitions.map(_.fullPackage(combined.basePackage)).toSet shouldBe
      Set("com.example.one", "com.example.two")
    combined.listType shouldBe "Array"
    combined.mapType shouldBe "java.util.Map"
    combined.dateType shouldBe "java.util.Date"
    combined.dateTimeType shouldBe "java.time.OffsetDateTime"
    combined.fromJsonStyle shouldBe "throwing"
    combined.visibility shouldBe Some("private[example]")
  }

  it should "retain defaults and report all conflicting settings" in {
    val defaults = MultiTemplate.combine(List(MultiTemplate("com.example", List(definition("User"))))).value
    defaults.basePackage shouldBe "com.example"
    defaults.definitions.head.subPackage shouldBe None

    val conflict =
      MultiTemplate
        .combine(
          List(
            MultiTemplate("one", List(definition("One")), listType = "Array", dateType = "java.util.Date"),
            MultiTemplate(
              "two",
              List(definition("Two")),
              listType = "Vector",
              dateType = "java.time.Instant",
              mapType = "java.util.Map"),
            MultiTemplate("three", List(definition("Three")), mapType = "HashMap")))
        .left
        .value
    conflict should include("listType")
    conflict should include("dateType")
    conflict should include("mapType")

    MultiTemplate.combine(Nil).value shouldBe MultiTemplate("", Nil)
  }
}
