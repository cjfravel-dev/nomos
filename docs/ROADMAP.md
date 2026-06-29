# Nomos Roadmap — Feature Hit List

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

## Suggested sequencing

1. P0-1..4 — unlocks the bulk of typed models.
2. P0-5 validator registry — prerequisite for cross-field rules.
3. P1-1 + P1-2 — large variant families + tolerant objects.
4. P1-3 + P1-4 — serialization parity.
