# Using Shadow to Package Gradle Plugins

In some scenarios, writing a Gradle plugin can be problematic because your plugin may depend on a version that
conflicts with the same dependency provided by the Gradle runtime. If this is the case, then you can utilize Shadow
to relocate your dependencies to a different package name to avoid the collision.

Configuring the relocation has always been possible, but the build author is required to know all the package names
beforehand. As of Shadow v8.1.0, automatic package relocation can be enabled by setting the `enabledRelocation`
and `relocationPrefix` settings on any [`ShadowJar`][ShadowJar] task.

A simple Gradle plugin can use this feature by applying the `shadow` plugin and configuring the [`ShadowJar`][ShadowJar]
task for relocation.

=== "Kotlin"

    ```kotlin
    plugins {
      id("com.gradle.plugin-publish") version "latest"
      id("com.gradleup.shadow")
    }

    dependencies {
      implementation("org.jdom:jdom2:2.0.6")
      implementation("org.ow2.asm:asm:6.0")
      implementation("org.ow2.asm:asm-commons:6.0")
      implementation("commons-io:commons-io:2.4")
      implementation("org.apache.ant:ant:1.9.4")
      implementation("org.codehaus.plexus:plexus-utils:2.0.6")
    }

    tasks.shadowJar {
      enableAutoRelocation = true
      archiveClassifier = ""
    }
    ```

=== "Groovy"

    ```groovy
    plugins {
      id 'com.gradle.plugin-publish' version 'latest'
      id 'com.gradleup.shadow'
    }

    dependencies {
      implementation 'org.jdom:jdom2:2.0.6'
      implementation 'org.ow2.asm:asm:6.0'
      implementation 'org.ow2.asm:asm-commons:6.0'
      implementation 'commons-io:commons-io:2.4'
      implementation 'org.apache.ant:ant:1.9.4'
      implementation 'org.codehaus.plexus:plexus-utils:2.0.6'
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      enableAutoRelocation = true
      archiveClassifier = ''
    }
    ```

## Publishing shadowed Gradle plugins

The Gradle Publish Plugin introduced support for plugins packaged with Shadow in version 1.0.0.
Starting with this version, plugin projects that apply both Shadow and the Gradle Plugin Publish plugin will be
automatically configured to publish the output of the [`ShadowJar`][ShadowJar] tasks as the consumable artifact for the
plugin. See the
[Gradle Plugin Publish docs](https://docs.gradle.org/current/userguide/publishing_gradle_plugins.html#shadow_dependencies)
for details.



[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
[ShadowJar]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
