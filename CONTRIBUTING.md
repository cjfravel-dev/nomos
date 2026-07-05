# Contributing to Nomos

Thanks for your interest in contributing!

## Building and testing

Nomos targets Java 8 (compiled with `--release 8`, so **JDK 9+ is required to build**) and
Scala 2.12. Build and test the reactor, then the example separately — it exercises the
end-to-end Maven-plugin path, which the reactor excludes:

```bash
mvn clean test
mvn -f nomos-example/pom.xml test
```

CI runs the reactor and the example on both Java 11 and Java 17, and additionally verifies
coverage, the release artifacts, and the end-to-end example build.

## Pull requests

- Keep changes focused and add tests for new behavior. The generator has a compile harness
  (`CompileHarness`) that compiles — and can execute — generated output; prefer it over
  string-only assertions for codegen changes.
- Update the user docs under `docs/` when behavior changes.
- Make sure `mvn clean test` and the example build pass before opening a PR.

## Code style

Scala sources are formatted with [scalafmt](https://scalameta.org/scalafmt/) (`.scalafmt.conf`),
linted with [scalafix](https://scalacenter.github.io/scalafix/) (`.scalafix.conf`:
`RemoveUnused` + `OrganizeImports`), and checked with [scalastyle](http://www.scalastyle.org/)
(`scalastyle-config.xml`, adapted from Apache Spark). All three are enforced on the
`nomos-runtime` and `nomos-core` modules and run as part of the build — scalafmt formatting is
checked in the `validate` phase, and scalafix and scalastyle in the `verify` phase, so
`mvn clean install` (and CI) fail on any violation. All three cover both the main and test
sources.

Apply them locally before opening a PR:

```bash
mvn scalafmt:format                                   # reformat Scala sources in place
mvn -pl nomos-runtime,nomos-core \
  test-compile io.github.evis:scalafix-maven-plugin_2.12:scalafix -Dscalafix.mode=IN_PLACE
mvn -pl nomos-runtime,nomos-core scalastyle:check     # run the scalastyle checks
```

Comments and scaladoc must describe current behavior only — not history, and not removed code.
