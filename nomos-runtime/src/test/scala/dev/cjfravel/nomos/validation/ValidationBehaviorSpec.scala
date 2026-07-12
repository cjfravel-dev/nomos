package dev.cjfravel.nomos.validation

import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer

import dev.cjfravel.nomos.json._
import dev.cjfravel.nomos.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, EitherValues}

class ValidationBehaviorSpec extends AnyFlatSpec with Matchers with EitherValues with BeforeAndAfterEach {

  private def validator(
      templateType: TemplateType,
      dateType: String = "java.time.LocalDate",
      dateTimeType: String = "java.time.LocalDateTime"): MultiValidator =
    new MultiValidator(
      MultiTemplate(
        "com.example",
        List(TemplateDefinition("Root", templateType)),
        dateType = dateType,
        dateTimeType = dateTimeType))

  private def errors(result: Either[List[ValidationError], JsonValue]): List[ValidationError] = result.left.value

  override def beforeEach(): Unit = ValidatorRegistry.clear()
  override def afterEach(): Unit = ValidatorRegistry.clear()

  "MultiValidator" should "report invalid JSON and unknown definitions" in {
    val v = validator(StringType())
    errors(v.validate("{bad", "Root")).head.message should include("Invalid JSON")
    errors(v.validateJson(JsonString("x"), "Missing")).head.message should include("not found")
    MultiValidator(MultiTemplate.single("com.example", TemplateDefinition("Root", StringType())))
      .validate("\"x\"", "Root") shouldBe a[Right[_, _]]
  }

  it should "enforce string constraints and type" in {
    val formatName = "starts-with-a-runtime"
    FormatRegistry.register(formatName)(_.startsWith("a"))
    val v =
      validator(
        StringType(
          List(MinLength(3), MaxLength(5), Pattern("^[a-z]+$"), Format(formatName), Enum(List("abc", "abcd")))))

    v.validate("\"abc\"", "Root") shouldBe a[Right[_, _]]
    errors(v.validate("\"ab\"", "Root")).exists(_.message.contains("minLength")) shouldBe true
    errors(v.validate("\"abcdef\"", "Root")).exists(_.message.contains("maxLength")) shouldBe true
    errors(v.validate("\"ABC\"", "Root")).exists(_.message.contains("pattern")) shouldBe true
    errors(v.validate("\"bbb\"", "Root")).exists(_.message.contains("format")) shouldBe true
    errors(v.validate("\"abcde\"", "Root")).exists(_.message.contains("enum")) shouldBe true
    errors(v.validate("1", "Root")).head.expected shouldBe "string"
  }

  it should "enforce numeric constraints precisely" in {
    val v = validator(NumberType(List(Min(0.1), Max(1.0), MultipleOf(0.1))))
    v.validate("0.3", "Root") shouldBe a[Right[_, _]]
    errors(v.validate("0.0", "Root")).exists(_.message.contains("min")) shouldBe true
    errors(v.validate("1.1", "Root")).exists(_.message.contains("max")) shouldBe true
    errors(v.validate("0.35", "Root")).exists(_.message.contains("multipleOf")) shouldBe true
    errors(v.validate("\"x\"", "Root")).head.expected shouldBe "number"

    validator(NumberType(List(MultipleOf(0.0)))).validate("0", "Root") shouldBe a[Right[_, _]]
    errors(validator(NumberType(List(MultipleOf(0.0)))).validate("1", "Root"))
      .exists(_.message.contains("multipleOf")) shouldBe true
    validator(DecimalType(List(Min(1.0)))).validate("1.5", "Root") shouldBe a[Right[_, _]]
  }

  it should "enforce integer and long semantics" in {
    val ints = validator(IntType(List(Min(0.0))))
    ints.validate("1", "Root") shouldBe a[Right[_, _]]
    errors(ints.validate("1.5", "Root")).head.actual shouldBe "fractional number"
    errors(ints.validate("2147483648", "Root")).head.message should include("int range")
    errors(ints.validate("\"1\"", "Root")).head.expected shouldBe "int"

    val longs = validator(LongType())
    longs.validate(Long.MaxValue.toString, "Root") shouldBe a[Right[_, _]]
    errors(longs.validate("9223372036854775808", "Root")).head.message should include("long range")
  }

