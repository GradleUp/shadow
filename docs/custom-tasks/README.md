# Creating a Custom ShadowJar Task

The built in [`ShadowJar`][ShadowJar] task only provides an output for the `main` source set of the project.
It is possible to add arbitrary [`ShadowJar`][ShadowJar] tasks to a project. When doing so, ensure that the
[`configurations`][ShadowJar.configurations] property is specified to inform Shadow which dependencies to merge into
the output.

=== "Kotlin"

    ```kotlin
    val testShadowJar by tasks.registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
      description = "Create a combined JAR of project and test dependencies"

      archiveClassifier = "test"
      from(sourceSets.test.map { it.output })
      configurations = project.configurations.testRuntimeClasspath.map { listOf(it) }

      manifest {
        // Optionally, set the main class for the JAR.
        attributes(mapOf("Main-Class" to "test.Main"))
        // You can also set other attributes here.
      }
    }

    // Optionally, make the `assemble` task depend on the new task.
    tasks.assemble {
      dependsOn(testShadowJar)
    }
    ```

=== "Groovy"

    ```groovy
    def testShadowJar = tasks.register('testShadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      description = 'Create a combined JAR of project and test dependencies'

      archiveClassifier = 'test'
      from sourceSets.named('test').map { it.output }
      configurations = project.configurations.named('testRuntimeClasspath').map { [it] }

      manifest {
        // Optionally, set the main class for the JAR.
        attributes 'Main-Class': 'test.Main'
        // You can also set other attributes here.
      }
    }

    // Optionally, make the `assemble` task depend on the new task.
    tasks.named('assemble') {
      dependsOn testShadowJar
    }
    ```

The code snippet above will generate a shadowed JAR containing both the `main` and `test` sources as well as all
`testRuntimeOnly` and `testImplementation` dependencies. The file is output to
`build/libs/<project>-<version>-test.jar`.

## Creating a Dependencies-Only Shadow JAR

It is also possible to create a shadow JAR that contains *only* the dependencies and none of the project's own
source code. This is accomplished by creating a custom [`ShadowJar`][ShadowJar] task and configuring the
[`configurations`][ShadowJar.configurations] property, but **not** adding any project sources with `from(...)`.

=== "Kotlin"

    ```kotlin
    val dependencyShadowJar by tasks.registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
      description = "Create a shadow JAR of all dependencies"
      archiveClassifier = "dep"
      configurations = project.configurations.runtimeClasspath.map { listOf(it) }
    }
    ```

=== "Groovy"

    ```groovy
    def dependencyShadowJar = tasks.register('dependencyShadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      description = 'Create a shadow JAR of all dependencies'
      archiveClassifier = 'dep'
      configurations = project.configurations.named('runtimeClasspath').map { [it] }
    }
    ```

The above configuration will create a shadow JAR file that contains only the classes from the `runtimeClasspath`
configuration. The standard `jar` task will still produce a JAR with only the project's sources.


[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
[ShadowJar.configurations]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/configurations.html
[ShadowJar]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
