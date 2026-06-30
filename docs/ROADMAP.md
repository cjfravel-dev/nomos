# Nomos Roadmap — Feature Hit List

## Status

P0-1..5, P1-1..4, P2, P3-1..4, P4-1..4, and P5-1..7 are implemented. Adapters (P1-4) ship as a registry plus
template wiring; generated (de)serialization hooks consume them via `Nomos.adapters`.
P3 parity gaps and P4 multi-template adoption gaps (cross-template refs, open-map objects, variant sub-packages,
prefix dispatch) are closed. P5 exact-parity gaps (nullable-raw fields, external-type refs, native date/datetime,
generated enum types, helper methods, selectable fromJson style, source field order) are closed.

Goal: grow Nomos so it can model rich, real-world JSON schemas (typed scalars, closed
value sets, maps, defaults, polymorphic families, and custom validations) without losing
functionality.

End goal: validate the **same JSON payloads** with **equal-or-stronger** guarantees than a
hand-written validator, from a **single source of truth** that is easier to maintain.

The list is ordered by priority. **P0** items are blockers for serious use. **P1** items
cause meaningful functionality loss. **P2** items are quality polish.

## Guiding principle: schema vs. custom logic

Nomos enforces **schema** — shape, types, cardinality, closed value sets. It should
*not* try to absorb every cross-field, cross-row, or semantic rule declaratively. Those
are **application checks** and belong in code. Conditional / cross-field constraints are
therefore delivered as **named custom validation functions** (see P0-5), not as an
ever-growing constraint DSL.

## Anti-patterns to retire (do not carry these over)

- **Duplicated model + validator** kept in sync by hand — one template should generate
  both the case class and its validator.
- **Stringly-typed markers** in field names to steer validation logic — encode intent in
  the template, not in magic name suffixes.
- **Silent numeric truncation** (e.g. accepting `1.5` where a whole number is required) —
  enforce integer/positivity explicitly (P0-1).
- **Enums duplicated** as both a generated enumeration and a separate "valid values"
  list — derive both from one `enum` (P0-2).
- **Manual variant registration** for unions — derive variants from the discriminator
  template (P1-1).
- **Tolerating unknown keys by default** — reject extras unless `additionalProperties`
  opts in (P1-2).

---

## P0 — Blockers

### P0-1. Integer / Long / Decimal types
Nomos numbers are always `Double`. Real schemas need whole-number and high-precision
types (iteration caps, day counts, large counters, money/precision values, positive-int
caps, decimals with scale).

Template:
```json
{ "retryCount": "int", "fileSize": "long", "rate": "decimal", "depth": { "type": "int", "min": 1 } }
```
Valid JSON:
```json
{ "retryCount": 3, "fileSize": 9000000000, "rate": 12.50, "depth": 1 }
```
Generates `Int` / `Long` / `BigDecimal`. Add whole-number + positivity validation.
*Effort: S.*

### P0-2. Enums / closed value sets
Closed sets are common in schemas; some families have dozens of values.
Both scalar and array forms are needed.

Template:
```json
{ "priority": { "type": "string", "enum": ["low","medium","high"] },
  "colors": { "type": "array", "items": "string", "enum": ["red","green","blue"] } }
```
Valid JSON:
```json
{ "priority": "high", "colors": ["red","blue"] }
```
Generate either `String` + validation, or a Scala `Enumeration`/sealed enum (build flag).
*Effort: M.*

### P0-3. Maps / open key→value objects
Open string maps with unrestricted keys (settings bags, key→value mappings, dynamic
parameters).

Template:
```json
{ "settings": { "$map": "string" } }
```
Valid JSON:
```json
{ "settings": { "theme": "dark", "lang": "en" } }
```
Generates `Map[String, T]`; validates values, keys unrestricted. *Effort: M.*

### P0-4. Default values
Defaults are needed for optional flags, nullable fields, and derived values.

Template:
```json
{ "verbose": { "type": "boolean", "default": false }, "name": "string" }
```
Valid JSON (verbose omitted → defaults to false):
```json
{ "name": "report" }
```
Emit case-class default args; serialize/validate honoring defaults. *Effort: M.*