  it should "validate booleans, dates, and datetimes" in {
    validator(BooleanType()).validate("true", "Root") shouldBe a[Right[_, _]]
    errors(validator(BooleanType()).validate("\"true\"", "Root")).head.expected shouldBe "boolean"

    validator(DateType()).validate("\"2026-07-11\"", "Root") shouldBe a[Right[_, _]]
    errors(validator(DateType()).validate("\"2026-99-99\"", "Root")).head.message should include("format: date")
    errors(validator(DateType()).validate("1", "Root")).head.expected shouldBe "date"

    validator(DateTimeType()).validate("\"2026-07-11T12:00:00\"", "Root") shouldBe a[Right[_, _]]
    errors(validator(DateTimeType()).validate("\"bad\"", "Root")).head.message should include("format: datetime")

    validator(DateType(), dateType = "java.util.Date").validate("\"2026-07-11\"", "Root") shouldBe a[Right[_, _]]
    validator(DateTimeType(), dateTimeType = "java.util.Date")
      .validate("\"2026-07-11T12:00:00Z\"", "Root") shouldBe a[Right[_, _]]
  }

  it should "validate arrays and item constraints" in {
    val v = validator(ArrayType(IntType(), List(MinItems(2), MaxItems(3), UniqueItems(true))))
    v.validate("[1,2]", "Root") shouldBe a[Right[_, _]]
    errors(v.validate("[1]", "Root")).exists(_.message.contains("minItems")) shouldBe true
    errors(v.validate("[1,2,3,4]", "Root")).exists(_.message.contains("maxItems")) shouldBe true
    errors(v.validate("[1,1]", "Root")).exists(_.message.contains("uniqueItems")) shouldBe true
    errors(v.validate("[1,\"x\"]", "Root")).exists(_.path == "root[1]") shouldBe true
    errors(v.validate("{}", "Root")).head.expected shouldBe "array"

    val objectArray =
      validator(ArrayType(ObjectType(ListMap("id" -> FieldDef(IntType()))), List(UniqueItems(true))))
    errors(objectArray.validate("""[{"id":1},{"id":1}]""", "Root"))
      .exists(_.message.contains("uniqueItems")) shouldBe true
  }

  it should "validate maps and unions" in {
    val mapValidator = validator(MapType(IntType()))
    mapValidator.validate("""{"a":1,"b":2}""", "Root") shouldBe a[Right[_, _]]
    errors(mapValidator.validate("""{"a":"x"}""", "Root")).head.path shouldBe "root.a"
    errors(mapValidator.validate("[]", "Root")).head.expected shouldBe "object"

    val union = validator(UnionType(List(StringType(), IntType())))
    union.validate("\"x\"", "Root") shouldBe a[Right[_, _]]
    union.validate("1", "Root") shouldBe a[Right[_, _]]
    errors(union.validate("true", "Root")).head.expected shouldBe "one of union types"
  }

  it should "delegate external types and enforce enum values" in {
    validator(ExternalType("example.External")).validate("""{"anything":true}""", "Root") shouldBe a[Right[_, _]]
    validator(ExternalType("example.Generated", generated = true)).validate("null", "Root") shouldBe a[Right[_, _]]

    val enum = validator(EnumType("Color", List("red", "blue")))
    enum.validate("\"red\"", "Root") shouldBe a[Right[_, _]]
    errors(enum.validate("\"green\"", "Root")).head.message should include("enum")
    errors(enum.validate("1", "Root")).head.actual shouldBe "number"
  }

  it should "enforce object fields and additional-property policies" in {
    val fields =
      ListMap(
        "required" -> FieldDef(StringType()),
        "optional" -> FieldDef(IntType(), optional = true),
        "nullable" -> FieldDef(StringType(), nullable = true),
        "defaulted" -> FieldDef(BooleanType(), default = Some("false")))

    val strict = validator(ObjectType(fields))
    strict.validate("""{"required":"x","nullable":null}""", "Root") shouldBe a[Right[_, _]]
    strict.validate("""{"required":"x","optional":null,"nullable":null,"defaulted":null}""", "Root") shouldBe
      a[Right[_, _]]
    errors(strict.validate("{}", "Root")).exists(_.message.contains("Missing required field")) shouldBe true
    errors(strict.validate("""{"required":null}""", "Root")).exists(_.expected == "string") shouldBe true
    errors(strict.validate("""{"required":"x","nullable":null,"extra":1}""", "Root")).head.path shouldBe "root.extra"
    errors(strict.validate("[]", "Root")).head.expected shouldBe "object"

    validator(ObjectType(fields, AllowExtra))
      .validate("""{"required":"x","nullable":null,"extra":1}""", "Root") shouldBe a[Right[_, _]]
    val typed = validator(ObjectType(fields, TypedExtra(IntType())))
    typed.validate("""{"required":"x","nullable":null,"extra":1}""", "Root") shouldBe a[Right[_, _]]
    errors(typed.validate("""{"required":"x","nullable":null,"extra":"bad"}""", "Root")).head.path shouldBe "root.extra"
  }

