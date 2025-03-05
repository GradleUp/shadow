# Using Shadow in Multi-Project Builds

When using Shadow in a multi-project build, project dependencies will be treated the same as
external dependencies.
That is a project dependency will be merged into the `shadowJar` output of the project that
is applying the Shadow plugin.

## Depending on the Shadow Jar from Another Project

In a multi-project build there may be one project that applies Shadow and another that
requires the shadowed JAR as a dependency.
In this case, use Gradle's normal dependency declaration mechanism to depend on the `shadow`
configuration of the shadowed project.

```groovy
// Depending On Shadow Output of Project
dependencies {
  implementation project(path: ':api', configuration: 'shadow')
}
```