### P0-5. Custom validation functions (conditional / cross-field) — **named**
Cross-field, cross-row, and conditional rules ("if A then B", column comparisons,
referential checks) are application logic, not schema. Nomos provides a **registry of
named validators** rather than a declarative constraint DSL. Naming gives traceability
and reuse tracking.

Template:
```json
{
  "name": "Reservation",
  "validators": ["dates.startBeforeEnd"],
  "template": { "startDate": "string", "endDate": "string" }
}
```
Valid JSON:
```json
{ "startDate": "2024-01-01", "endDate": "2024-01-05" }
```
Generated code calls a registry hook; users register by name:
```scala
Nomos.validators.register("dates.startBeforeEnd") { (json, ctx) =>
  if (json.before("startDate", "endDate")) Nil
  else List(ValidationError("endDate", "must be >= startDate"))
}
```
Errors and run reports carry the validator name. *Effort: M–L.*

---

## P1 — Major functionality loss

### P1-1. Large ref-variant unions
Big discriminated families can have many variants whose bodies are top-level types.
Discriminators must accept `$ref` variant bodies, not just inline objects.

Template:
```json
{ "$type": { "discriminator": "kind", "variants": { "circle": "$ref:Circle", "square": "$ref:Square" } } }
```
Valid JSON:
```json
{ "kind": "circle", "radius": 4 }
```
*Effort: M.*

### P1-2. `additionalProperties` toggle
Some objects must tolerate extra keys; Nomos currently always rejects unknowns. Add a
per-object `additionalProperties: true | false | <type>`.

Template:
```json
{ "id": "string", "$additionalProperties": "string" }
```
Valid JSON:
```json
{ "id": "a1", "extra": "ok", "note": "kept" }
```
*Effort: S.*

### P1-3. Pluggable formats / domain validators
Make `format` a registry so domain-specific checks plug in (custom string formats,
parameterized numeric formats).

Template:
```json
{ "code": { "type": "string", "format": "hexColor" } }
```
Valid JSON:
```json
{ "code": "#ff8800" }
```
*Effort: M.*

### P1-4. Custom (de)serialization adapters
Allow per-type adapter hooks (e.g. date encodings, legacy/compat shapes) so output stays
byte-compatible with existing payloads.

Template:
```json
{ "createdAt": { "type": "string", "adapter": "epochMillis" } }
```
Valid JSON:
```json
{ "createdAt": "1704067200000" }
```
*Effort: M.*

---

## P2 — Parity / polish

Wire up existing-but-unparsed array constraints — template:
```json
{ "scores": { "type": "array", "items": "number", "minItems": 1, "maxItems": 5, "uniqueItems": true } }
```
Valid JSON:
```json
{ "scores": [1, 2, 3] }
```

Numeric value-set / negativity guards — template:
```json
{ "balance": { "type": "int", "min": 0 } }
```
Valid JSON:
```json
{ "balance": 0 }
```

---

## P4 — Multi-template adoption gaps

Found replacing a large hand-written model split across many sub-packages with generated code.

### P4-1. Cross-template `$ref` resolution — **High**
A `$ref` only resolves to definitions in the same template file, so a model can't be split
one-template-per-package: a root type referencing types in sibling files fails with
"references undefined type". Resolve refs across all templates under the templates root
(shared definition space) so large schemas stay manageable as many small files. Note:
parse-time validation must defer cross-file refs — validate after merging all templates, not
per file — so the maven plugin (which parses each file) also benefits, not just `generateAll`.

`a/root.json`:
```json
{ "definitions": [ { "name": "Root", "template": { "child": "$ref:Child" } } ] }
```
`a/child.json`:
```json
{ "definitions": [ { "name": "Child", "template": { "id": "string" } } ] }
```
*Effort: M.*

### P4-2. Open-map-only objects generate `???` — **High**
An object whose only declared policy is `$additionalProperties` (no named fields) emits an
uncompilable `???` field type. Generate `Map[String, T]` (or `Map[String, Any]` for `true`).

