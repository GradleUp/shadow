# Filtering Shadow Jar Contents

The final contents of a shadow JAR can be filtered using the `exclude` and `include` methods inherited from Gradle's
[`Jar`][Jar] task type.

When using `exclude`/`include` with a [`ShadowJar`][ShadowJar] task, the resulting copy specs are applied to the
_final_ JAR contents. This means that, the configuration is applied to the individual files from both the project
source set or _any_ of the dependencies to be merged.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      exclude("a2.properties")
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      exclude 'a2.properties'
    }
    ```

Excludes and includes can be combined just like a normal [`Jar`][Jar] task, with `excludes` taking precedence over
`includes`. Additionally, ANT style patterns can be used to match multiple files.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      include("*.jar")
      include("*.properties")
      exclude("a2.properties")
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      include '*.jar'
      include '*.properties'
      exclude 'a2.properties'
    }
    ```



[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
[ShadowJar]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
