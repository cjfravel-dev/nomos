# Recursive Tree Example

This example demonstrates recursive type references for tree-like structures.

## Template

Place the template at `src/main/resources/nomos/templates/com/example/examples/tree/tree-node.json`. The Maven plugin derives the base package `com.example.examples.tree` from that path. Recursive self-reference uses `$ref:TreeNode`.

```json
{
  "useOptionTypes": true,
  "listType": "List",
  "definitions": [
    {
      "name": "TreeNode",
      "description": "Recursive tree node",
      "template": {
        "id": "string",
        "value": "number",
        "label": { "$optional": "string" },
        "children": ["$ref:TreeNode"]
      }
    }
  ]
}
```

## Running the Example

With the Maven plugin configured, generate sources with:

```bash
mvn generate-sources
```

Or use the public API directly:

```scala
import dev.cjfravel.nomos.Nomos
import scala.io.Source

val source = Source.fromFile("src/main/resources/nomos/templates/com/example/examples/tree/tree-node.json")
val templateJson = try source.mkString finally source.close()

val generated = for {
  template <- Nomos.parseTemplate(templateJson, "com.example.examples.tree")
  report <- Nomos.generateCode(template, "target/generated-sources")
} yield report
```

## Generated Code

The template generates `target/generated-sources/com/example/examples/tree/TreeNode.scala` plus `NomosFormats` in the same base package:

```scala
package com.example.examples.tree

case class TreeNode(
  id: String,
  value: Double,
  label: Option[String],
  children: List[TreeNode]
)

object TreeNode {
  import NomosFormats._
  import dev.cjfravel.nomos.json._
  import dev.cjfravel.nomos.serialization.{Codecs, CodecRegistry}
  import dev.cjfravel.nomos.validation.ValidationError

  def decode(json: JsonValue): Either[String, TreeNode] = json match {
    case o: JsonObject =>
      for {
        id <- Codecs.required(o, "id", Codecs.string)
        value <- Codecs.required(o, "value", Codecs.double)
        label <- Codecs.optional(o, "label", Codecs.string)
        children <- Codecs.required(o, "children", Codecs.list(TreeNode.decode))
      } yield TreeNode(id, value, label, children)
    case other => Left("TreeNode: expected object, got " + other.typeName)
  }

  def encode(obj: TreeNode): JsonValue = JsonObject.fromFields(List(
    Some("id" -> JsonString(obj.id)),
    Some("value" -> JsonNumber.fromDouble(obj.value)),
    obj.label.map(v => "label" -> JsonString(v)),
    Some("children" -> JsonArray(obj.children.iterator.map(x => TreeNode.encode(x)).toVector))
  ).flatten)

  def fromJson(json: String): Either[String, TreeNode] = Json.parse(json).right.flatMap(decode)

  def toJson(obj: TreeNode): String = Json.write(encode(obj))

  def validate(json: String): Either[List[ValidationError], TreeNode] = {
    validator.validate(json, "com.example.examples.tree.TreeNode") match {
      case Right(_) => fromJson(json).left.map(err => List(ValidationError("root", err, "valid JSON", json)))
      case Left(errors) => Left(errors)
    }
  }
}
```

## Example JSON

Valid JSON for a tree structure:

```json
{
  "id": "root",
  "value": 1,
  "label": "Root Node",
  "children": [
    {
      "id": "child1",
      "value": 2,
      "label": "First Child",
      "children": [
        {
          "id": "grandchild1",
          "value": 3,
          "label": "First Grandchild",
          "children": []
        },
        {
          "id": "grandchild2",
          "value": 4,
          "children": []
        }
      ]
    },
    {
      "id": "child2",
      "value": 5,
      "label": "Second Child",
      "children": []
    }
  ]
}
```

## Usage Example

```scala
import com.example.examples.tree.TreeNode

val tree = TreeNode(
  id = "root",
  value = 1,
  label = Some("Root"),
  children = List(
    TreeNode("left", 2, None, List.empty),
    TreeNode(
      id = "right",
      value = 3,
      label = Some("Right Branch"),
      children = List(TreeNode("right-child", 4, None, List.empty))
    )
  )
)

def printTree(node: TreeNode, indent: Int = 0): Unit = {
  println("  " * indent + s"${node.id}: ${node.value}")
  node.children.foreach(child => printTree(child, indent + 1))
}

val json = TreeNode.toJson(tree)
val validated = TreeNode.validate(json)
printTree(tree)
```

Output:

```text
root: 1.0
  left: 2.0
  right: 3.0
    right-child: 4.0
```
