# Relocating Packages

Shadow is capable of scanning a project's classes and relocating specific dependencies to a new location. This is often
required when one of the dependencies is susceptible to breaking changes in versions or to classpath pollution in a
downstream project.

> [!TIP]
> Google's Guava and the ASM library are typical cases where package relocation can come in handy.

Shadow uses the ASM library to modify class byte code to replace the package name and any import statements for a class.
Any non-class files that are stored within a package structure are also relocated to the new location.

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.shadowJar {
      relocate("junit.framework", "shadow.junit")
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      relocate 'junit.framework', 'shadow.junit'
    }
    ```

The code snippet will rewrite the location for any class in the `junit.framework` to be `shadow.junit`. For example, the
class `junit.framework.TestCase` becomes `shadow.junit.TestCase`. In the resulting JAR, the class file is relocated from
`junit/framework/TestCase.class` to `shadow/junit/TestCase.class`.

> [!WARNING]
> **Scope of Relocation**
>
> Relocation operates at a package level.
> It is not necessary to specify any patterns for matching, it will operate simply on the prefix provided.
>
> Relocation will be applied globally to all instances of the matched prefix.
> That is, it does **not** scope to _only_ the dependencies being shadowed.
> Be specific as possible when configuring relocation as to avoid unintended relocations.

## Filtering Relocation

Specific classes or files can be `included`/`excluded` from the relocation operation if necessary. Use
[Ant Path Matcher][ant-path-matcher] syntax to specify matching path for your files and directories.

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.shadowJar {
      relocate("junit.textui", "a") {
        exclude("junit.textui.TestRunner")
      }
      relocate("junit.framework", "b") {
        include("junit.framework.Test*")
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      relocate('junit.textui', 'a') {
        exclude 'junit.textui.TestRunner'
      }
      relocate('junit.framework', 'b') {
        include 'junit.framework.Test*'
      }
    }
    ```

