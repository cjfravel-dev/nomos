package dev.cjfravel.nomos.generation

import java.io.{File, PrintWriter}

/**
 * Represents a generated Scala source file
 *
 * @param relativePath The relative path from the output directory (e.g., "com/myapp/models/User.scala")
 * @param content The complete Scala source code
 */
case class GeneratedFile(
  relativePath: String,
  content: String
) {
  /**
   * Writes the file to the given output directory
   */
  def writeTo(outputDir: File): Either[String, File] = {
    try {
      val file = new File(outputDir, relativePath)
      
      // Create parent directories if they don't exist
      file.getParentFile.mkdirs()
      
      // Write the content
      val writer = new PrintWriter(file)
      try {
        writer.write(content)
        Right(file)
      } finally {
        writer.close()
      }
    } catch {
      case e: Exception => Left(s"Failed to write file $relativePath: ${e.getMessage}")
    }
  }

  /**
   * Gets the file name (without path)
   */
  def fileName: String = {
    relativePath.split('/').last
  }
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