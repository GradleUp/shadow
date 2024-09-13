# Minimizing

Shadow can automatically remove all classes of dependencies that are not used by the project, thereby minimizing the resulting shadowed JAR.

```groovy
// Minimizing a shadow JAR
tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
  minimize()
}
```

A dependency can be excluded from the minimization process, thereby forcing it's inclusion the shadow JAR.
This is useful when the dependency analyzer cannot find the usage of a class programmatically, for example if the class
is loaded dynamically via `Class.forName(String)`. Each of the `group`, `name` and `version` fields separated by `:` of
a `dependency` is interpreted as a regular expression.

```groovy
// Force a class to be retained during minimization
tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
  minimize {
    exclude(dependency('org.scala-lang:.*:.*'))
  }
}
```

> Dependencies scoped as `api` will automatically excluded from minimization and used as "entry points" on minimization.

Similar to dependencies, projects can also be excluded.

```groovy
tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
    minimize {
        exclude(project(":api"))
    }
}
```

> When excluding a `project`, all dependencies of the excluded `project` are automatically
  excluded as well.
