# Recursive Tree Example

This example demonstrates recursive type references for tree-like structures.

## Template

We'll create a TreeNode type that can contain child TreeNodes.

## Running the Example

```scala
import dev.cjfravel.chisel.model._
import dev.cjfravel.chisel.generation._

// Define the template
val treeTemplate = Template(
  name = "TreeNode",
  subPackage = Some("examples.tree"),
  templateType = ObjectType(Map(
    "id" -> FieldDef(StringType(), optional = false),
    "value" -> FieldDef(NumberType(), optional = false),
    "label" -> FieldDef(StringType(), optional = true),
    "children" -> FieldDef(
      ArrayType(RecursiveRef("TreeNode")), 
      optional = false
    )
  ))
)

// Configure the generator
val config = GeneratorConfig(
  basePackage = "com.example",
  outputDir = "target/generated-sources"
)

// Generate code
val generator = new CodeGenerator(config)
val result = generator.generate(treeTemplate)

result match {
  case Right(files) =>
    files.foreach { file =>
      println(s"Generated: ${file.relativePath}")
      file.writeTo(config.outputDirectory)
    }
  case Left(error) =>
    println(s"Error: ${error.message}")
}
```

## Generated Code

The above will generate `target/generated-sources/com/example/examples/tree/TreeNode.scala`:

```scala
package com.example.examples.tree

case class TreeNode(
  id: String,
  value: Double,
  label: Option[String],
  children: List[TreeNode]
)
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
package com.example.examples.tree

val tree = TreeNode(
  id = "root",
  value = 1,
  label = Some("Root"),
  children = List(
    TreeNode(
      id = "left",
      value = 2,
      label = None,
      children = List.empty
    ),
    TreeNode(
      id = "right",
      value = 3,
      label = Some("Right Branch"),
      children = List(
        TreeNode(
          id = "right-child",
          value = 4,
          label = None,
          children = List.empty
        )
      )
    )
  )
)

// Traverse the tree
def printTree(node: TreeNode, indent: Int = 0): Unit = {
  println("  " * indent + s"${node.id}: ${node.value}")
  node.children.foreach(child => printTree(child, indent + 1))
}

printTree(tree)
```

Output:
```
root: 1.0
  left: 2.0
  right: 3.0
    right-child: 4.0