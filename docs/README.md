# Nomos Documentation

## Overview

This directory contains comprehensive documentation for the Nomos project.

## Documentation Files

### Core Documentation

- **[TEMPLATE_FORMAT.md](TEMPLATE_FORMAT.md)** - Complete reference for JSON template syntax
  - Basic types (string, number, boolean)
  - Complex types (arrays, objects, type discriminators)
  - Field constraints and validation rules
  - Reference types and multi-definition templates

- **[MULTI_MODULE_DESIGN.md](MULTI_MODULE_DESIGN.md)** - Project architecture
  - Module structure (nomos-bom, nomos-core, nomos-maven-plugin)
  - Dependency management with BOM pattern
  - Build configuration and plugin usage

- **[EXAMPLES.md](EXAMPLES.md)** - Practical usage examples
  - Basic code generation
  - Type discriminators and sealed traits
  - Validation examples
  - Maven plugin configuration

- **[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)** - Design decisions and architecture
  - Component design
  - Feature specifications
  - Design patterns and principles

- **[ROADMAP.md](ROADMAP.md)** - Prioritized feature hit list
  - P0 blockers: typed scalars, enums, maps, defaults, named custom validators
  - P1/P2: variant unions, additionalProperties, pluggable formats/adapters

## Quick Navigation

**Getting Started:**
1. Read [TEMPLATE_FORMAT.md](TEMPLATE_FORMAT.md) to learn template syntax
2. See [EXAMPLES.md](EXAMPLES.md) for usage patterns
3. Check [MULTI_MODULE_DESIGN.md](MULTI_MODULE_DESIGN.md) for project structure

**For Contributors:**
- [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) - Understanding the codebase
- [MULTI_MODULE_DESIGN.md](MULTI_MODULE_DESIGN.md) - Module responsibilities

## Key Concepts

### Templates
JSON files that define the structure of your data models. Nomos generates Scala case classes from these templates.

### Code Generation
The process of converting templates into Scala code, including case classes and serialization/deserialization methods.

### Validation
Runtime verification that JSON data conforms to template specifications, checking types, constraints, and required fields.

### BOM (Bill of Materials)
Maven pattern for centralized dependency version management, ensuring consistent versions across all modules.

## Module Structure

```
nomos/
├── nomos-bom/          # Dependency version management
├── nomos-core/         # Core library (parser, validator, generator)
├── nomos-maven-plugin/ # Maven build integration
└── nomos-example/      # Working example project
```

## Additional Resources

- **Root README**: See [../README.md](../README.md) for quick start guide
- **Example Project**: See [../nomos-example](../nomos-example) for working code
- **Tests**: See test files in nomos-core for detailed examples