# Minimizing

Shadow can automatically remove unused JARs, classes, and code of dependencies, thereby minimizing the resulting
shadowed JAR.

## Overview & Choosing a Minimization Tool

Shadow provides two minimization backends:

1. **`DEPENDENCY_ANALYZER` (Default)**: A lightweight, class-level analyzer powered by [jdependency][jdependency] based
   on bytecode constant pool references.
2. **`R8` (Recommended for advanced shrinking)**: A whole-program optimizer and shrinker powered by [R8][R8] that
   operates at the method/field level.

| Feature / Capability             | `DEPENDENCY_ANALYZER` (Default)                         | `R8` (Advanced)                                                          |
|:---------------------------------|:--------------------------------------------------------|:-------------------------------------------------------------------------|
| **Analysis Granularity**         | **Class-level** (keeps or drops entire class files)     | **Method/Field-level** (fine-grained Tree Shaking)                       |
| **Reflection & Dynamic Loading** | Manual exclusion required (`minimize { exclude(...) }`) | Supported via ProGuard keep rules and embedded consumer rules            |
| **Java SPI (`ServiceLoader`)**   | ❌ **Not detected automatically**                       | **Preserved by default rules** (parses `META-INF/services`)              |
| **Bytecode Optimizations**       | None (simple file filtering)                            | Dead code elimination, method inlining, constant folding, etc.           |
| **Build Overhead**               | Minimal / fast                                          | Requires running R8 compiler                                             |
| **Best For**                     | Simple projects with direct bytecode references         | Modern applications with SPI, reflection, or requiring smallest JAR size |

---

## Minimizing with the Dependency Analyzer

By default, calling `minimize()` enables Shadow's built-in dependency analyzer:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.shadowJar {
      minimize()
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize()
    }
    ```

A dependency can be excluded from the minimization process, thereby forcing its inclusion in the shadow JAR. This is
useful when the dependency analyzer cannot find the usage of a class programmatically, for example if the class is
loaded dynamically via `Class.forName(String)` or loaded via Java SPI (`ServiceLoader`). Each of the `group`, `name` and
`version` fields separated by `:` of a `dependency` is interpreted as a regular expression.

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.shadowJar {
      minimize {
        exclude(dependency("org.apache.logging.log4j:.*:.*"))
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize {
        exclude(dependency('org.apache.logging.log4j:.*:.*'))
      }
    }
    ```

> [!NOTE]
> Dependencies scoped as `api` will be automatically excluded from minimization and used as "entry points" on
> minimization.

