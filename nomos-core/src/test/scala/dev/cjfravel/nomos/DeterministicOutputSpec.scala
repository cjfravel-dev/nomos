package dev.cjfravel.nomos

import scala.collection.immutable.ListMap

import dev.cjfravel.nomos.generation.TemplateSerializer
import dev.cjfravel.nomos.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * `variantNames` is a plain `Map`; with 5+ entries Scala uses a `HashMap` whose iteration order is unspecified, so the
 * embedded template it is serialized into is not byte-stable. Entries are now emitted in a deterministic (sorted) order
 * so generated `NomosFormats.scala` is reproducible.
 */
class DeterministicOutputSpec extends AnyFlatSpec with Matchers {

  "TemplateSerializer" should "emit variantNames entries in a deterministic sorted order" in {
    val keys = List("zebra", "alpha", "mango", "delta", "kiwi") // 5+ entries => backed by HashMap
    val variants = ListMap(keys.map(k => k -> ObjectType(ListMap("x" -> FieldDef(StringType(), optional = false)))): _*)
    val variantNames = keys.map(k => k -> ("N_" + k)).toMap
    val disc = TypeDiscriminator("kind", variants, ListMap.empty, includeInOutput = true, variantNames = variantNames)

    val s = TemplateSerializer.serializeTemplateType(disc)
    val positions = keys.sorted.map(k => s.indexOf("\"" + k + "\" -> \"N_" + k + "\""))
    positions.foreach(_ should be >= 0)
    positions shouldBe positions.sorted
  }
}