  it should "validate discriminator variants, common fields, fallback, and malformed tags" in {
    val discriminator =
      TypeDiscriminator(
        "kind",
        ListMap(
          "a" -> ObjectType(ListMap("aValue" -> FieldDef(IntType()))),
          "b" -> ObjectType(ListMap("bValue" -> FieldDef(StringType(), optional = true)))),
        ListMap("id" -> FieldDef(StringType())))
    val v = validator(discriminator)

    v.validate("""{"kind":"a","id":"x","aValue":1}""", "Root") shouldBe a[Right[_, _]]
    errors(v.validate("""{"kind":"a","id":"x"}""", "Root")).exists(_.message.contains("aValue")) shouldBe true
    errors(v.validate("""{"kind":"a","aValue":1}""", "Root")).exists(_.message.contains("id")) shouldBe true
    errors(v.validate("""{"kind":"a","id":"x","aValue":"bad"}""", "Root")).exists(_.path == "root.aValue") shouldBe true
    errors(v.validate("""{"kind":"a","id":"x","aValue":1,"extra":true}""", "Root"))
      .exists(_.path == "root.extra") shouldBe true
    errors(v.validate("""{"kind":"unknown","id":"x"}""", "Root")).head.message should include("Invalid discriminator")
    errors(v.validate("""{"kind":1,"id":"x"}""", "Root")).head.message should include("must be a string")
    errors(v.validate("""{"id":"x"}""", "Root")).head.message should include("Missing required field")
    errors(v.validate("[]", "Root")).head.expected shouldBe "object"

    val fallback = validator(discriminator.copy(fallbackVariant = Some("Unknown")))
    fallback.validate("""{"kind":"unknown","id":"x","anything":true}""", "Root") shouldBe a[Right[_, _]]
    errors(fallback.validate("""{"kind":"unknown"}""", "Root")).exists(_.message.contains("id")) shouldBe true
    fallback.validate("""{"kind":"unknown","id":null}""", "Root") shouldBe a[Left[_, _]]
  }

  it should "prefer an exact discriminator and support prefix matching" in {
    val discriminator =
      TypeDiscriminator(
        "kind",
        ListMap(
          "a" -> ObjectType(ListMap("short" -> FieldDef(StringType()))),
          "abc" -> ObjectType(ListMap("exact" -> FieldDef(StringType())))),
        variantMatch = "prefix")
    val v = validator(discriminator)

    v.validate("""{"kind":"abc","exact":"x"}""", "Root") shouldBe a[Right[_, _]]
    v.validate("""{"kind":"a-suffix","short":"x"}""", "Root") shouldBe a[Right[_, _]]
  }

  "FormatRegistry" should "validate built-ins, custom formats, and unknown names" in {
    val valid =
      Map(
        "email" -> "a@example.com",
        "url" -> "https://example.com",
        "uuid" -> "01234567-89ab-cdef-0123-456789abcdef",
        "guid" -> "01234567-89ab-cdef-0123-456789abcdef",
        "uuidUpper" -> "01234567-89AB-CDEF-0123-456789ABCDEF",
        "guidUpper" -> "01234567-89AB-CDEF-0123-456789ABCDEF",
        "uuidLower" -> "01234567-89ab-cdef-0123-456789abcdef",
        "guidLower" -> "01234567-89ab-cdef-0123-456789abcdef",
        "iso8601" -> "2026-07-11T12:00:00Z",
        "alphaNoWhitespace" -> "Alpha",
        "alpha" -> "Alpha",
        "alphanumeric" -> "Alpha123",
        "alphaUpper" -> "ABC",
        "alphaLower" -> "abc",
        "alphanumericUpper" -> "ABC123",
        "alphanumericLower" -> "abc123",
        "majorAndMinor" -> "2.12",
        "pascalCase" -> "PascalCase1",
        "camelCase" -> "camelCase1",
        "snakeCase" -> "snake_case1",
        "kebabCase" -> "kebab-case1",
        "screamingSnakeCase" -> "SCREAMING_SNAKE_1")
    valid.foreach { case (name, value) =>
      withClue(name) {
        FormatRegistry.isRegistered(name) shouldBe true
        FormatRegistry.validate(name, value) shouldBe true
        FormatRegistry.validate(name, "! invalid !") shouldBe false
      }
    }
    FormatRegistry.validate("unknown-format", "anything") shouldBe true
    FormatRegistry.register("runtime-even-length")(_.length % 2 == 0)
    FormatRegistry.validate("runtime-even-length", "four") shouldBe true
    FormatRegistry.validate("runtime-even-length", "odd") shouldBe false
  }

