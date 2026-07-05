package dev.cjfravel.nomos.generation

import java.io.File

import scala.io.Source

import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FileWriterSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  val testOutputDir = new File("target/test-file-writer")
  val fileWriter = new FileWriter()

  override def beforeEach(): Unit =
    // Clean up test directory before each test
    if (testOutputDir.exists()) {
      deleteRecursively(testOutputDir)
    }

  override def afterEach(): Unit =
    // Clean up test directory after each test
    if (testOutputDir.exists()) {
      deleteRecursively(testOutputDir)
    }

  private def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) {
      file.listFiles().foreach(deleteRecursively)
    }
    file.delete()
  }

  "FileWriter" should "write a single file successfully" in {
    val generatedFile: GeneratedFile =
      GeneratedFile(
        relativePath = "com/example/User.scala",
        content = "package com.example\n\ncase class User(id: String)\n")

    val result = fileWriter.writeFile(generatedFile, testOutputDir)
    result shouldBe a[Right[_, _]]

    val writtenFile = result.right.get
    writtenFile should exist
    writtenFile.getName shouldBe "User.scala"

    val content = Source.fromFile(writtenFile).mkString
    content should include("package com.example")
    content should include("case class User(id: String)")
  }

  it should "create parent directories automatically" in {
    val generatedFile =
      GeneratedFile(
        relativePath = "com/example/deeply/nested/path/MyClass.scala",
        content = "package com.example.deeply.nested.path\n\ncase class MyClass()\n")

    val result = fileWriter.writeFile(generatedFile, testOutputDir)
    result shouldBe a[Right[_, _]]

    val writtenFile = result.right.get
    writtenFile should exist
    writtenFile.getParentFile should exist
    writtenFile.getParentFile.isDirectory shouldBe true
  }

  it should "overwrite existing files" in {
    val generatedFile1 = GeneratedFile(relativePath = "Test.scala", content = "// Version 1\n")

    val generatedFile2 = GeneratedFile(relativePath = "Test.scala", content = "// Version 2\n")

    fileWriter.writeFile(generatedFile1, testOutputDir)
    val result = fileWriter.writeFile(generatedFile2, testOutputDir)

    result shouldBe a[Right[_, _]]
    val content = Source.fromFile(result.right.get).mkString
    content should include("Version 2")
    content should not include "Version 1"
  }

  it should "write multiple files successfully" in {
    val files =
      List(
        GeneratedFile("com/example/User.scala", "package com.example\n\ncase class User()\n"),
        GeneratedFile("com/example/Order.scala", "package com.example\n\ncase class Order()\n"),
        GeneratedFile("com/example/Product.scala", "package com.example\n\ncase class Product()\n"))

    val result = fileWriter.writeFiles(files, testOutputDir)
    result shouldBe a[Right[_, _]]

    val writtenFiles = result.right.get
    writtenFiles should have length 3
    writtenFiles.foreach(file => file should exist)
  }

  it should "return all errors when writing multiple files fails" in {
    // Create a file where we'll try to write a directory
    val blockingFile = new File(testOutputDir, "com")
    testOutputDir.mkdirs()
    blockingFile.createNewFile()

    val files =
      List(GeneratedFile("com/example/User.scala", "content"), GeneratedFile("com/example/Order.scala", "content"))

    val result = fileWriter.writeFiles(files, testOutputDir)
    result shouldBe a[Left[_, _]]

    val errors = result.left.get
    errors should not be empty
  }

  it should "generate a write report with successes and failures" in {
    // Create a blocking file to cause one write to fail
    testOutputDir.mkdirs()
    val blockingFile = new File(testOutputDir, "blocked")
    blockingFile.createNewFile()

    val files =
      List(
        GeneratedFile("success1.scala", "content 1"),
        GeneratedFile("blocked/file.scala", "content 2"), // This will fail
        GeneratedFile("success2.scala", "content 3"))

    val report = fileWriter.writeFilesWithReport(files, testOutputDir)

    report.successCount shouldBe 2
    report.failureCount shouldBe 1
    report.totalCount shouldBe 3
    report.isFailure shouldBe true
    report.summary should include("2 file(s)")
    report.summary should include("failed to write 1 file(s)")
  }

  it should "generate a successful write report when all files write" in {
    val files = List(GeneratedFile("File1.scala", "content 1"), GeneratedFile("File2.scala", "content 2"))

    val report = fileWriter.writeFilesWithReport(files, testOutputDir)

    report.successCount shouldBe 2
    report.failureCount shouldBe 0
    report.isSuccess shouldBe true
    report.summary should include("Successfully wrote 2 file(s)")
    report.successPaths should have length 2
    report.failureMessages shouldBe empty
  }

  it should "ensure output directory exists" in {
    val dir = new File(testOutputDir, "new/directory")
    dir.exists() shouldBe false

    val result = fileWriter.ensureOutputDirectory(dir)
    result shouldBe a[Right[_, _]]

    val createdDir = result.right.get
    createdDir should exist
    createdDir.isDirectory shouldBe true
  }

  it should "fail to ensure directory if path exists as file" in {
    val file = new File(testOutputDir, "not-a-directory")
    testOutputDir.mkdirs()
    file.createNewFile()

    val result = fileWriter.ensureOutputDirectory(file)
    result shouldBe a[Left[_, _]]

    val error = result.left.get
    error.message should include("not a directory")
  }

  it should "write files with correct content encoding" in {
    val generatedFile: GeneratedFile =
      GeneratedFile(relativePath = "Unicode.scala", content = "// Comment with émojis: 🎉 ✨\npackage example\n")

    val result = fileWriter.writeFile(generatedFile, testOutputDir)
    result shouldBe a[Right[_, _]]

    val content = Source.fromFile(result.right.get, "UTF-8").mkString
    content should include("émojis")
    content should include("🎉")
  }

  it should "provide detailed error messages" in {
    val generatedFile = GeneratedFile(relativePath = "com/example/Test.scala", content = "content")

    // Try to write to a location we know will fail (like root or read-only)
    val invalidDir = new File("/invalid/path/that/does/not/exist")

    val result = fileWriter.writeFile(generatedFile, invalidDir)
    result shouldBe a[Left[_, _]]

    val error = result.left.get
    error shouldBe a[WriteError.IOError]
    error.message should not be empty
  }

  it should "handle file paths with special characters" in {
    val generatedFile =
      GeneratedFile(
        relativePath = "com/example/My-Special_File.scala",
        content = "package com.example\n\ncase class MySpecialFile()\n")

    val result = fileWriter.writeFile(generatedFile, testOutputDir)
    result shouldBe a[Right[_, _]]

    val writtenFile = result.right.get
    writtenFile should exist
    writtenFile.getName shouldBe "My-Special_File.scala"
  }
}