Template:
```json
{ "extras": { "$additionalProperties": true } }
```
Valid JSON:
```json
{ "extras": { "a": 1, "b": "x" } }
```
*Effort: S.*

### P4-3. Collapse identical variants to one shared class — **Resolved**
A discriminator generates a distinct case class per variant value, which would explode
structurally identical variants into many redundant classes. This is handled by `variantNames`:
pointing several discriminator values at the same name generates a single shared class, reused
by all those values and distinguished only by the discriminator value at runtime.

Template (4 values, same shape → one `Number` class):
```json
{ "$type": { "discriminator": "type",
  "variantNames": { "int": "Number", "long": "Number", "double": "Number", "short": "Number" },
  "variants": { "int": {}, "long": {}, "double": {}, "short": {} } } }
```
Generates one `Number` class for all four values.

### P4-4. Prefix dispatch in generated deserialization — **High**
With `variantMatch: prefix`, validation routes a parameterized value (e.g. `Decimal(28,8)`) to
its variant, but generated `fromJson` dispatches on exact discriminator equality
(`case "Decimal" =>`), so the parameterized value falls through to an unknown-type error and
fails to deserialize. Codegen must emit prefix dispatch (`startsWith`) for prefix-matched
discriminators so validation and deserialization agree.

Template:
```json
{ "$type": { "discriminator": "type", "variantMatch": "prefix",
  "variants": { "Decimal": { "scale": "int" }, "String": {} } } }
```
Valid JSON (must both validate and deserialize):
```json
{ "type": "Decimal(28,8)", "scale": 8 }
```
*Effort: S.*

---

## P5 — Exact-parity gaps (replacing a hand-written model)

Found making generated output byte/shape-identical to an existing hand-written model. These
block a drop-in replacement; until then, generated code is close but not identical.

### P5-1. Nullable raw types (no `Option`) — **High**
Legacy models often use a nullable raw type for an optional field (absent → `null`), e.g.
`Array[String]` or `Boolean` defaulting to false — not `Option[T]`. `useOptionTypes=false`
currently makes optional fields *required*, so there is no mode that yields a nullable raw
type. Add an "optional ⇒ nullable raw (no Option wrapper)" generation mode.
```json
{ "tags": { "$optional": ["string"], "nullable": true } }
```
Expected `tags: Array[String]` (null when absent); today `Option[Array[String]]`. *Effort: M.*

> Follow-up (found on retest): `nullable` is honored for plain object fields but **ignored
> inside discriminator `commonFields` and variant fields** — those still emit `Option`.
> Propagate `nullable` into discriminator-generated fields.

### P5-2. Reference external (hand-written) types — **High**
A field can only `$ref` a type defined in the templates. Keeping a field whose type is a rich
hand-written class (not something you want generated) forces either regenerating that type or
dropping the field. Allow referencing an external fully-qualified type that nomos does not
generate.
```json
{ "rules": ["$extern:com.example.legacy.Rule"] }
```
*Effort: M.*

### P5-3. Native date / temporal type — **Medium**
No date type; a date field must be `string`. Add a `date`/`datetime` type mapping to the
platform date class with a configurable (de)serialization format.
```json
{ "effective_date": "datetime" }
```
*Effort: M.*

### P5-4. Closed enum as a generated enum type — **Medium**
`enum` validates a `String` but does not emit a Scala `Enumeration`/sealed enum type, so a
field typed as an enum in the legacy model becomes `String`. Add an option to generate a named
enum type and use it as the field type.
```json
{ "kind": { "type": "string", "enum": ["a","b"], "as": "enumType", "name": "Kind" } }
```
*Effort: M.*

### P5-5. Custom / helper methods on generated classes — **Medium**
No way to attach derived helpers (e.g. a computed display name) to a generated class, so such
methods are lost. Allow declaring derived members in the template (or a partial/companion mixin
the generator preserves).
*Effort: M.*

> Follow-up (found on retest): declared `methods` emit into the **companion object only**, so an
> instance method that references fields (e.g. `def displayName = name + version`) does not
> compile. Support instance-level methods (emit into the case class body).

