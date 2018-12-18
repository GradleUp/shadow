# Filtering Shadow Jar Contents

The final contents of a shadow JAR can be filtered using the `exclude` and `include` methods inherited from Gradle's
`Jar` task type.

Refer to the [Jar](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html) documentation for details
on the various versions of the methods and their behavior.

When using `exclude`/`include` with a `ShadowJar` task, the resulting copy specs are applied to the _final_ JAR
contents.
This means that, the configuration is applied to the individual files from both the project source set or _any_
of the dependencies to be merged.

```groovy
// Exclude a file from Shadow Jar
shadowJar {
   exclude 'a2.properties'
}
```


Excludes and includes can be combined just like a normal `Jar` task, with `excludes` taking precedence over `includes`.
Additionally, ANT style patterns can be used to match multiple files.

```groovy
// Configuring output using ANT patterns
shadowJar {
   include '*.jar'
   include '*.properties'
   exclude 'a2.properties'
}
```
