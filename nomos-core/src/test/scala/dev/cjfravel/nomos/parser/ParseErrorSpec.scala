package dev.cjfravel.nomos.parser

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ParseErrorSpec extends AnyFlatSpec with Matchers {

  "ParseError messages" should "format each error variant" in {
    ParseError.JsonSyntaxError("boom").message shouldBe "boom"
    ParseError.InvalidType("foo", "root").message should include("Invalid type 'foo'")
    ParseError.InvalidType("foo", "root", "bad").message should include("bad")
    ParseError.MissingField("name", "root").message should include("Missing required field 'name'")
    ParseError.InvalidFieldValue("n", "string", "1", "p").message should include("expected string, got 1")
    ParseError.InvalidDiscriminator("nope", "p").message should include("Invalid discriminator")
    ParseError.InvalidReference("Ref", "p").message should include("Invalid reference 'Ref'")
    ParseError.InvalidReference("Ref", "p", "why").message should include("why")
    ParseError.InvalidConstraint("min", "p", "neg").message should include("Invalid constraint 'min'")
    ParseError.StructureError("oops").message should include("structure error")
  }

  it should "join multiple errors and expose path" in {
    val m = ParseError.MultipleErrors(List(ParseError.MissingField("a", "p"), ParseError.MissingField("b", "p")))
    m.message should include("a")
    m.message should include("b")
    m.path shouldBe "<multiple>"
  }
}
