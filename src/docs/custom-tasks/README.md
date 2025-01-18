# Creating a Custom ShadowJar Task

The built in `shadowJar` task only provides an output for the `main` source set of the project.
It is possible to add arbitrary [`ShadowJar`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html) 
tasks to a project. When doing so, ensure that the `configurations` property is specified to inform Shadow which 
dependencies to merge into the output.

```groovy
// Shadowing Test Sources and Dependencies
def testShadowJar = tasks.register('testShadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
  group = com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.GROUP_NAME
  description = "Create a combined JAR of project and test dependencies"
  
  archiveClassifier = "tests"
  from sourceSets.test.output
  configurations = [project.configurations.testRuntimeClasspath]
}

// Optionally, make the `assemble` task depend on the new task
tasks.named('assemble') {
  dependsOn testShadowJar
}
```

The code snippet above will generate a shadowed JAR containing both the `main` and `test` sources as well as all `testRuntimeOnly`
and `testImplementation` dependencies.
The file is output to `build/libs/<project>-<version>-tests.jar`.