  "ValidatorRegistry" should "run named validators and prefix their errors" in {
    val context = ValidatorContext(JsonString("node"), JsonString("root"), "root.value")
    ValidatorRegistry.register("runtime.reject") { ctx =>
      List(ValidationError(ctx.path, "rejected", "accepted", ctx.node.typeName))
    }
    ValidatorRegistry.isRegistered("runtime.reject") shouldBe true
    val registered = ValidatorRegistry.run("runtime.reject", context)
    registered.head.path shouldBe "root.value"
    registered.head.message shouldBe "[runtime.reject] rejected"

    val unknown = ValidatorRegistry.run("runtime.unknown", context)
    unknown.head.message should include("Unknown validator")
    unknown.head.path shouldBe "root.value"
  }

  it should "run phase two only after structural validation" in {
    var calls = 0
    ValidatorRegistry.register("runtime.count") { _ =>
      calls += 1
      Nil
    }
    val template =
      MultiTemplate(
        "com.example",
        List(
          TemplateDefinition(
            "Root",
            ObjectType(ListMap("id" -> FieldDef(StringType()))),
            validators = List("runtime.count"))))
    val v = new MultiValidator(template)

    v.validate("""{"id":"x"}""", "Root") shouldBe a[Right[_, _]]
    calls shouldBe 1
    v.validate("{}", "Root") shouldBe a[Left[_, _]]
    calls shouldBe 1
  }

  it should "run referenced validators through every container with node, root, and path" in {
    val seen = ListBuffer.empty[ValidatorContext]
    ValidatorRegistry.register("runtime.capture") { context =>
      seen += context
      Nil
    }
    val child =
      TemplateDefinition(
        "Child",
        ObjectType(ListMap("value" -> FieldDef(StringType()))),
        validators = List("runtime.capture"))
    val root =
      TemplateDefinition(
        "Root",
        ObjectType(
          ListMap(
            "direct" -> FieldDef(ReferenceType("Child")),
            "array" -> FieldDef(ArrayType(ReferenceType("Child"))),
            "map" -> FieldDef(MapType(ReferenceType("Child"))),
            "union" -> FieldDef(UnionType(List(ReferenceType("Child"), StringType())))),
          TypedExtra(ReferenceType("Child"))))
    val template = MultiTemplate("com.example", List(root, child))
    val json =
      """{"direct":{"value":"d"},"array":[{"value":"a"}],"map":{"one":{"value":"m"}},""" +
        """"union":{"value":"u"},"extra":{"value":"e"}}"""
    val v = new MultiValidator(template)

    v.reachesAnyValidator("Root") shouldBe true
    v.reachesAnyValidator("Child") shouldBe true
    v.reachesAnyValidator("Missing") shouldBe false
    v.validate(json, "Root") shouldBe a[Right[_, _]]
    seen.map(_.path).toSet shouldBe
      Set("root.direct", "root.array[0]", "root.map.one", "root.union", "root.extra")
    seen.foreach(_.root shouldBe seen.head.root)
  }

