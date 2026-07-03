# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.0.1-alpha2]

Early alpha of the Nomos JSON templating engine:

- `nomos-runtime` — a dependency-free runtime (first-party JSON model, codecs, and validation)
  that generated code depends on; no third-party JSON dependency.
- `nomos-core` — the generator that turns JSON templates into Scala models with `fromJson`/
  `toJson`/`validate`, plus an embedded template for runtime validation.
- `nomos-maven-plugin` — a Maven goal that generates code from templates at build time.
- `nomos-bom` — a bill of materials to align module versions.