For a more advanced path matching you might want to use [Regular Expressions][regular-expressions] instead. Wrap the
expression in `%regex[]` before passing it to `include`/`exclude`.

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.shadowJar {
      relocate("org.foo", "a") {
        include("%regex[org/foo/.*Factory[0-9].*]")
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      relocate('org.foo', 'a') {
        include '%regex[org/foo/.*Factory[0-9].*]'
      }
    }
    ```

It may be desirable to relocate all packages in a Shadow JAR except for a select few. This can be accomplished by
specifying a relocation with an empty string `''` as the pattern to match on all packages. An `exclude` filter can then
be used to prevent relocation of specific packages.

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.shadowJar {
      relocate("", "my/shadow/prefix") {
        exclude("META-INF/**")
        // Exclude all JUnit packages.
        exclude("junit/**")
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      relocate('', 'my/shadow/prefix') {
        exclude 'META-INF/**'
        // Exclude all JUnit packages.
        exclude 'junit/**'
      }
    }
    ```

## Skipping Relocation for String Constants

If there is a class like:

```java
package foo;

public class Bar {
  public static void main(String[] args) {
    System.out.println("foo.Bar");
  }
}
```

in your project, and you configure the relocation like:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.shadowJar {
      relocate("foo", "my.foo")
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      relocate 'foo', 'my.foo'
    }
    ```

the string constant `"foo.Bar"` will be relocated to `"my.foo.Bar"` by default. This may not be what you want, you can
skip relocating string constants in the classes like:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.shadowJar {
      relocate("foo", "my.foo") {
        // Optionally, defaults to `false`.
        skipStringConstants = true
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      relocate('foo', 'my.foo') {
        // Optionally, defaults to `false`.
        skipStringConstants = true
      }
    }
    ```

## Automatically Relocating Dependencies

Shadow is shipped with a task that can be used to automatically configure all packages from all dependencies to be
relocated. This feature was formally shipped into a 2nd plugin (`com.github.johnrengelman.plugin-shadow`) but has been
removed for clarity reasons in version 4.0.0.

To configure automatic dependency relocation, set `enableAutoRelocation = true` and optionally specify a custom
`relocationPrefix` to override the default value of `"shadow"`.

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.shadowJar {
      enableAutoRelocation = true
      relocationPrefix = "myapp"
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      enableAutoRelocation = true
      relocationPrefix = "myapp"
    }
    ```

> [!WARNING]
> **Performance & Transitive Dependencies**
>
> Configuring package auto relocation can add significant time to the shadow process as it will process all
> dependencies in the configurations declared to be shadowed. By default, this is the `runtime` or `runtimeClasspath`
> configurations.
>
> Be mindful that some Gradle plugins will automatically add dependencies to your class path. You may need to remove
> these dependencies if you do not intend to shadow them into your library.

## Relocating Kotlin Standard Library

It is not recommended to relocate Kotlin Standard Library if you are using [Kotlin Metadata][kotlin-metadata] or
[Kotlin Reflection][kotlin-reflection] in the project, because they are tightly coupled with Kotlin compiler and
runtime. See more details and discussion in [#1622][#1622].

## Relocating Project Resources Only

If you want to relocate the resources of the project only and exclude all dependencies (related to a normal JAR but with
relocating), you can try out the trick like:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.shadowJar {
      // Empty mergedDependencies will exclude all dependencies.
      mergedDependencies.setFrom()
      relocate("com.example", "shadow.com.example")
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      // Empty mergedDependencies will exclude all dependencies.
      mergedDependencies.setFrom()
      relocate 'com.example', 'shadow.com.example'
    }
    ```

This is useful in some cases, as mentioned in [#759]. See
[Configuring Shadowed Dependencies][configuring-shadowed-dependencies] for more information about `mergedDependencies`.

## Relocating with R8

As an alternative to Shadow's built-in `relocate` configuration (which uses ASM to rename package prefixes during JAR
merging), you can use [R8][r8-minimizing] to handle package relocation (also referred to as *repackaging*).

R8 performs whole-program analysis during its minimization pass to safely relocate classes while respecting Java access
visibility constraints (such as package-private and `protected` members). For more details on R8 rules, see
the [Global options for additional optimization][android-r8-global-options] and ProGuard manual for
[-repackageclasses][repackageclasses], [-allowaccessmodification][allowaccessmodification]
and [-keeppackagenames][keeppackagenames].

### Configuring R8 Repackaging

To use R8 for package relocation, enable R8 under `minimize` and provide ProGuard repackaging directives via
`proguardRules` or an external rule file:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    repositories {
      google()
    }

    tasks.shadowJar {
      minimize {
        r8 {
          proguardRules.addAll(
            // Repackage all relocatable classes into a single destination package
            "-repackageclasses 'shadow.repackaged'",
            // Optional: widen access to public to allow R8 to relocate more classes
            "-allowaccessmodification",
            // Optional: preserve specific package names if needed
            "-keeppackagenames com.example.keep.**",
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
          proguardRules.addAll(
            // Repackage all relocatable classes into a single destination package
            "-repackageclasses 'shadow.repackaged'",
            // Optional: widen access to public to allow R8 to relocate more classes
            "-allowaccessmodification",
            // Optional: preserve specific package names if needed
            "-keeppackagenames com.example.keep.**",
          )
        }
      }
    }
    ```

### Comparison: Shadow `relocate` vs. R8 Repackaging

| Feature                        | Shadow `relocate` (`SimpleRelocator`)                                     | R8 Repackaging (`-repackageclasses`)                |
|:-------------------------------|:--------------------------------------------------------------------------|:----------------------------------------------------|
| **Execution Stage**            | During JAR merging (ASM bytecode transformation)                          | Post-merge whole-program optimization               |
| **Relocation Scope**           | Explicit per-prefix or per-class pattern matching                         | Whole-program automatic relocation                  |
| **Visibility Handling**        | Direct string/type renaming (no visibility check)                         | Analyzes package-private & protected constraints    |
| **Embedded R8/ProGuard Rules** | Requires [transformer][ProGuardFilesResourceTransformer] to rewrite rules | Handled natively without extra transformers         |
| **Shrinking / Obfuscation**    | Relocation only                                                           | Combined with shrinking (optional name obfuscation) |


[#1622]: https://github.com/GradleUp/shadow/issues/1622
[#759]: https://github.com/GradleUp/shadow/issues/759
[kotlin-metadata]: https://kotlinlang.org/docs/metadata-jvm.html
[kotlin-reflection]: https://kotlinlang.org/docs/reflection.html
[r8-minimizing]: ../minimizing/README.md#minimizing-with-r8
[android-r8-global-options]: https://developer.android.com/topic/performance/app-optimization/global-options#global-options
[repackageclasses]: https://www.guardsquare.com/manual/configuration/usage#repackageclasses
[allowaccessmodification]: https://www.guardsquare.com/manual/configuration/usage#allowaccessmodification
[keeppackagenames]: https://www.guardsquare.com/manual/configuration/usage#keeppackagenames
[ant-path-matcher]: https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html
[regular-expressions]: https://regexr.com/
[configuring-shadowed-dependencies]: ../dependencies/README.md
[ProGuardFilesResourceTransformer]: ../merging/README.md#merging-r8proguard-rule-files
