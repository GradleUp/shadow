# Minimizing

Shadow can automatically remove all classes of dependencies that are not used by the project, thereby minimizing the resulting shadowed JAR.

```groovy
// Minimizing an shadow JAR
shadowJar {
  minimize()
}
```

A dependency can be excluded from the minimization process, thereby forcing its inclusion into the shadow JAR.
This is useful when the dependency analyzer cannot find the usage of a class programmatically, for example if the class
is loaded dynamically via `Class.forName(String)`. Each of the `group`, `name` and `version` fields separated by `:` of
a `dependency` is interpreted as a regular expression.

```groovy
// Force a class to be retained during minimization
shadowJar {
  minimize {
    exclude(dependency('org.scala-lang:.*:.*'))
  }
}
```

> Dependencies scoped as `api` will automatically be excluded from minimization and used as "entry points" on minimization.

Similar to dependencies, projects can also be excluded.

```groovy
shadowJar {
    minimize {
        exclude(project(":api"))
    }
}
```

> When excluding a `project`, all dependencies of the excluded `project` are automatically
  excluded as well.

## Enable tree-shaking with R8

By default, Shadow will use a rather simple method to determine if a class from a dependency is actually being used within the
project classes. With rather complex dependencies like `guava` this can lead to unsatisfying results. In order to further minimize
the dependencies, `R8` (optimization / shrinking and obfuscation tool of the Android project) can be used, which facilitates a
tree-shaking algorithm in order remove all unused parts of dependency classes. The downside is that the execution will be slightly
slower, however leading to smaller shadow JARs.

```groovy
shadowJar {
    // enable minimization using R8
    useR8()
    minimize()
}
```

Additional rules (in `ProGuard` rule format) can be specified if needed:

```groovy
shadowJar {
    useR8 {
      rule '-keep class x.y.z.** { *; }'
      rule '-keepattributes Signature'
    }
    minimize()
}
```

Rules can also be loaded from an external file:

```groovy
shadowJar {
    useR8 {
      configuration file('mydefaultrules.pro')
      rule '-keep class x.y.z.** { *; }'
    }
    minimize()
}
```
