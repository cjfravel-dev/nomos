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

Match the surrounding code. Comments should describe current behavior — not history, and not
removed code.
