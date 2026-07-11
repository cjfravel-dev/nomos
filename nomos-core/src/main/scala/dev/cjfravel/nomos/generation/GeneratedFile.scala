package dev.cjfravel.nomos.generation

import java.io.File

/**
 * Represents a generated Scala source file
 *
 * @param relativePath
 *   The relative path from the output directory (e.g., "com/myapp/models/User.scala")
 * @param content
 *   The complete Scala source code
 */
case class GeneratedFile(relativePath: String, content: String) {

  /**
   * Writes the file to the given output directory
   */
  def writeTo(outputDir: File): Either[String, File] =
    new FileWriter().writeFile(this, outputDir).left.map(_.message)

  /**
   * Gets the file name (without path)
   */
  def fileName: String =
    relativePath.split('/').last
}

object GeneratedFile {

  /**
   * Creates a GeneratedFile from a package name and type name
   */
  def apply(packageName: String, typeName: String, content: String): GeneratedFile = {
    val packagePath = packageName.replace('.', '/')
    val relativePath = s"$packagePath/$typeName.scala"
    GeneratedFile(relativePath, content)
  }
}