### P5-6. Pluggable serializer + `fromJson` signature — **Resolved**
Generated serialization is now a first-party, dependency-free codec (no third-party JSON library),
and `fromJson` is selectable between `Either`-returning (default) and throwing `T` via the
template-level `"fromJsonStyle"`.

> Follow-up (found on retest): the template-level `"fromJsonStyle": "throwing"` is not wired
> through the maven plugin to the generator, so generated `fromJson` still returns `Either`.
> Plumb the template flag into `GeneratorConfig` (same class of plugin-vs-core wiring as P4-1).

### P5-8. Date target type — **Resolved**
`date`/`datetime` map to `java.time.LocalDate`/`LocalDateTime` by default. Override per project
with template-level `dateType` / `dateTimeType` (any fully-qualified class) to match a legacy
model, e.g. `java.util.Date`.
*Effort: S.*

### P5-7. Preserve source field order — **Low**
Generated case-class fields are reordered relative to the template, changing positional
construction and serialized key order. Preserve declaration order.
*Effort: S.*

---

## P6 — Multi-template config consistency

### P6-1. `combine` drops per-file generation settings — **Resolved**
When generating from many template files (one per package), `MultiTemplate.combine` now treats
`useOptionTypes`/`listType`/`fromJsonStyle`/`dateType`/`dateTimeType` consistently: it takes any
non-default value across files, so a setting declared in any template is honored regardless of
file-discovery order.
*Effort: S.*

---

## P7 — Full-module port gaps

Found porting two large modules end-to-end (a 9-variant config union and a 92-variant step
union) to generated code, one template per sub-package.

### P7-1. Boxed / nullable numerics — **Resolved**
A nullable numeric optional field generates a Scala primitive with a null default
(`f: Long = null`, `f: Int = null`), which does not compile — `scala.Int`/`Long` are value
types and cannot be null. Legacy models use boxed `java.lang.Integer`/`Long` for nullable
numbers. Add a boxed/nullable numeric type (emit `java.lang.Integer`/`Long`, or `Option[Int]`),
selected when a numeric field is `nullable`.
```json
{ "max_files": { "$optional": "int", "nullable": true } }
```
A nullable numeric now generates the boxed Java type with a null default — `java.lang.Integer`,
`java.lang.Long`, `java.lang.Double`, `java.lang.Boolean` — which compiles. Reference types
(BigDecimal, List, String) stay raw. *Effort: M.*

### P7-2. Throwing `fromJson` for unions — **Resolved**
`fromJsonStyle: "throwing"` is honored for plain definitions but ignored for discriminated
unions (and `variantNames` unions), whose generated `fromJson` still returns `Either`. Apply
the selected style now applies to discriminated and `variantNames` unions: a throwing
project generates a throwing union `fromJson` (and `validate` adapts). *Effort: S.*

### P7-3. Configurable map type — **Resolved**
Open maps (`$map`) always emit a Scala `Map[String, T]`. A legacy model using
`java.util.Map[String, T]` can't be matched. Make the generated map type configurable
(like `dateType`/`listType`): template-level `mapType` overrides the generated map type,
e.g. `java.util.Map`. *Effort: S.*

### P7-4. Explicit `double` keyword — **Resolved**
`number` maps to `Double` and `decimal` to `BigDecimal`, but there is no explicit `double`
type, so a `Double` field is non-obvious (authors reach for `decimal` and get `BigDecimal`).
`double` is now an explicit keyword aliasing the `Double` mapping (alongside `number`).
*Effort: S.*

---

## P8 — Validation &amp; serialization wiring (full end-to-end migration)

Found taking real modules all the way: deleting the hand-rolled validation DSL and routing
all validation/(de)serialization through generated `validate()`/`fromJson`/`toJson`. The model
layer ports cleanly; these are the friction points at the validation + serde boundary.

