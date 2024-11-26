# Creating a Custom ShadowJar Task

The built in `shadowJar` task only provides an output for the `main` source set of the project.
It is possible to add arbitrary [`ShadowJar`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html) 
tasks to a project. When doing so, ensure that the `configurations` property is specified to inform Shadow which 
dependencies to merge into the output.

```groovy
// Shadowing Test Sources and Dependencies
task testJar(type: com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
  archiveClassifier.set("tests")
  from sourceSets.test.output
  configurations = [project.configurations.testRuntimeClasspath]
}
```

The code snippet above will generate a shadowed JAR containing both the `main` and `test` sources as well as all `testRuntimeOnly`
and `testImplementation` dependencies.
The file is output to `build/libs/<project>-<version>-tests.jar`.
    
