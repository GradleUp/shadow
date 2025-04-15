# Creating a Custom ShadowJar Task

The built in [ShadowJar] task only provides an output for the `main` source set of the project.
It is possible to add arbitrary [ShadowJar] 
tasks to a project. When doing so, ensure that the `configurations` property is specified to inform Shadow which 
dependencies to merge into the output.

=== "Kotlin"

    ```kotlin
    val testShadowJar by tasks.registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
      group = LifecycleBasePlugin.BUILD_GROUP
      description = "Create a combined JAR of project and test dependencies"

      archiveClassifier = "tests"
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
      group = LifecycleBasePlugin.BUILD_GROUP
      description = 'Create a combined JAR of project and test dependencies'

      archiveClassifier = 'tests'
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

The code snippet above will generate a shadowed JAR containing both the `main` and `test` sources as well as all `testRuntimeOnly`
and `testImplementation` dependencies.
The file is output to `build/libs/<project>-<version>-tests.jar`.



[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
[ShadowJar]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
