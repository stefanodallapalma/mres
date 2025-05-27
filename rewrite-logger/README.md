**OpenRewrite JavaTemplate Type Resolution Issue - Method Chain Transformation**

I'm working on migrating from an old logger API to a newer one using OpenRewrite.

**Context:**
Transforming `logger.with("field").info("message")` → `logger.atInfo().with("field").log("message")` (same class, different method order)

**Problem:**
My JavaTemplate fails with type resolution errors:
```java
JavaTemplate.builder("#{any(com.example.util.OldLogger)}.at#{}().with(#{}).log(#{})")
    .contextSensitive()
    .imports("com.example.util.OldLogger")
    .build();

// Applied with: logger, capitalizedLogLevel, withMethodArg, logMethodArg
// ❌ Fails: "LST contains missing or invalid type information"
```

**Weird Behavior:**
If I swap the last two arguments (`logMethodArg, withMethodArg`), it generates code without errors - but the arguments are in wrong positions! 🤯

**Current Workaround:**
Adding `.dependsOn()` with method stubs to `javaParser()` works, but feels brittle:
```java
.javaParser(JavaParser.fromJavaVersion().dependsOn("package com.example.util; public class OldLogger { ... }"))
```

**Questions:**
1. Why does argument order affect type resolution in templates?
2. Is there a cleaner way to help OpenRewrite resolve fluent API method chains without hardcoding type stubs?
3. Am I missing something fundamental about how JavaTemplate resolves chained method calls?

The module contains a minimal reproducible example. Any insights appreciated! 🙏
