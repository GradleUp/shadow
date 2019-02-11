# Using Shadow to Package Gradle Plugins

In some scenarios, writing a Gradle plugin can be problematic because your plugin may depend on a version that
conflicts with the same dependency provided by the Gradle runtime. If this is the case, then you can utilize Shadow
to relocate your dependencies to a different package name to avoid the collision.

Configuring the relocation has always been possible, but the build author is required to know all the package names
before hand. Shadow v2.0 corrects this by introducing a new task type `ConfigureShadowRelocation`.
Tasks of this type are configured to target an instance of a `ShadowJar` task and run immediately before it.

The `ConfigureShadowRelocation` task, scans the dependencies from the configurations specified on the associated
`ShadowJar` task and collects the package names contained within them. It then configures relocation for these
packages using the specified `prefix` on the associated `ShadowJar` task.

While this is useful for developing Gradle plugins, nothing about the `ConfigureShadowRelocation` task is tied to
Gradle projects. It can be used for standard Java or Groovy projects.

A simple Gradle plugin can use this feature by applying the `shadow` plugin and configuring the relocation task
to execute before the `shadowJar` tasks:

```groovy no-plugins
import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation

plugins {
  id 'com.github.johnrengelman.shadow' version '@version@'
  id 'java'
}

dependencies {
    shadow localGroovy()
    shadow gradleApi()

    compile 'org.jdom:jdom2:2.0.6'
    compile 'org.ow2.asm:asm:6.0'
    compile 'org.ow2.asm:asm-commons:6.0'
    compile 'commons-io:commons-io:2.4'
    compile 'org.apache.ant:ant:1.9.4'
    compile 'org.codehaus.plexus:plexus-utils:2.0.6'
}

task relocateShadowJar(type: ConfigureShadowRelocation) {
    target = tasks.shadowJar
}

tasks.shadowJar.dependsOn tasks.relocateShadowJar
```

Note that the `localGroovy()` and `gradleApi()` dependencies are added to the `shadow` configuration instead of the
normal `compile` configuration. These 2 dependencies are provided by Gradle to compile your project but are ultimately
provided by the Gradle runtime when executing the plugin. Thus, it is **not** advisable to bundle these dependencies
with your plugin.

## Special Handling of the Java Gradle Plugin Development Plugin

The Java Gradle Plugin Development plugin, `java-gradle-plugin`, automatically adds the full Gradle API to the `compile` 
configuration; thus overriding a possible assignment of `gradleApi()` to the `shadow` configuration.  Since it is never
a good idea to include the Gradle API when creating a Gradle plugin, the dependency is removed so that it is not 
included in the resultant shadow jar.  Virtually:

    // needed to prevent inclusion of gradle-api into shadow JAR
    configurations.compile.dependencies.remove dependencies.gradleApi()
