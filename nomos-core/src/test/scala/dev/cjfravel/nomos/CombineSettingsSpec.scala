package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import scala.collection.immutable.ListMap

class CombineSettingsSpec extends AnyFlatSpec with Matchers with EitherValues {

  def t(pkg: String,
        listType: String = "List",
        fromJsonStyle: String = "either",
        dateType: String = "java.time.LocalDate",
        dateTimeType: String = "java.time.LocalDateTime",
        visibility: Option[String] = None) =
    MultiTemplate(pkg, List(TemplateDefinition("D" + pkg.last,
      ObjectType(ListMap("id" -> FieldDef(StringType(), false))))),
      listType, fromJsonStyle, dateType, dateTimeType, visibility = visibility)

  "combine" should "honor a non-default setting declared only in a non-first file" in {
    val merged = MultiTemplate.combine(List(
      t("com.example.a"),
      t("com.example.b", listType = "Array",
        fromJsonStyle = "throwing", dateType = "java.util.Date", dateTimeType = "java.util.Date",
        visibility = Some("private[example]")))).value
    merged.listType shouldBe "Array"
    merged.fromJsonStyle shouldBe "throwing"
    merged.dateType shouldBe "java.util.Date"
    merged.dateTimeType shouldBe "java.util.Date"
    merged.visibility shouldBe Some("private[example]")
  }

  "combine" should "keep defaults when no file overrides them" in {
    val merged = MultiTemplate.combine(List(t("com.example.a"), t("com.example.b"))).value
    merged.listType shouldBe "List"
    merged.fromJsonStyle shouldBe "either"
    merged.dateType shouldBe "java.time.LocalDate"
    merged.dateTimeType shouldBe "java.time.LocalDateTime"
    merged.visibility shouldBe None
  }

  "combine" should "allow the same non-default value in more than one file" in {
    val merged = MultiTemplate.combine(List(
      t("com.example.a", listType = "Array"),
      t("com.example.b", listType = "Array"))).value
    merged.listType shouldBe "Array"
  }

  "combine" should "fail when two files give a setting conflicting non-default values" in {
    val result = MultiTemplate.combine(List(
      t("com.example.a", listType = "Array"),
      t("com.example.b", listType = "Vector")))
    result.isLeft shouldBe true
    val msg = result.left.get
    msg should include("listType")
    msg should include("Array")
    msg should include("Vector")
  }

  "combine" should "report every conflicting setting at once" in {
    val msg = MultiTemplate.combine(List(
      t("com.example.a", listType = "Array", dateType = "java.util.Date"),
      t("com.example.b", listType = "Vector", dateType = "java.time.Instant"))).left.get
    msg should include("listType")
    msg should include("dateType")
  }
}