Similar to [`ShadowJar.dependencies`][ShadowJar.dependencies], projects can also be excluded.

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.shadowJar {
      minimize {
        exclude(project(":api"))
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize {
        exclude(project(":api"))
      }
    }
    ```

> [!NOTE]
> When excluding a `project`, all dependencies of the excluded `project` are automatically excluded from
> minimization as well.

> [!TIP]
> **Difference between `dependencies` filter and `minimize` filter**
>
> Both `dependencies { ... }` and `minimize { ... }` implement [`DependencyFilter`][DependencyFilter], sharing the
> same `include` / `exclude` syntax, but they control completely different operations:
>
> - **`shadowJar.dependencies` (Packaging filter)**:
>   - `exclude(...)`: Excludes matching dependencies from being bundled into the shadow JAR at all.
>   - `include(...)`: Bundles *only* matching dependencies into the shadow JAR, discarding all other dependencies.
> - **`shadowJar.minimize` (Shrinking filter)**:
>   - `exclude(...)`: Excludes matching dependencies from *minimization / code shrinking*. The dependencies are still bundled into the shadow JAR, and all of their classes and methods are fully preserved without being stripped.
>   - `include(...)`: Applies code shrinking *only* to matching dependencies. All other dependencies are bundled and fully preserved.

## Minimizing with R8

Shadow can also run [R8][R8] over the final shadowed JAR. This is useful when you want whole-program shrinking instead
of the default dependency analyzer. R8 runs after Shadow has merged, transformed, and relocated the JAR, so service
descriptors in `META-INF/services` are used to keep service providers.

The default R8 configuration only shrinks unused code. It disables name minification and optimization. R8 also applies
rules published in dependency JARs, for example under `META-INF/proguard`.

> [!NOTE]
> **Relocating Embedded ProGuard Rules**
>
> If you relocate classes using Shadow's `relocate` configuration from a dependency that publishes embedded R8/ProGuard
> rules (for example under `META-INF/proguard`), those rules are not rewritten automatically. Add
> [`ProGuardFilesResourceTransformer`][ProGuardFilesResourceTransformer] so class names and package patterns inside
> embedded rules are updated to match your relocations.
>
> Alternatively, if you use [R8 Repackaging][r8-repackaging] (e.g. `-repackageclasses`), R8 applies embedded rules
> natively without needing rule rewriting.

> [!NOTE]
> **Shadowed Sources JAR and R8**
>
> R8 operates directly on compiled JVM bytecode rather than source code. When minimizing with R8
(`minimize { r8 { ... } }`),
> Shadow cannot determine which source files correspond to classes removed by R8. Therefore, the shadowed sources JAR
will
> contain all relocated source files without responding to R8 shrinking results.
>
> If you need unused source files to be filtered out of the shadowed sources JAR, use the default dependency analyzer
> minimization (`minimize()`) instead.

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      minimize {
        r8 {
          // Optional extra configuration
          proguardRules.add("-keep class com.example.ReflectiveApi { *; }")
          proguardRuleFiles.from(layout.projectDirectory.file("r8-rules.pro"))
          configurationFile = layout.buildDirectory.file("r8/configuration.txt")
        }
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    repositories {
      google()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize {
        r8 {
          // Optional extra configuration
          proguardRules.add('-keep class com.example.ReflectiveApi { *; }')
          proguardRuleFiles.from(layout.projectDirectory.file('r8-rules.pro'))
          configurationFile = layout.buildDirectory.file('r8/configuration.txt')
        }
      }
    }
    ```

R8 writes the collective ProGuard configuration it used to `build/shadowJar/r8/configuration.txt` by default. Shadow
passes this location to R8 with `--pg-conf-output`. Set `configurationFile` to retain it elsewhere.

By default, R8 removes directory entries from the resulting JAR to reduce archive size. If your application or
dependencies require directory entries at runtime (e.g. for classpath resource discovery),
add [`-keepdirectories`][-keepdirectories] to `proguardRules`.

R8 also supports ProGuard reporting options such as

- [`-printmapping`][-printmapping]
- [`-printseeds`][-printseeds]
- [`-printusage`][-printusage]

Add them as `proguardRules` when you want to retain name mappings, matched keep rules, or removed code:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      minimize {
        r8 {
          enableObfuscation()
          configurationFile = layout.buildDirectory.file("r8/configuration.txt")
          proguardRules.addAll(
            "-printmapping reports/mapping.txt",
            "-printseeds reports/seeds.txt",
            "-printusage reports/usage.txt",
          )
        }
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    repositories {
      google()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize {
        r8 {
          enableObfuscation()
          configurationFile = layout.buildDirectory.file('r8/configuration.txt')
          proguardRules.addAll(
            '-printmapping reports/mapping.txt',
            '-printseeds reports/seeds.txt',
            '-printusage reports/usage.txt',
          )
        }
      }
    }
    ```

Relative report paths are resolved from the directory containing `configurationFile`. The example above writes the
reports under `build/r8/reports`. Use absolute paths if the reports must be written independently of the configuration
file location. This behavior follows [R8's configuration parser][ProguardConfigurationParser]. `-printmapping` only
contains renamed items, so call `enableObfuscation()` when you need a useful mapping.

These reporting options belong in the build's R8 configuration, not in rules published inside a dependency JAR.
Android's [library optimization guidance][library-optimization-guidance] lists them among the global options that
library authors should not publish as consumer keep rules.

When your classes reference types that are available on the compile classpath but not bundled into the shadowed JAR
(such as `compileOnly` dependencies or `gradleApi()`), supply them to R8 via `classpath` so R8 can analyze the complete
class hierarchy without bundling those dependencies into the output archive:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    repositories {
      google()
    }

    val compileClasspath = configurations.compileClasspath

    tasks.shadowJar {
      minimize {
        r8 {
          classpath.from(compileClasspath)
        }
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    repositories {
      google()
    }

    def compileClasspath = configurations.compileClasspath

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize {
        r8 {
          classpath.from(compileClasspath)
        }
      }
    }
    ```

Shadow resolves R8 from the `shadowR8` configuration. The default dependency is `com.android.tools:r8`, which is
published by Google Maven rather than Maven Central. Add `google()` to your repositories or override the dependency:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    dependencies {
      shadowR8("com.android.tools:r8:9.1.31")
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    dependencies {
      shadowR8 'com.android.tools:r8:9.1.31'
    }
    ```

Advanced R8 command line arguments can be added with `args`. Replacing the default `args` value removes Shadow's default
command line arguments, so prefer the helper functions for common obfuscation and optimization toggles. These helpers
are independent and can be used together.

For example, to downgrade R8 warnings to info:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      minimize {
        r8 {
          args.addAll(listOf("--map-diagnostics", "warning", "info"))
        }
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    repositories {
      google()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize {
        r8 {
          args.addAll(['--map-diagnostics', 'warning', 'info'])
        }
      }
    }
    ```

To enable name obfuscation:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      minimize {
        r8 {
          enableObfuscation()
        }
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    repositories {
      google()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize {
        r8 {
          enableObfuscation()
        }
      }
    }
    ```

To enable optimization:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      minimize {
        r8 {
          enableOptimization()
        }
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    repositories {
      google()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize {
        r8 {
          enableOptimization()
        }
      }
    }
    ```

To enable both:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      minimize {
        r8 {
          enableObfuscation()
          enableOptimization()
        }
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    repositories {
      google()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize {
        r8 {
          enableObfuscation()
          enableOptimization()
        }
      }
    }
    ```

### Filtering Dependencies in R8 Minimization

The `include` and `exclude` filters configured on `minimize { ... }` apply to R8 minimization when default rules are
enabled (the default):

- **`exclude(...)` (Keep specific dependencies)**: Excludes matching dependencies from R8 shrinking. Shadow
  automatically generates `-keep` rules for all classes in the excluded dependencies so they are fully preserved.
- **`include(...)` (Shrink only specific dependencies)**: Applies R8 shrinking *only* to matching dependencies. All
  other dependencies are automatically kept in full.

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      minimize {
        // Exclude specific dependencies from R8 shrinking (kept completely)
        exclude(project(":api"))

        // Or shrink only specific dependencies (all others are kept completely)
        include(dependency("org.apache.logging.log4j:.*:.*"))

        r8 {
          // Optional extra configuration
        }
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    repositories {
      google()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize {
        // Exclude specific dependencies from R8 shrinking (kept completely)
        exclude(project(':api'))

        // Or shrink only specific dependencies (all others are kept completely)
        include(dependency('org.apache.logging.log4j:.*:.*'))

        r8 {
          // Optional extra configuration
        }
      }
    }
    ```

### Customizing Rules Without Defaults

By default, Shadow generates fallback keep rules for project classes, excluded dependencies, and service descriptors,
and generates `-dontoptimize` to disable optimization unless explicitly enabled.

To take full control over Shadow-generated rules and maximize R8 optimizations (such as shrinking unused project classes
or methods and running optimizations), disable `useDefaultRules`:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      minimize {
        r8 {
          useDefaultRules = false
          proguardRules.add("-keep class com.example.Main { public static void main(java.lang.String[]); }")
        }
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    repositories {
      google()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize {
        r8 {
          useDefaultRules = false
          proguardRules.add('-keep class com.example.Main { public static void main(java.lang.String[]); }')
        }
      }
    }
    ```

> [!NOTE]
> Setting `useDefaultRules = false` disables Shadow's auto-generated rules, including `-keep` rules generated from
> `minimize` `include` / `exclude` filters. This does not disable consumer rules embedded in dependency JARs
> (e.g. under `META-INF/proguard`). Furthermore, name obfuscation remains disabled by default unless
> `enableObfuscation()` is called or `args` is customized.



[-printmapping]: https://www.guardsquare.com/manual/configuration/usage#printmapping
[-printseeds]: https://www.guardsquare.com/manual/configuration/usage#printseeds
[-printusage]: https://www.guardsquare.com/manual/configuration/usage#printusage
[-keepdirectories]: https://www.guardsquare.com/manual/configuration/usage#keepdirectories
[library-optimization-guidance]: https://developer.android.com/topic/performance/app-optimization/library-optimization
[R8]: https://r8.googlesource.com/r8
[jdependency]: https://github.com/tcurdt/jdependency
[ProguardConfigurationParser]: https://r8.googlesource.com/r8/+/refs/tags/9.1.31/src/main/java/com/android/tools/r8/shaking/ProguardConfigurationParser.java
[ShadowJar.dependencies]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/dependencies.html
[DependencyFilter]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-dependency-filter/index.html
[ProGuardFilesResourceTransformer]: ../merging/README.md#merging-r8proguard-rule-files
[r8-repackaging]: ../relocation/README.md#relocating-with-r8
