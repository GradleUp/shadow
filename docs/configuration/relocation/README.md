# Relocating Packages

Shadow is capable of scanning a project's classes and relocating specific dependencies to a new location.
This is often required when one of the dependencies is susceptible to breaking changes in versions or
to classpath pollution in a downstream project.

> Google's Guava and the ASM library are typical cases where package relocation can come in handy.

Shadow uses the ASM library to modify class byte code to replace the package name and any import
statements for a class.
Any non-class files that are stored within a package structure are also relocated to the new location.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      relocate("junit.framework", "shadow.junit")
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      relocate 'junit.framework', 'shadow.junit'
    }
    ```

The code snippet will rewrite the location for any class in the `junit.framework` to be `shadow.junit`.
For example, the class `junit.framework.TestCase` becomes `shadow.junit.TestCase`.
In the resulting JAR, the class file is relocated from `junit/framework/TestCase.class` to
`shadow/junit/TestCase.class`.

> Relocation operates at a package level.
It is not necessary to specify any patterns for matching, it will operate simply on the prefix
provided.

> Relocation will be applied globally to all instances of the matched prefix.
That is, it does **not** scope to _only_ the dependencies being shadowed.
Be specific as possible when configuring relocation as to avoid unintended relocations.

## Filtering Relocation

Specific classes or files can be `included`/`excluded` from the relocation operation if necessary. Use
[Ant Path Matcher](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html) 
syntax to specify matching path for your files and directories.

=== "Kotlin"

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

=== "Groovy"

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

For a more advanced path matching you might want to use [Regular Expressions](https://regexr.com/) instead. Wrap the expression in `%regex[]` before
passing it to `include`/`exclude`.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      relocate("org.foo", "a") {
        include("%regex[org/foo/.*Factory[0-9].*]")
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      relocate('org.foo', 'a') {
        include '%regex[org/foo/.*Factory[0-9].*]'
      }
    }
    ```

## Automatically Relocating Dependencies

Shadow is shipped with a task that can be used to automatically configure all packages from all dependencies to be relocated.
This feature was formally shipped into a 2nd plugin (`com.github.johnrengelman.plugin-shadow`) but has been
removed for clarity reasons in version 4.0.0.

To configure automatic dependency relocation, set `enableRelocation = true` and optionally specify a custom
`relocationPrefix` to override the default value of `"shadow"`.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      enableRelocation = true
      relocationPrefix = "myapp"
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      enableRelocation = true
      relocationPrefix = "myapp"
    }
    ```

In versions before 8.1.0 it was necessary to configure a separate `ConfigureShadowRelocation` task for this.

> Configuring package auto relocation can add significant time to the shadow process as it will process all dependencies
in the configurations declared to be shadowed. By default, this is the `runtime` or `runtimeClasspath` configurations.
Be mindful that some Gradle plugins will automatically add dependencies to your class path. You may need to remove these 
dependencies if you do not intend to shadow them into your library.
