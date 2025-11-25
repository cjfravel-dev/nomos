package dev.cjfravel.nomos.generation

import java.io.{File, PrintWriter}
import scala.util.{Try, Success, Failure}

/**
 * Handles writing generated files to the filesystem
 */
class FileWriter {

  /**
   * Writes a single generated file to disk
   * 
   * @param file The generated file to write
   * @param outputDir The base output directory
   * @return Either an error message or the written file
   */
  def writeFile(file: GeneratedFile, outputDir: File): Either[WriteError, File] = {
    Try {
      val targetFile = new File(outputDir, file.relativePath)
      
      // Create parent directories if they don't exist
      targetFile.getParentFile.mkdirs()
      
      // Write the content
      val writer = new PrintWriter(targetFile)
      try {
        writer.write(file.content)
        targetFile
      } finally {
        writer.close()
      }
    } match {
      case Success(f) => Right(f)
      case Failure(e) => Left(WriteError.IOError(file.relativePath, e.getMessage))
    }
  }

  /**
   * Writes multiple generated files to disk
   * 
   * @param files The list of generated files to write
   * @param outputDir The base output directory
   * @return Either a list of errors or a list of written files
   */
  def writeFiles(files: List[GeneratedFile], outputDir: File): Either[List[WriteError], List[File]] = {
    val results = files.map(file => writeFile(file, outputDir))
    
    val errors = results.collect { case Left(error) => error }
    val successes = results.collect { case Right(file) => file }
    
    if (errors.nonEmpty) {
      Left(errors)
    } else {
      Right(successes)
    }
  }

  /**
   * Writes files and returns a report of what was written
   * 
   * @param files The list of generated files to write
   * @param outputDir The base output directory
   * @return A write report containing successes and failures
   */
  def writeFilesWithReport(files: List[GeneratedFile], outputDir: File): WriteReport = {
    val results = files.map { file =>
      writeFile(file, outputDir) match {
        case Right(writtenFile) => (file, Some(writtenFile), None)
        case Left(error) => (file, None, Some(error))
      }
    }
    
    val successes = results.collect { case (gen, Some(written), _) => (gen, written) }
    val failures = results.collect { case (gen, _, Some(error)) => (gen, error) }
    
    WriteReport(successes, failures)
  }

  /**
   * Ensures the output directory exists
   */
  def ensureOutputDirectory(outputDir: File): Either[WriteError, File] = {
    Try {
      if (!outputDir.exists()) {
        outputDir.mkdirs()
      }
      if (!outputDir.isDirectory) {
        throw new IllegalArgumentException(s"Output path exists but is not a directory: ${outputDir.getAbsolutePath}")
      }
      outputDir
    } match {
      case Success(dir) => Right(dir)
      case Failure(e) => Left(WriteError.DirectoryError(outputDir.getAbsolutePath, e.getMessage))
    }
  }
}

/**
 * Report of write operations
 */
case class WriteReport(
  successes: List[(GeneratedFile, File)],
  failures: List[(GeneratedFile, WriteError)]
) {
  def successCount: Int = successes.length
  def failureCount: Int = failures.length
  def totalCount: Int = successCount + failureCount
  
  def isSuccess: Boolean = failures.isEmpty
  def isFailure: Boolean = failures.nonEmpty
  
  def summary: String = {
    if (isSuccess) {
      s"Successfully wrote $successCount file(s)"
    } else {
      s"Wrote $successCount file(s), failed to write $failureCount file(s)"
    }
  }
  
  def successPaths: List[String] = successes.map(_._2.getAbsolutePath)
  def failureMessages: List[String] = failures.map { case (gen, err) =>
    s"${gen.relativePath}: ${err.message}"
  }
}

/**
 * Errors that can occur during file writing
 */
sealed trait WriteError {
  def message: String
}

object WriteError {
  case class IOError(path: String, details: String) extends WriteError {
    def message: String = s"Failed to write file $path: $details"
  }
  
  case class DirectoryError(path: String, details: String) extends WriteError {
    def message: String = s"Failed to create/access directory $path: $details"
  }
  
  case class PermissionError(path: String) extends WriteError {
    def message: String = s"Permission denied writing to $path"
  }
}

object FileWriter {
  def apply(): FileWriter = new FileWriter()
}