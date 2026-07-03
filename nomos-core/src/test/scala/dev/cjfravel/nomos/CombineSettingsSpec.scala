package dev.cjfravel.nomos

import dev.cjfravel.nomos.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.immutable.ListMap

class CombineSettingsSpec extends AnyFlatSpec with Matchers {

  def t(pkg: String,
        useOptionTypes: Boolean = true,
        listType: String = "List",
        fromJsonStyle: String = "either",
        dateType: String = "java.time.LocalDate",
        dateTimeType: String = "java.time.LocalDateTime",
        visibility: Option[String] = None) =
    MultiTemplate(pkg, List(TemplateDefinition("D" + pkg.last,
      ObjectType(ListMap("id" -> FieldDef(StringType(), false))))),
      useOptionTypes, listType, fromJsonStyle, dateType, dateTimeType, visibility = visibility)

  "combine" should "honor a non-default setting declared only in a non-first file" in {
    val merged = MultiTemplate.combine(List(
      t("com.example.a"),
      t("com.example.b", useOptionTypes = false, listType = "Array",
        fromJsonStyle = "throwing", dateType = "java.util.Date", dateTimeType = "java.util.Date",
        visibility = Some("private[example]"))))
    merged.useOptionTypes shouldBe false
    merged.listType shouldBe "Array"
    merged.fromJsonStyle shouldBe "throwing"
    merged.dateType shouldBe "java.util.Date"
    merged.dateTimeType shouldBe "java.util.Date"
    merged.visibility shouldBe Some("private[example]")
  }

  "combine" should "keep defaults when no file overrides them" in {
    val merged = MultiTemplate.combine(List(t("com.example.a"), t("com.example.b")))
    merged.useOptionTypes shouldBe true
    merged.listType shouldBe "List"
    merged.fromJsonStyle shouldBe "either"
    merged.dateType shouldBe "java.time.LocalDate"
    merged.dateTimeType shouldBe "java.time.LocalDateTime"
    merged.visibility shouldBe None
  }
}
