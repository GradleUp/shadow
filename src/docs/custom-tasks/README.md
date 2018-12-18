# Creating a Custom ShadowJar Task

The built in `shadowJar` task only provides an output for the `main` source set of the project.
It is possible to add arbitrary [`ShadowJar`](http://imperceptiblethoughts.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html) 
tasks to a project. When doing so, ensure that the `configurations` property is specified to inform Shadow which 
dependencies to merge into the output.

```groovy
// Shadowing Test Sources and Dependencies
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

task testJar(type: ShadowJar) {
  classifier = 'tests'
  from sourceSets.test.output
  configurations = [project.configurations.testRuntime]
}
```

The code snippet above will geneated a shadowed JAR contain both the `main` and `test` sources as well as all `runtime`
and `testRuntime` dependencies.
The file is output to `build/libs/<project>-<version>-tests.jar`.
