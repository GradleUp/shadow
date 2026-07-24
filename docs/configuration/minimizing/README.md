# Minimizing

Shadow can automatically remove all JARs and classes of dependencies that are not used by the project, thereby
minimizing the resulting shadowed JAR.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      minimize()
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize()
    }
    ```

A dependency can be excluded from the minimization process, thereby forcing its inclusion the shadow JAR.
This is useful when the dependency analyzer cannot find the usage of a class programmatically, for example if the class
is loaded dynamically via `Class.forName(String)`. Each of the `group`, `name` and `version` fields separated by `:` of
a `dependency` is interpreted as a regular expression.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      minimize {
        exclude(dependency("org.scala-lang:.*:.*"))
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize {
        exclude(dependency('org.scala-lang:.*:.*'))
      }
    }
    ```

> Dependencies scoped as `api` will be automatically excluded from minimization and used as "entry points" on
> minimization.

Similar to [`ShadowJar.dependencies`][ShadowJar.dependencies], projects can also be excluded.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      minimize {
        exclude(project(":api"))
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      minimize {
        exclude(project(":api"))
      }
    }
    ```

> When excluding a `project`, all dependencies of the excluded `project` are automatically excluded from 
> minimization as well.

## Post-processing with R8

Shadow can also run [R8](https://r8.googlesource.com/r8) over the final shadowed JAR. This is useful when you want
whole-program shrinking independently of the default dependency analyzer. R8 runs after Shadow has merged,
transformed, relocated, and optionally minimized the JAR, so service descriptors in `META-INF/services` are used to
keep service providers.

The default R8 configuration only shrinks unused code. It disables name minification and optimization.
Shadow also extracts R8 rules published in dependency JARs, for example under `META-INF/proguard`.

The `minimize` and `r8` blocks are independent and can be enabled separately or together. Dependency exclusions in
`minimize` only configure Shadow's dependency analyzer; use ProGuard rules to keep classes during R8 post-processing.

=== "Kotlin"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      r8 {
        // Optional extra configuration
        proguardRules.add("-keep class com.example.ReflectiveApi { *; }")
        proguardRuleFiles.from(layout.projectDirectory.file("r8-rules.pro"))
      }
    }
    ```

=== "Groovy"

    ```groovy
    repositories {
      google()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      r8 {
        // Optional extra configuration
        proguardRules.add('-keep class com.example.ReflectiveApi { *; }')
        proguardRuleFiles.from(layout.projectDirectory.file('r8-rules.pro'))
      }
    }
    ```

Shadow resolves R8 from the `shadowR8` configuration. The default dependency is `com.android.tools:r8`, which is
published by Google Maven rather than Maven Central. Add `google()` to your repositories or override the dependency:

=== "Kotlin"

    ```kotlin
    dependencies {
      shadowR8("com.android.tools:r8:9.1.31")
    }
    ```

=== "Groovy"

    ```groovy
    dependencies {
      shadowR8 'com.android.tools:r8:9.1.31'
    }
    ```

Advanced R8 command line arguments can be added with `args`. Replacing the default `args` value removes Shadow's
default command line arguments, so prefer the helper functions for common obfuscation and optimization toggles. These
helpers are independent and can be used together.

For example, to downgrade R8 warnings to info:

=== "Kotlin"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      r8 {
        args.addAll(listOf("--map-diagnostics", "warning", "info"))
      }
    }
    ```

=== "Groovy"

    ```groovy
    repositories {
      google()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      r8 {
        args.addAll(['--map-diagnostics', 'warning', 'info'])
      }
    }
    ```

To enable name obfuscation:

=== "Kotlin"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      r8 {
        enableObfuscation()
      }
    }
    ```

=== "Groovy"

    ```groovy
    repositories {
      google()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      r8 {
        enableObfuscation()
      }
    }
    ```

To enable optimization:

=== "Kotlin"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      r8 {
        enableOptimization()
      }
    }
    ```

=== "Groovy"

    ```groovy
    repositories {
      google()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      r8 {
        enableOptimization()
      }
    }
    ```

To enable both:

=== "Kotlin"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      r8 {
        enableObfuscation()
        enableOptimization()
      }
    }
    ```

=== "Groovy"

    ```groovy
    repositories {
      google()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      r8 {
        enableObfuscation()
        enableOptimization()
      }
    }
    ```

[ShadowJar.dependencies]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/dependencies.html
