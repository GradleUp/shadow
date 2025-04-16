# Configuring Shadowed Dependencies

Shadow configures the default [`ShadowJar`][ShadowJar] task to merge all dependencies from the project's
`runtimeClasspath` configuration into the final JAR. The configurations from which to source dependencies for the
merging can be configured using the [`configurations`][ShadowJar.configurations] property of the
[`ShadowJar`][ShadowJar] task type.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      configurations = project.configurations.compileClasspath.map { listOf(it) }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      configurations = project.configurations.named('compileClasspath').map { [it] }
    }
    ```

The above code sample would configure the [`ShadowJar`][ShadowJar] task to merge dependencies from only the
`compileClasspath` configuration.
This means any dependency declared in the `runtimeOnly` configuration would be **not** be included in the final JAR.

> Note the literal use of [`project.configurations`][Project.configurations] when setting the
> [`configurations`][ShadowJar.configurations] attribute of a [`ShadowJar`][ShadowJar] task.
> This is **required**. It may be tempting to specify `configurations = [configurations.compileClasspath]` but this will
> not have the intended effect, as `configurations.compile` will try to delegate to the
> [`configurations`][ShadowJar.configurations] property of the [`ShadowJar`][ShadowJar] task instead of the `project`

## Embedding Jar Files Inside Your Shadow Jar

The [`ShadowJar`][ShadowJar] task is a subclass of the [`Jar`][Jar] task, which means that the [`Jar.from`][Jar.from]
method can be used to add extra files.

=== "Kotlin"

    ```kotlin
    dependencies {
      // Merge foo.jar (with unzipping) into the shadowed JAR.
      implementation(files("foo.jar"))
    }

    tasks.shadowJar {
      from("bar.jar") {
        // Copy bar.jar file (without unzipping) into META-INF/ in the shadowed JAR.
        into("META-INF")
      }
    }
    ```

=== "Groovy"

    ```groovy
    dependencies {
      // Merge foo.jar (with unzipping) into the shadowed JAR.
      implementation files('foo.jar')
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      from('bar.jar') {
        // Copy bar.jar file (without unzipping) into META-INF/ in the shadowed JAR.
        into('META-INF')
      }
    }
    ```

See also [Adding Extra Files](../README.md#adding-extra-files)

## Filtering Dependencies

Individual dependencies can be filtered from the final JAR by using the `dependencies` block of a
[`ShadowJar`][ShadowJar] task. Dependency filtering does **not** apply to transitive dependencies.
That is, excluding a dependency does not exclude any of its dependencies from the final JAR.

The `dependency` blocks provides a number of methods for resolving dependencies using the notations familiar from
Gradle's [`project.configurations`][Project.configurations] block.

=== "Kotlin"

    ```kotlin
    dependencies {
      implementation("org.apache.logging.log4j:log4j-core:2.11.1")
    }

    tasks.shadowJar {
      dependencies {
        exclude(dependency("org.apache.logging.log4j:log4j-core:2.11.1"))
      }
    }
    ```

=== "Groovy"

    ```groovy
    dependencies {
      implementation 'org.apache.logging.log4j:log4j-core:2.11.1'
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      dependencies {
        exclude(dependency('org.apache.logging.log4j:log4j-core:2.11.1'))
      }
    }
    ```

=== "Kotlin"

    ```kotlin
    dependencies {
      implementation(project(":api"))
    }

    tasks.shadowJar {
      dependencies {
        exclude(dependency(":api"))
      }
    }
    ```

=== "Groovy"

    ```groovy
    dependencies {
      implementation project(':api')
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      dependencies {
        exclude(project(':api'))
      }
    }
    ```

> While not being able to filter entire transitive dependency graphs might seem like an oversight, it is necessary
> because it would not be possible to intelligently determine the build author's intended results when there is a
> common dependency between two 1st level dependencies when one is excluded and the other is not.

### Using Regex Patterns to Filter Dependencies

Dependencies can be filtered using regex patterns.
Coupled with the `<group>:<artifact>:<version>` notation for dependencies, this allows for excluding/including
using any of these individual fields.

=== "Kotlin"

    ```kotlin
    dependencies {
      implementation("org.apache.logging.log4j:log4j-core:2.11.1")
    }

    tasks.shadowJar {
      dependencies {
        exclude(dependency("org.apache.logging.log4j:log4j-core:.*"))
      }
    }
    ```

=== "Groovy"

    ```groovy
    dependencies {
      implementation 'org.apache.logging.log4j:log4j-core:2.11.1'
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      dependencies {
        exclude(dependency('org.apache.logging.log4j:log4j-core:.*'))
      }
    }
    ```

Any of the individual fields can be safely absent and will function as though a wildcard was specified.

=== "Kotlin"

    ```kotlin
    dependencies {
      implementation("org.apache.logging.log4j:log4j-core:2.11.1")
    }

    tasks.shadowJar {
      dependencies {
        exclude(dependency(":org.apache.logging.log4j:log4j-core"))
      }
    }
    ```

=== "Groovy"

    ```groovy
    dependencies {
      implementation 'org.apache.logging.log4j:log4j-core:2.11.1'
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      dependencies {
        exclude(dependency('org.apache.logging.log4j:log4j-core'))
      }
    }
    ```

The above code snippet is functionally equivalent to the previous example.

This same pattern can be used for any of the dependency notation fields.

=== "Kotlin"

    ```kotlin
    dependencies {
      implementation("org.apache.logging.log4j:log4j-core:2.11.1")
    }

    tasks.shadowJar {
      dependencies {
        exclude(dependency(":log4j-core:2.11.1"))
      }
    }
    ```

=== "Groovy"

    ```groovy
    dependencies {
      implementation 'org.apache.logging.log4j:log4j-core:2.11.1'
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      dependencies {
        exclude(dependency(':log4j-core:2.11.1'))
      }
    }
    ```

=== "Kotlin"

    ```kotlin
    dependencies {
      implementation("org.apache.logging.log4j:log4j-core:2.11.1")
    }

    tasks.shadowJar {
      dependencies {
        exclude(dependency("org.apache.logging.log4j:2.11.1"))
      }
    }
    ```

=== "Groovy"

    ```groovy
    dependencies {
      implementation 'org.apache.logging.log4j:log4j-core:2.11.1'
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      dependencies {
        exclude(dependency('org.apache.logging.log4j:2.11.1'))
      }
    }
    ```

### Using type-safe dependency accessors

You can also use type-safe project accessors or version catalog accessors to filter dependencies.

=== "Kotlin"

    ```kotlin
    dependencies {
      // Have to declare this dependency in your libs.versions.toml
      implementation(libs.log4j.core)
      // Have to enable `TYPESAFE_PROJECT_ACCESSORS` flag in your settings.gradle.kts
      implementation(projects.api)
    }

    tasks.shadowJar {
      dependencies {
        exclude(dependency(libs.log4j.core))
        exclude(project(projects.api))
      }
    }
    ```

=== "Groovy"

    ```groovy
    dependencies {
      // Have to declare this dependency in your libs.versions.toml
      implementation libs.log4j.core
      // Have to enable `TYPESAFE_PROJECT_ACCESSORS` flag in your settings.gradle
      implementation projects.api
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      dependencies {
        exclude(dependency(libs.log4j.core))
        exclude(project(projects.api))
      }
    }
    ```

### Programmatically Selecting Dependencies to Filter

If more complex decisions are needed to select the dependencies to be included, the
[`ShadowJar.dependencies`][ShadowJar.dependencies]
block provides a method that accepts a `Closure` for selecting dependencies.

=== "Kotlin"

    ```kotlin
    dependencies {
      implementation("org.apache.logging.log4j:log4j-core:2.11.1")
    }

    tasks.shadowJar {
      dependencies {
        exclude {
          it.moduleGroup == "org.apache.logging.log4j"
        }
      }
    }
    ```

=== "Groovy"

    ```groovy
    dependencies {
      implementation 'org.apache.logging.log4j:log4j-core:2.11.1'
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      dependencies {
        exclude {
          it.moduleGroup == 'org.apache.logging.log4j'
        }
      }
    }
    ```



[Jar.from]: https://docs.gradle.org/current/dsl/org.gradle.jvm.tasks.Jar.html#org.gradle.jvm.tasks.Jar:from(java.lang.Object,%20org.gradle.api.Action)
[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
[ShadowJar.configurations]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/configurations.html
[ShadowJar.dependencies]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/dependencies.html
[ShadowJar]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
[Project.configurations]: https://docs.gradle.org/current/dsl/org.gradle.api.Project.html#org.gradle.api.Project:configurations
