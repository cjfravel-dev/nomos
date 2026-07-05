package dev.cjfravel.nomos.validation

import dev.cjfravel.nomos.json.JsonValue

/**
 * The input to a custom validator.
 *
 * @param node
 *   The JSON value at this validator's definition level: the whole document for a top-level definition, or the
 *   referenced sub-node when the definition is reached through a `$ref`.
 * @param root
 *   The whole document being validated, so a validator can traverse up to siblings or the root regardless of the level
 *   it runs at.
 * @param path
 *   The JSON path to `node` (e.g. `root`, `root.child`, `root.items[2]`), for error paths.
 */
final case class ValidatorContext(node: JsonValue, root: JsonValue, path: String)
