# Scala JSON Library Comparison for Chisel

## Overview

Comparison of popular Scala JSON libraries for use in the Chisel project (Scala 2.12).

---

## 1. Circe

### Pros
- **Type-safe**: Compile-time guarantees with automatic derivation
- **Functional**: Pure functional approach with cats integration
- **Performance**: Fast parsing and encoding
- **Modern API**: Clean, intuitive syntax
- **Active maintenance**: Well-maintained with regular updates
- **Excellent error messages**: Detailed decoding failures with paths
- **Automatic derivation**: Can automatically generate codecs for case classes
- **Streaming support**: Can handle large JSON streams efficiently

### Cons
- **Learning curve**: Requires understanding of cats/functional concepts
- **Compile times**: Can slow down compilation with heavy macro usage
- **Binary size**: Larger dependency footprint
- **Scala 2.12 consideration**: Works but best experience is on 2.13+

### Maven Dependency
```xml
<dependency>
  <groupId>io.circe</groupId>
  <artifactId>circe-core_2.12</artifactId>
  <version>0.14.6</version>
</dependency>
<dependency>
  <groupId>io.circe</groupId>
  <artifactId>circe-parser_2.12</artifactId>
  <version>0.14.6</version>
</dependency>
```

### Code Example
```scala
import io.circe._, io.circe.parser._

val json = """{"name": "Alice", "age": 30}"""
val result = parse(json).flatMap(_.as[User])
```

**Recommendation Level: ⭐⭐⭐⭐⭐ (Excellent choice)**

---

## 2. Play JSON

### Pros
- **Battle-tested**: Used in production by many large applications
- **Simple API**: Easy to learn and use
- **Good documentation**: Extensive docs and examples
- **Macros for case classes**: Automatic Format derivation
- **Flexible**: Easy to define custom readers/writers
- **Integration**: Works well with Play Framework ecosystem
- **Stable**: Mature library with predictable behavior

### Cons
- **Mutable API**: Some operations use mutable structures
- **Play dependency**: Brings in Play Framework dependencies even if not using Play
- **Less functional**: Not as pure-functional as circe
- **Error handling**: Error messages can be cryptic
- **Performance**: Slightly slower than circe in benchmarks

### Maven Dependency
```xml
<dependency>
  <groupId>com.typesafe.play</groupId>
  <artifactId>play-json_2.12</artifactId>
  <version>2.9.4</version>
</dependency>
```

### Code Example
```scala
import play.api.libs.json._

val json = """{"name": "Alice", "age": 30}"""
val result = Json.parse(json).validate[User]
```

**Recommendation Level: ⭐⭐⭐⭐ (Very good choice)**

---

## 3. Json4s

### Pros
- **Multiple backends**: Supports Jackson or native parser
- **Simple API**: Easy to get started
- **Flexible**: Multiple DSLs for different use cases
- **Extraction**: Pattern matching support for JSON
- **Lightweight native**: Native parser has minimal dependencies
- **Mature**: Long history and proven in production

### Cons
- **Reflection-based**: Uses runtime reflection, slower than compile-time
- **Type safety**: Less type-safe than circe or play-json
- **Error messages**: Poor error messages for invalid JSON
- **Maintenance**: Less active development recently
- **API inconsistency**: Multiple DSLs can be confusing
- **Thread safety**: Some operations require careful handling in concurrent code

### Maven Dependency
```xml
<dependency>
  <groupId>org.json4s</groupId>
  <artifactId>json4s-native_2.12</artifactId>
  <version>4.0.7</version>
</dependency>
```

### Code Example
```scala
import org.json4s._
import org.json4s.native.JsonMethods._

val json = """{"name": "Alice", "age": 30}"""
val result = parse(json).extract[User]
```

**Recommendation Level: ⭐⭐⭐ (Good choice)**

---

## 4. Spray JSON

### Pros
- **Lightweight**: Minimal dependencies
- **Simple**: Straightforward API
- **Fast**: Good performance
- **Stable**: Mature and stable API
- **Type-safe**: Compile-time type safety with JsonFormat

### Cons
- **Maintenance**: No longer actively maintained
- **Boilerplate**: More manual JsonFormat definitions needed
- **Limited features**: Lacks advanced features of newer libraries
- **Error messages**: Basic error reporting
- **No automatic derivation**: Must manually write formats

### Maven Dependency
```xml
<dependency>
  <groupId>io.spray</groupId>
  <artifactId>spray-json_2.12</artifactId>
  <version>1.3.6</version>
</dependency>
```

**Recommendation Level: ⭐⭐ (Use only if you need lightweight)**

---

## 5. uPickle

### Pros
- **Simplicity**: Very easy to use
- **Fast**: Excellent performance
- **Lightweight**: Small footprint
- **Macros**: Automatic derivation with minimal boilerplate
- **MessagePack support**: Can serialize to binary format
- **Works everywhere**: Scala.js compatible

### Cons
- **Less functional**: Not FP-oriented
- **Error messages**: Basic error reporting
- **Community**: Smaller community than circe/play-json
- **Documentation**: Less comprehensive docs
- **Flexibility**: Less flexible for custom serialization

### Maven Dependency
```xml
<dependency>
  <groupId>com.lihaoyi</groupId>
  <artifactId>upickle_2.12</artifactId>
  <version>3.1.4</version>
</dependency>
```

**Recommendation Level: ⭐⭐⭐ (Good for simple use cases)**

---

## Recommendation for Chisel

### Best Choice: **Circe** ⭐⭐⭐⭐⭐

**Rationale:**
1. **Error handling**: Excellent for validation - provides detailed error paths crucial for Chisel's validation messages
2. **Type safety**: Strong compile-time guarantees help prevent bugs in code generation
3. **Active maintenance**: Regular updates and bug fixes
4. **Performance**: Fast parsing important for validation operations
5. **Functional design**: Aligns well with Scala best practices
6. **Cursor API**: Makes it easy to navigate JSON structure for template parsing

**Alternative: Play JSON** ⭐⭐⭐⭐

If you prefer:
- Simpler, less functional approach
- Don't mind the Play Framework dependency
- Want more familiar API for developers coming from other JVM backgrounds

---

## Summary Table

| Library    | Type Safety | Performance | Error Messages | Maintenance | Learning Curve | Chisel Fit |
|------------|-------------|-------------|----------------|-------------|----------------|------------|
| Circe      | ⭐⭐⭐⭐⭐    | ⭐⭐⭐⭐⭐     | ⭐⭐⭐⭐⭐        | ⭐⭐⭐⭐⭐     | ⭐⭐⭐          | ⭐⭐⭐⭐⭐    |
| Play JSON  | ⭐⭐⭐⭐     | ⭐⭐⭐⭐      | ⭐⭐⭐          | ⭐⭐⭐⭐      | ⭐⭐⭐⭐         | ⭐⭐⭐⭐     |
| Json4s     | ⭐⭐⭐       | ⭐⭐⭐       | ⭐⭐            | ⭐⭐⭐        | ⭐⭐⭐⭐         | ⭐⭐⭐       |
| Spray JSON | ⭐⭐⭐⭐     | ⭐⭐⭐⭐      | ⭐⭐            | ⭐⭐          | ⭐⭐⭐          | ⭐⭐         |
| uPickle    | ⭐⭐⭐       | ⭐⭐⭐⭐⭐     | ⭐⭐            | ⭐⭐⭐        | ⭐⭐⭐⭐⭐       | ⭐⭐⭐       |
