package dev.cjfravel.nomos.parser

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * The template's top-level object accepts a fixed set of settings keys. An unrecognized key is a
 * mistake (a typo, or a removed setting such as useOptionTypes/mapType) and is rejected with a
 * message naming the offending key, so it never silently no-ops.
 */
class TopLevelKeysSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val parser = new TemplateParser()
  private def parse(json: String) = parser.parseMultiTemplate(json, "com.example")

  "parseMultiTemplate" should "accept the supported top-level settings keys" in {
    val json =
      """{"listType":"Array","fromJsonStyle":"throwing","dateType":"java.util.Date",
        |"dateTimeType":"java.util.Date","visibility":"private[demo]",
        |"definitions":[{"name":"N","template":{"id":"string"}}]}""".stripMargin
    parse(json) shouldBe a[Right[_, _]]
  }

  it should "reject an unknown top-level key, naming it" in {
    val err = parse(
      """{"listTpye":"List","definitions":[{"name":"N","template":{"id":"string"}}]}""").left.value
    err.message should include("listTpye")
  }

  it should "reject the removed useOptionTypes setting as an unknown key" in {
    val err = parse(
      """{"useOptionTypes":true,"definitions":[{"name":"N","template":{"id":"string"}}]}""").left.value
    err.message should include("useOptionTypes")
  }

  it should "reject the removed mapType setting as an unknown key" in {
    val err = parse(
      """{"mapType":"Map","definitions":[{"name":"N","template":{"m":{"$map":"string"}}}]}""").left.value
    err.message should include("mapType")
  }
}
