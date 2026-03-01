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

## Making the Shadowed JAR the Default Artifact

When a project needs to expose the shadowed JAR as its default output — so that consumers can depend on it without specifying the `shadow` configuration explicitly — you can reconfigure the [consumable configurations][gradle-consumable-configs] `apiElements` and `runtimeElements` to publish the shadowed JAR instead of the regular JAR.

As a reminder, configurations like `api` and `implementation` are where dependencies are declared ([_declarable_][gradle-declarable-configs]), while `apiElements` and `runtimeElements` are what Gradle consumes when projects depend on each other.

By tuning these consumable configurations, a simple declaration like `implementation(project(":api"))` will resolve to the shadowed JAR by default, preventing accidental consumption of the unshadowed artifact.

**In the shadowed project (`:api`):**

=== "Kotlin"

    ```kotlin
    plugins {
      `java-library`
      id("com.gradleup.shadow")
    }

    configurations {
      named("apiElements") {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
        outgoing.variants.clear()
      }
      named("runtimeElements") {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
        outgoing.variants.clear()
      }
    }
    ```

=== "Groovy"

    ```groovy
    plugins {
      id 'java-library'
      id 'com.gradleup.shadow'
    }

    configurations {
      apiElements {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.named('shadowJar'))
        outgoing.variants.clear()
      }
      runtimeElements {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.named('shadowJar'))
        outgoing.variants.clear()
      }
    }
    ```

!!! important

    Clearing `outgoing.variants` ensures Gradle doesn't select the unshadowed `classes` variant by default during compilation.

**Consuming projects can then depend on `:api` without specifying the `shadow` configuration:**

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

### Excluding Transitive Dependencies

If you want to exclude transitive dependencies that were bundled into the shadow JAR, you can add [`exclude` rules][gradle-exclude-rules] to the configurations as well:

=== "Kotlin"

    ```kotlin
    configurations {
      named("apiElements") {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
        outgoing.variants.clear()
        exclude(group = "com.example", module = "bundled-library")
      }
      named("runtimeElements") {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
        outgoing.variants.clear()
        exclude(group = "com.example", module = "bundled-library")
      }
    }
    ```

=== "Groovy"

    ```groovy
    configurations {
      apiElements {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.named('shadowJar'))
        outgoing.variants.clear()
        exclude group: 'com.example', module: 'bundled-library'
      }
      runtimeElements {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.named('shadowJar'))
        outgoing.variants.clear()
        exclude group: 'com.example', module: 'bundled-library'
      }
    }
    ```

[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
[ShadowJar]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
[gradle-consumable-configs]: https://docs.gradle.org/current/userguide/dependency_management_terminology.html#sub:terminology_consumable_configuration
[gradle-declarable-configs]: https://docs.gradle.org/current/userguide/dependency_management_terminology.html#sub:terminology_declarable_configuration
[gradle-exclude-rules]: https://docs.gradle.org/current/userguide/dependency_downgrade_and_exclude.html#sec:excluding-transitive-deps
