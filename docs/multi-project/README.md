# Using Shadow in Multi-Project Builds

When using Shadow in a multi-project build, project dependencies will be treated the same as
external dependencies.
That is a project dependency will be merged into the [`ShadowJar`][ShadowJar] output of the project that
is applying the Shadow plugin.

## Depending on the Shadow Jar from Another Project

In a multi-project build there may be one project that applies Shadow and another that
requires the shadowed JAR as a dependency.
In this case, use Gradle's normal dependency declaration mechanism to depend on the `shadow`
configuration of the shadowed project.

=== "Kotlin"

    ```kotlin
    dependencies {
      implementation(project(path = ":api", configuration = "shadow"))
    }
    ```

=== "Groovy"

    ```groovy
    dependencies {
      implementation project(path: ':api', configuration: 'shadow')
    }
    ```

However, this is not ideal as it allows one to mistakenly declare `implementation(project(":api"))` thus providing unshadowed packages or dependencies.

As a reminder in Gradle, on a **java library**, configurations like `api`, `implementation`, `runtimeOnly` (and `compileOnly`) are where dependencies are declared (they are [_declarable_](https://docs.gradle.org/current/userguide/declaring_configurations.html#1_declarable_configurations)). But when consuming a project like `project(":api")`, Gradle looks for [_consumable_ configurations](https://docs.gradle.org/current/userguide/declaring_configurations.html#3_consumable_configurations), and there are [`apiElements` (for compilation) and `runtimeElements` (for runtime)](https://docs.gradle.org/current/userguide/declaring_configurations.html#3_consumable_configurations:~:text=A%20library%20typically%20provides%20consumable%20configurations%20like%20apiElements%20(for%20compilation)%20and%20runtimeElements%20(for%20runtime%20dependencies).) for a **java library** project.

By tuning these _consumable_ configurations, you can enable a single declaration of the project without users needing to specify exposed configurations.

**Shadowed project**

=== "Kotlin"

    ```kotlin
    configurations {
      named("apiElements") {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
      }
      named("runtimeElements") {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
      }
    }
    ```

=== "Groovy"

    ```groovy
    configurations {
      apiElements {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
      }
      runtimeElements {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
      }
    }
    ```

**Consuming projects**

=== "Kotlin"

    ```kotlin
    dependencies {
      implementation(project(":api"))
    }
    ```

=== "Groovy"

    ```groovy
    dependencies {
      implementation project(':api')
    }
    ```

[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
[ShadowJar]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
