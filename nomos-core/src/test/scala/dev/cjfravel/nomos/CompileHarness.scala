package dev.cjfravel.nomos

import dev.cjfravel.nomos.generation.GeneratedFile

import java.nio.file.Files
import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.reporters.StoreReporter

/**
 * Shared harness for tests that compile (and optionally execute) generated output in-process.
 * String-only assertions on generated source can let type errors slip through, so these tests
 * compile the emitted code against nomos-runtime, and `runDriver` additionally runs it.
 */
trait CompileHarness {

  /** Compiles the given sources to a fresh out dir, returning (compiler error messages, outDir). */
  protected def compile(files: List[GeneratedFile]): (Seq[String], String) = {
    val srcDir = Files.createTempDirectory("nomos-compile-src")
    val paths = files.map { f =>
      val p = srcDir.resolve(f.relativePath)
      Files.createDirectories(p.getParent)
      Files.write(p, f.content.getBytes("UTF-8"))
      p.toString
    }
    val outDir = Files.createTempDirectory("nomos-compile-out").toString
    val settings = new Settings()
    settings.usejavacp.value = true
    settings.classpath.value = System.getProperty("java.class.path")
    settings.outdir.value = outDir
    val reporter = new StoreReporter(settings)
    val global = new Global(settings, reporter)
    new global.Run().compile(paths)
    val errs = reporter.infos.collect { case i if i.severity == reporter.ERROR => s"${i.pos}: ${i.msg}" }.toSeq
    (errs, outDir)
  }

  /** Compiles the generated files and returns any compiler error messages. */
  protected def compileErrors(files: List[GeneratedFile]): Seq[String] = compile(files)._1

  /**
   * Compiles sources + a driver object, then invokes `<driverFqn>.run()` (a no-arg method that
   * returns a String) and returns its result. Throws if compilation fails.
   */
  protected def runDriver(files: List[GeneratedFile], driverFqn: String): String = {
    val (errs, outDir) = compile(files)
    if (errs.nonEmpty) throw new AssertionError("compilation failed:\n" + errs.mkString("\n"))
    val loader = new java.net.URLClassLoader(Array(new java.io.File(outDir).toURI.toURL), getClass.getClassLoader)
    loader.loadClass(driverFqn).getMethod("run").invoke(null).asInstanceOf[String]
  }
}
