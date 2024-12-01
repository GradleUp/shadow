# Using Shadow to Package Gradle Plugins

In some scenarios, writing a Gradle plugin can be problematic because your plugin may depend on a version that
conflicts with the same dependency provided by the Gradle runtime. If this is the case, then you can utilize Shadow
to relocate your dependencies to a different package name to avoid the collision.

Configuring the relocation has always been possible, but the build author is required to know all the package names
beforehand. As of Shadow v8.1.0, automatic package relocation can be enabled by setting the `enabledRelocation` 
and `relocationPrefix` settings on any `ShadowJar` task.

A simple Gradle plugin can use this feature by applying the `shadow` plugin and configuring the `shadowJar` task for relocation.

```groovy
apply plugin: 'java'
apply plugin: 'com.gradleup.shadow'

dependencies {
  shadow localGroovy()
  shadow gradleApi()

  implementation 'org.jdom:jdom2:2.0.6'
  implementation 'org.ow2.asm:asm:6.0'
  implementation 'org.ow2.asm:asm-commons:6.0'
  implementation 'commons-io:commons-io:2.4'
  implementation 'org.apache.ant:ant:1.9.4'
  implementation 'org.codehaus.plexus:plexus-utils:2.0.6'
}

tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
  enableRelocation = true
}
```

Note that the `localGroovy()` and `gradleApi()` dependencies are added to the `shadow` configuration instead of the
normal `compile` configuration. These 2 dependencies are provided by Gradle to compile your project but are ultimately
provided by the Gradle runtime when executing the plugin. Thus, it is **not** advisable to bundle these dependencies
with your plugin.

## Publishing shadowed Gradle plugins
The Gradle Publish Plugin introduced support for plugins packaged with Shadow in version 1.0.0.
Starting with this version, plugin projects that apply both Shadow and the Gradle Plugin Publish plugin will be
automatically configured to publish the output of the `shadowJar` tasks as the consumable artifact for the plugin.
See the [Gradle Plugin Publish docs](https://docs.gradle.org/current/userguide/publishing_gradle_plugins.html#shadow_dependencies) for details.

## Automatic package relocation with Shadow prior to v8.1.0

Prior to Shadow v8.1.0, Shadow handled this by introducing a new task type `ConfigureShadowRelocation`.
Tasks of this type are configured to target an instance of a `ShadowJar` task and run immediately before it.

The `ConfigureShadowRelocation` task, scans the dependencies from the configurations specified on the associated
`ShadowJar` task and collects the package names contained within them. It then configures relocation for these
packages using the specified `prefix` on the associated `ShadowJar` task.

While this is useful for developing Gradle plugins, nothing about the `ConfigureShadowRelocation` task is tied to
Gradle projects. It can be used for standard Java or Groovy projects.
