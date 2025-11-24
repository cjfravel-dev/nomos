# Chisel - Multi-Module Architecture

A Scala library for defining JSON templates and generating type-safe case classes with automatic JSON serialization/deserialization.

## Project Structure

```
chisel/
├── pom.xml                  # Parent POM (aggregator)
├── pom-single-module-backup.xml  # Original single-module POM (backup)
├── chisel-runtime/          # Runtime library with shaded json4s
│   ├── pom.xml
│   ├── README.md
│   └── src/main/scala/
│       └── dev/cjfravel/chisel/runtime/
│           ├── ChiselFormats.scala      # Base trait for formats
│           └── ChiselSerializer.scala    # Discriminator helper
├── chisel-core/             # Core library (template parsing & code gen)
│   ├── pom.xml
│   └── (uses ../src for source code)
├── chisel-maven-plugin/     # Maven plugin for build integration
│   ├── pom.xml
│   └── src/main/java/
│       └── dev/cjfravel/chisel/maven/
│           └── GenerateMojo.java        # Maven goal implementation
└── chisel-example/          # Example project
    ├── pom.xml
    ├── README.md
    ├── src/main/resources/templates/
    │   └── user.json                    # Template definition
    └── src/main/scala/com/example/
        └── ExampleApp.scala             # Application code
```

## Module Descriptions

### chisel-runtime

Runtime support library for generated code.

**Features:**
- `ChiselFormats`: Base trait providing default json4s formats
- `ChiselSerializer`: Helper for creating discriminated type serializers
- **Shaded dependencies**: json4s relocated to prevent version conflicts

**Artifact:** `dev.cjfravel:chisel-runtime:0.1.0-SNAPSHOT`

### chisel-core

Core functionality for template parsing and code generation.

**Features:**
- Template parsing from JSON
- Scala case class generation
- JSON validator generation
- Support for discriminators, recursion, references

**Artifact:** `dev.cjfravel:chisel-core:0.1.0-SNAPSHOT`

### chisel-maven-plugin

Maven plugin for automatic code generation during build.

**Goal:** `chisel:generate` (bound to `generate-sources` phase)

**Configuration:**
- `templateDirectory` (default: `src/main/resources/templates`)
- All other config in template files themselves

**Artifact:** `dev.cjfravel:chisel-maven-plugin:0.1.0-SNAPSHOT`

### chisel-example

Complete example project demonstrating Chisel usage.

See [chisel-example/README.md](chisel-example/README.md) for details.

## Building the Project

### Build All Modules

```bash
# Use the parent POM (default pom.xml)
mvn clean install
```

This builds modules in order:
1. chisel-runtime (with shading)
2. chisel-core
3. chisel-maven-plugin
4. chisel-example (generates code and runs example)

### Build Individual Modules

```bash
# Build runtime
cd chisel-runtime && mvn clean install

# Build core (requires runtime)
cd chisel-core && mvn clean install

# Build plugin (requires core)
cd chisel-maven-plugin && mvn clean install

# Build example (requires all)
cd chisel-example && mvn clean compile
```

## Using Chisel in Your Project

### 1. Add Dependencies

```xml
<dependencies>
  <dependency>
    <groupId>dev.cjfravel</groupId>
    <artifactId>chisel-runtime</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

### 2. Configure Plugin

```xml
<build>
  <plugins>
    <plugin>
      <groupId>dev.cjfravel</groupId>
      <artifactId>chisel-maven-plugin</artifactId>
      <version>0.1.0-SNAPSHOT</version>
      <executions>
        <execution>
          <goals>
            <goal>generate</goal>
          </goals>
          <configuration>
            <templateDirectory>src/main/resources/templates</templateDirectory>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

### 3. Create Templates

Create `src/main/resources/templates/mymodel.json`:

```json
{
  "basePackage": "com.mycompany.models",
  "outputDir": "target/generated-sources/chisel",
  "useOptionTypes": true,
  "listType": "List",
  "generateJson4s": true,
  "definitions": [
    {
      "name": "MyModel",
      "package": "mymodel",
      "type": "object",
      "fields": {
        "id": {"type": "string"},
        "name": {"type": "string"}
      }
    }
  ]
}
```

### 4. Build and Use

```bash
mvn compile
```

Generated code appears in `target/generated-sources/chisel/`.

```scala
import com.mycompany.models.mymodel.MyModel
import MyModel.formats

val model = MyModel(id = "123", name = "Example")
val json = Extraction.decompose(model)
```

## Key Features

### 1. Self-Contained Templates

All configuration is in the template files:
- Package structure
- Output location
- Type preferences (Option vs nullable, List vs Array)
- JSON serialization

### 2. Multi-Module Support

All paths resolved relative to `${project.basedir}`:
- Works correctly in multi-module projects
- Each module can have its own templates
- No configuration duplication needed

### 3. Shaded Dependencies

Runtime uses shaded json4s:
- Prevents version conflicts
- Isolated from user's dependencies
- `org.json4s` → `dev.cjfravel.chisel.shaded.json4s`

### 4. Build Integration

Automatic code generation:
- Runs during `generate-sources` phase
- IDE integration (IntelliJ, VS Code)
- Generated sources added to compile classpath

### 5. Type Safety

Generated code provides:
- Compile-time type checking
- Option types for optional fields
- Sealed traits for discriminated types
- Automatic JSON ser/des

## Migration from Single Module

If you have existing code using the single-module version:

### Option 1: Keep Single Module

The original `pom.xml` still works. Just:
1. Update to use `chisel-runtime` dependency
2. Update generated code imports to use runtime

### Option 2: Use Multi-Module

1. Build multi-module project
2. Install to local Maven repo
3. Update your project to use maven plugin
4. Create templates in `src/main/resources/templates`
5. Remove manual code generation calls

## Development

### Adding Features

1. Add to `chisel-core` for parsing/generation
2. Add to `chisel-runtime` for runtime support
3. Update `chisel-maven-plugin` if new config needed
4. Add example to `chisel-example`
5. Update documentation

### Testing

```bash
# Test runtime
cd chisel-runtime && mvn test

# Test core
cd chisel-core && mvn test

# Test plugin with example
cd chisel-example && mvn clean compile
```

## Documentation

- [Multi-Module Design](docs/MULTI_MODULE_DESIGN.md) - Architecture details
- [Template Format](docs/TEMPLATE_FORMAT.md) - Template syntax
- [Examples](docs/EXAMPLES.md) - Usage examples
- [Example Project](chisel-example/README.md) - Complete working example

## Advantages of Multi-Module

1. **Separation of Concerns**: Runtime, core, and plugin are independent
2. **Dependency Isolation**: Shaded runtime prevents conflicts
3. **Build Integration**: Automatic generation during Maven lifecycle
4. **Developer Experience**: IDE support, minimal configuration
5. **Production Ready**: Follows industry best practices
6. **Maintainability**: Centralized serialization logic
7. **Flexibility**: Easy to extend and customize
8. **Testing**: Each module tested independently

## Future Enhancements

- Gradle plugin
- SBT plugin
- CLI tool
- IDE plugins (IntelliJ, VS Code)
- Schema validation
- Performance optimizations
- Cross-compilation (Scala 2.13, 3.x)

## License

[Your License Here]

## Contributors

[Your Name/Team]