# Relocating Packages

Shadow is capable of scanning a project's classes and relocating specific dependencies to a new location.
This is often required when one of the dependencies is susceptible to breaking changes in versions or
to classpath pollution in a downstream project.

> Google's Guava and the ASM library are typical cases where package relocation can come in handy.

Shadow uses the ASM library to modify class byte code to replace the package name and any import
statements for a class.
Any non-class files that are stored within a package structure are also relocated to the new location.

```groovy
// Relocating a Package
shadowJar {
   relocate 'junit.framework', 'shadow.junit'
}
```

The code snippet will rewrite the location for any class in the `junit.framework` to be `shadow.junit`.
For example, the class `junit.textui.TestRunner` becomes `shadow.junit.TestRunner`.
In the resulting JAR, the class file is relocated from `junit/textui/TestRunner.class` to
`shadow/junit/TestRunner.class`.

> Relocation operates at a package level.
It is not necessary to specify any patterns for matching, it will operate simply on the prefix
provided.

> Relocation will be applied globally to all instance of the matched prefix.
That is, it does **not** scope to _only_ the dependencies being shadowed.
Be specific as possible when configuring relocation as to avoid unintended relocations.

## Filtering Relocation

Specific classes or files can be `included`/`excluded` from the relocation operation if necessary.

```groovy
// Configuring Filtering for Relocation
shadowJar {
   relocate('junit.textui', 'a') {
       exclude 'junit.textui.TestRunner'
   }
   relocate('junit.framework', 'b') {
       include 'junit.framework.Test*'
   }
}
```

## Automatically Relocating Dependencies

Shadow ships with a task that can be used to automatically configure all packages from all dependencies to be relocated.
This feature was formally shipped into a 2nd plugin (`com.github.johnrengelman.plugin-shadow`) but has been
removed for clarity reasons in version 4.0.0.

To configure automatic dependency relocation, declare a task of type `ConfigureShadowRelocation` and configure the
`target` parameter to be the `ShadowJar` task you wish to auto configure. You will also need to declared a task
dependency so the tasks execute in the correct order.

```groovy
// Configure Auto Relocation
import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation

task relocateShadowJar(type: ConfigureShadowRelocation) {
    target = tasks.shadowJar
    prefix = "myapp" // Default value is "shadow"

}

tasks.shadowJar.dependsOn tasks.relocateShadowJar
```

> Configuring package auto relocation can add significant time to the shadow process as it will process all dependencies
in the configurations declared to be shadowed. By default, this is the `runtime` or `runtimeClasspath` configurations.
Be mindful that some Gradle plugins will automatically add dependencies to your class path. You may need to remove these 
dependencies if you do not intend to shadow them into your library.  The `java-gradle-plugin` would normally cause such
problems if it were not for the special handling that Shadow provides as described in 
[Special Handling of the Java Gradle Plugin Development Plugin](/plugins/#special-handling-of-the-java-gradle-plugin-gevelopmeny-plugin).