  it should "compute validator reachability through each supported container" in {
    ValidatorRegistry.register("runtime.noop")(_ => Nil)
    val child =
      TemplateDefinition(
        "Child",
        ObjectType(ListMap("value" -> FieldDef(StringType()))),
        validators = List("runtime.noop"))
    val variantReachability =
      TypeDiscriminator("kind", ListMap("a" -> ObjectType(ListMap("child" -> FieldDef(ReferenceType("Child"))))))
    val roots =
      List(
        TemplateDefinition("ArrayRoot", ArrayType(ReferenceType("Child"))),
        TemplateDefinition("MapRoot", MapType(ReferenceType("Child"))),
        TemplateDefinition("UnionRoot", UnionType(List(StringType(), ReferenceType("Child")))),
        TemplateDefinition("ExtraRoot", ObjectType(ListMap.empty, TypedExtra(ReferenceType("Child")))),
        TemplateDefinition("RecursiveRoot", RecursiveRef("Child")),
        TemplateDefinition("VariantRoot", variantReachability))
    val v = new MultiValidator(MultiTemplate("com.example", child :: roots))

    roots.foreach(root => withClue(root.name)(v.reachesAnyValidator(root.name) shouldBe true))
  }

  it should "traverse validators in discriminator common and variant fields" in {
    val seen = ListBuffer.empty[String]
    ValidatorRegistry.register("runtime.capture") { context =>
      seen += context.path
      Nil
    }
    val child =
      TemplateDefinition(
        "Child",
        ObjectType(ListMap("value" -> FieldDef(StringType()))),
        validators = List("runtime.capture"))
    val choice =
      TemplateDefinition(
        "Choice",
        TypeDiscriminator(
          "kind",
          ListMap("a" -> ObjectType(ListMap("variant" -> FieldDef(ReferenceType("Child"))))),
          ListMap("common" -> FieldDef(ReferenceType("Child")))))
    val v = new MultiValidator(MultiTemplate("com.example", List(choice, child)))

    v.validate("""{"kind":"a","common":{"value":"c"},"variant":{"value":"v"}}""", "Choice") shouldBe a[Right[_, _]]
    seen.toSet shouldBe Set("root.common", "root.variant")
  }

  it should "resolve fully-qualified references and reject ambiguous runtime references" in {
    val one =
      TemplateDefinition("Child", ObjectType(ListMap("value" -> FieldDef(StringType()))), subPackage = Some("one"))
    val two =
      TemplateDefinition("Child", ObjectType(ListMap("value" -> FieldDef(IntType()))), subPackage = Some("two"))
    val qualified =
      TemplateDefinition("Qualified", ObjectType(ListMap("child" -> FieldDef(ReferenceType("com.example.one.Child")))))
    val ambiguous =
      TemplateDefinition(
        "Ambiguous",
        ObjectType(ListMap("child" -> FieldDef(ReferenceType("Child")))),
        subPackage = Some("other"))
    val v = new MultiValidator(MultiTemplate("com.example", List(one, two, qualified, ambiguous)))

    v.validate("""{"child":{"value":"x"}}""", "Qualified") shouldBe a[Right[_, _]]
    errors(v.validate("""{"child":{"value":"x"}}""", "Ambiguous"))
      .exists(_.message.contains("Unresolved reference")) shouldBe true
  }

  it should "accept nullable discriminator fields and optional omissions" in {
    val discriminator =
      TypeDiscriminator(
        "kind",
        ListMap(
          "a" -> ObjectType(
            ListMap(
              "nullable" -> FieldDef(StringType(), nullable = true),
              "optional" -> FieldDef(IntType(), optional = true)))),
        ListMap(
          "commonNullable" -> FieldDef(StringType(), nullable = true),
          "commonOptional" -> FieldDef(IntType(), optional = true)))
    val v = validator(discriminator)

    v.validate("""{"kind":"a","commonNullable":null,"nullable":null}""", "Root") shouldBe a[Right[_, _]]

    val fallback = validator(discriminator.copy(fallbackVariant = Some("Unknown")))
    fallback.validate("""{"kind":"unknown","commonNullable":null}""", "Root") shouldBe a[Right[_, _]]
  }

  "ValidationError" should "render and construct every public error shape" in {
    ValidationError("root", "bad", "good", "actual").fullMessage shouldBe
      "root: bad (expected: good, got: actual)"
    ValidationError.typeMismatch("root", "string", "number").message shouldBe "Type mismatch"
    ValidationError.missingField("root", "id").actual shouldBe "missing"
    ValidationError.constraintViolation("root", "min: 1", "0").message should include("Constraint violation")
    ValidationError.invalidDiscriminator("root", "kind", Set("a"), "b").message should include("Invalid discriminator")
    ValidationError.extraField("root", "extra").message should include("Extra field")
  }
}
