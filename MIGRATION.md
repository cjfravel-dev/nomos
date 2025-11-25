# Migration to Multi-Module Structure

## Current State

The project currently has a single-module structure with `pom.xml` at the root.

## Migration Strategy

### Option 1: Clean Migration (Recommended for New Deployments)

1. **Backup current pom.xml**
   ```bash
   mv pom.xml pom-single-module-backup.xml
   ```

2. **Rename parent POM**
   ```bash
   mv pom-parent.xml pom.xml
   ```

3. **Build the multi-module project**
   ```bash
   mvn clean install
   ```

4. **The new structure**:
   ```
   nomos/
   ├── pom.xml                    # Now the parent/aggregator POM
   ├── src/                       # Original source (used by nomos-core)
   ├── nomos-runtime/
   ├── nomos-core/              # References ../src
   ├── nomos-maven-plugin/
   └── nomos-example/
   ```

### Option 2: Gradual Migration (For Active Development)

Keep both POMs temporarily:

1. **Build multi-module**:
   ```bash
   mvn -f pom-parent.xml clean install
   ```

2. **Build single-module** (existing workflow):
   ```bash
   mvn clean install
   ```

3. **Once validated, switch**:
   ```bash
   mv pom.xml pom-single-module.xml
   mv pom-parent.xml pom.xml
   ```

## Why Replace pom.xml?

1. **Standard Maven Convention**: Parent POM should be `pom.xml` at root
2. **Default Build**: `mvn install` works without `-f` flag
3. **IDE Support**: IntelliJ/Eclipse auto-detect multi-module from root pom.xml
4. **Clean Structure**: Follows Maven best practices

## Post-Migration

### Build Commands

```bash
# Build everything
mvn clean install

# Build specific module
mvn -pl nomos-runtime clean install

# Build runtime + core
mvn -pl nomos-runtime,nomos-core clean install

# Build example (includes generation)
mvn -pl nomos-example clean compile
```

### For Users

Projects using Chisel just need:

```xml
<dependency>
  <groupId>dev.cjfravel</groupId>
  <artifactId>nomos-runtime</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>

<plugin>
  <groupId>dev.cjfravel</groupId>
  <artifactId>nomos-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  ...
</plugin>
```

## Recommendation

**Replace `pom.xml` with `pom-parent.xml` now** since:

1. The multi-module structure is complete
2. All modules are implemented
3. The example project demonstrates usage
4. It's the standard Maven convention

Execute:
```bash
# Keep backup for reference
cp pom.xml pom-single-module-backup.xml

# Activate multi-module structure
mv pom-parent.xml pom.xml

# Build and verify
mvn clean install
```

## Rollback (if needed)

```bash
# Restore original
mv pom.xml pom-parent.xml
mv pom-single-module-backup.xml pom.xml
```

## Benefits of Multi-Module at Root

- ✅ Standard Maven multi-module structure
- ✅ `mvn install` works without flags
- ✅ IDE auto-imports correctly
- ✅ Cleaner CI/CD integration
- ✅ Follows Apache Maven conventions
- ✅ Better reactor build performance