### P8-1. `datetime` rejects ISO-8601 instants with `Z` — **High**
The generated `datetime` validator rejects values like `2024-01-02T09:57:26Z` (trailing `Z`
UTC instant), which are extremely common in real payloads. Accept the `Z` (and offset) forms
of ISO-8601. Today this forces suppressing datetime errors as a workaround.
```json
{ "effective_date": "datetime" }   // must accept "2024-01-02T09:57:26Z"
```
*Effort: S.*

### P8-2. Nested unions / external-type deserialization — **Resolved** (dependency-free migration)
Generated `fromJson`/`toJson` are now explicit, reflection-free first-party codecs (no Jackson).
Nested discriminated unions decode directly via each type's generated `decode`, and `$extern`
types are decoded/encoded through a registered codec (`dev.cjfravel.nomos.serialization.CodecRegistry`,
keyed by fully-qualified name). Applications register an external codec at startup.

### P8-3. `validate()` does not recurse into `$extern` types — **Medium**
Fields typed as `$extern` are treated opaquely by the generated validator (presence/array shape
only), so their internals aren't schema-checked. Allow an external type to supply a validator
nomos can call, or document that externs are unvalidated.
*Effort: M.*

### P8-4. `toJson` parity (null emission, key order, dates) — **Resolved** (dependency-free migration)
Generated encoders now emit object keys in template field order, omit absent `Option` fields,
and format dates via `toString` (ISO-8601). Numbers preserve their lexeme, so round-trips are
byte-stable. Further emission knobs (keep-null, custom date format) can be added if needed.

### P8-5. Companion (de)serialization hook — **Resolved** (dependency-free migration)
Each generated companion exposes public `decode`/`encode` (and `fromJson`/`toJson`) so consumers
can compose custom wrapping without editing generated code, with no global mutable state.

---

## Suggested sequencing

1. P0-1..4 — unlocks the bulk of typed models.
2. P0-5 validator registry — prerequisite for cross-field rules.
3. P1-1 + P1-2 — large variant families + tolerant objects.
4. P1-3 + P1-4 — serialization parity.
5. P3-1..4 — real-payload parity blockers (escaped keys + parameterized variants are highest impact).
6. P4-1, P4-2, P4-4 — multi-template adoption (cross-template refs, open-map objects, prefix dispatch in codegen).
7. P5-1..7 — exact-parity gaps (nullable-raw + external-type refs are highest impact).

---

## P3 — Real-payload parity gaps

Found while validating real-world payloads end-to-end. P3-1 and P3-2 are the highest-impact
blockers for object-heavy schemas.

### P3-1. Escaped keys (reserved-keyword field names) — **High**
A field literally named `type` (or `default`, `enum`, `items`, etc.) collides with template
keywords, so `{ "name": "string", "type": "string" }` is read as a type spec, not an object
with a `type` field. Add backtick escaping: a quoted key strips its backticks to become a
literal field name. Today backticks are kept verbatim — implement the unescape.

Template:
```json
{ "name": "string", "`type`": "string" }
```
Valid JSON:
```json
{ "name": "Guid", "type": "String" }
```
*Effort: S.*

### P3-2. Parameterized discriminator values — **High**
Discriminator matching is exact, so a value like `Decimal(28,8)` cannot select a `Decimal`
variant. Support prefix/pattern variant keys so families with parameterized tags resolve.

Template:
```json
{ "$type": { "discriminator": "type", "variantMatch": "prefix",
  "variants": { "Decimal": { "scale": "int" }, "String": {} } } }
```
Valid JSON:
```json
{ "type": "Decimal(28,8)" }
```
*Effort: M.*

### P3-3. Union value types (maps & fields) — **Medium**
A value may legitimately be one of several types (e.g. `string` OR array-of-string). Allow a
union so a single field/map accepts alternatives without a discriminator.

Template:
```json
{ "settings": { "$map": ["string", ["string"]] } }
```
Valid JSON:
```json
{ "settings": { "a": "x,y", "b": ["x","y"] } }
```
*Effort: M.*

### P3-4. Serialization round-trip parity test — **Medium**
Confirm generated `toJson`/`fromJson` reproduce the original payload byte-for-byte (key
order, discriminator placement) so generated code can replace hand-written serializers.
*Effort: S.*
