# Publishing Shadow JARs

## Publishing with Maven-Publish Plugin

The Shadow plugin will automatically configure the necessary tasks in the presence of Gradle's
[`maven-publish`][maven-publish] plugin.
The plugin provides the `shadow` component to configure the publication with the necessary
artifact and dependencies in the POM file.

=== "Kotlin"

    ```kotlin
    plugins {
      java
      `maven-publish`
      id("com.gradleup.shadow")
    }

    publishing {
      publications {
        create<MavenPublication>("shadow") {
          from(components["shadow"])
        }
      }
      repositories {
        maven("https://repo.myorg.com")
      }
    }
    ```

=== "Groovy"

    ```groovy
    plugins {
      id 'java'
      id 'maven-publish'
      id 'com.gradleup.shadow'
    }

    publishing {
      publications {
        shadow(MavenPublication) {
          from components.shadow
        }
      }
      repositories {
        maven { url = 'https://repo.myorg.com' }
      }
    }
    ```

## Shadow Variant in Default Java Component

The Shadow plugin adds an optional variant to the `java` component when publishing. This variant contains the shadowed
JAR. This allows consumers of the published library to choose between the standard JAR and the shadowed JAR.

This feature is enabled by default. It can be disabled by setting the `addShadowVariantIntoJavaComponent` property in
the `shadow` extension to `false`. If you want to publish the standard JAR only, disable this feature like:

=== "Kotlin"

    ```kotlin
    plugins {
      java
      `maven-publish`
      id("com.gradleup.shadow")
    }

    shadow {
      addShadowVariantIntoJavaComponent = false
    }

    publishing {
      publications {
        create<MavenPublication>("shadow") {
          from(components["shadow"])
        }
      }
      repositories {
        maven("https://repo.myorg.com")
      }
    }
    ```

=== "Groovy"

    ```groovy
    plugins {
      id 'java'
      id 'maven-publish'
      id 'com.gradleup.shadow'
    }

    shadow {
      addShadowVariantIntoJavaComponent = false
    }

    publishing {
      publications {
        shadow(MavenPublication) {
          from components.shadow
        }
      }
      repositories {
        maven { url = 'https://repo.myorg.com' }
      }
    }
    ```

The target JVM version attribute (`org.gradle.jvm.version`) of the shadowed variant is added by default, it is useful
for consumers to select the correct variant based on their target JVM version. But it may cause issues in some cases,
you can disable this by setting the `addTargetJvmVersionAttribute` property in the `shadow` extension to `false`:

=== "Kotlin"

    ```kotlin
    shadow {
      addTargetJvmVersionAttribute = false
    }
    ```

=== "Groovy"

    ```groovy
    shadow {
      addTargetJvmVersionAttribute = false
    }
    ```

## Shadow Configuration and Publishing

The Shadow plugin provides a custom configuration (`configurations.shadow`) to specify
runtime dependencies that are **not** merged into the final JAR file.
When configuring publishing with the Shadow plugin, the dependencies in the `shadow`
configuration, are translated to become `RUNTIME` scoped dependencies of the
published artifact.

No other dependencies are automatically configured for inclusion in the POM file.
For example, excluded dependencies are **not** automatically added to the POM file or
if the configuration for merging are modified by specifying
`shadowJar.configurations = [configurations.myConfiguration]`, there is no automatic
configuration of the POM file.

This automatic configuration occurs _only_ when using the above methods for
configuring publishing. If this behavior is not desirable, then publishing **must**
be manually configured.

## Publish the Shadowed JAR instead of the Original JAR

You may want to publish the shadowed JAR instead of the original JAR. This can be done by trimming
the `archiveClassifier` of the shadowed JAR like the following:

=== "Kotlin"

    ```kotlin
    plugins {
      java
      `maven-publish`
      id("com.gradleup.shadow")
    }

    group = "shadow"
    version = "1.0"

    dependencies {
      val retrofitVersion = "2.12.0"
      // This will be bundled in the shadowed JAR and not declared in the POM.
      implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
      // This will be excluded from the shadowed JAR but declared as a runtime dependency in `META-INF/MANIFEST.MF`
      // file's `Class-Path` entry, and also in the POM file.
      shadow("com.squareup.retrofit2:converter-java8:$retrofitVersion")
      // This will be excluded from the shadowed JAR and not declared in the POM or `META-INF/MANIFEST.MF`.
      compileOnly("com.squareup.retrofit2:converter-scalars:$retrofitVersion")
    }

    tasks.shadowJar {
      archiveClassifier = ""
    }

    publishing {
      publications {
        create<MavenPublication>("shadow") {
          from(components["shadow"])

          // Optionally, you can add extra dependencies to the POM file like the following:
          pom.withXml {
            val dependenciesNode = asNode().get("dependencies") ?: asNode().appendNode("dependencies")
            val node = (dependenciesNode as groovy.util.Node).appendNode("dependency")
            node.appendNode("groupId", "com.squareup.retrofit2")
            node.appendNode("artifactId", "converter-gson")
            node.appendNode("version", "2.12.0")
            node.appendNode("scope", "runtime")
          }
        }
      }
      repositories {
        maven("https://repo.myorg.com")
      }
    }
    ```

=== "Groovy"

    ```groovy
    plugins {
      id 'java'
      id 'maven-publish'
      id 'com.gradleup.shadow'
    }

    group = 'shadow'
    version = '1.0'

    dependencies {
      def retrofitVersion = '2.12.0'
      // This will be bundled in the shadowed JAR and not declared in the POM.
      implementation "com.squareup.retrofit2:retrofit:$retrofitVersion"
      // This will be excluded from the shadowed JAR but declared as a runtime dependency in `META-INF/MANIFEST.MF`
      // file's `Class-Path` entry, and also in the POM file.
      shadow "com.squareup.retrofit2:converter-java8:$retrofitVersion"
      // This will be excluded from the shadowed JAR and not declared in the POM or `META-INF/MANIFEST.MF`.
      compileOnly "com.squareup.retrofit2:converter-scalars:$retrofitVersion"
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      archiveClassifier = ''
    }

    publishing {
      publications {
        shadow(MavenPublication) {
          from components.shadow

          // Optionally, you can add extra dependencies to the POM file like the following:
          pom.withXml { xml ->
            def dependenciesNode = xml.asNode().get('dependencies') ?: xml.asNode().appendNode('dependencies')
            def node = dependenciesNode.appendNode('dependency')
            node.appendNode('groupId', 'com.squareup.retrofit2')
            node.appendNode('artifactId', 'converter-gson')
            node.appendNode('version', '2.12.0')
            node.appendNode('scope', 'runtime')
          }
        }
      }
      repositories {
        maven { url = 'https://repo.myorg.com' }
      }
    }
    ```

## Publish Custom ShadowJar Task Outputs

It is possible to publish a custom [`ShadowJar`][ShadowJar] task's output via the
[`MavenPublication.artifact()`][MavenPublication.artifact] method.

=== "Kotlin"

    ```kotlin
    plugins {
      java
      `maven-publish`
      id("com.gradleup.shadow")
    }

    val testShadowJar by tasks.registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
      description = "Create a combined JAR of project and test dependencies"
      archiveClassifier = "tests"
      from(sourceSets.test.map { it.output })
      configurations = project.configurations.testRuntimeClasspath.map { listOf(it) }
    }

    dependencies {
      testImplementation("junit:junit:3.8.2")
    }

    publishing {
      publications {
        create<MavenPublication>("shadow") {
          artifact(testShadowJar)
        }
      }
      repositories {
        maven("https://repo.myorg.com")
      }
    }
    ```

=== "Groovy"

    ```groovy
    plugins {
      id 'java'
      id 'maven-publish'
      id 'com.gradleup.shadow'
    }

    def testShadowJar = tasks.register('testShadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      description = 'Create a combined JAR of project and test dependencies'
      archiveClassifier = 'tests'
      from sourceSets.named('test').map { it.output }
      configurations = project.configurations.named('testRuntimeClasspath').map { [it] }
    }

    dependencies {
      testImplementation 'junit:junit:3.8.2'
    }

    publishing {
      publications {
        shadow(MavenPublication) {
          artifact(testShadowJar)
        }
      }
      repositories {
        maven { url = 'https://repo.myorg.com' }
      }
    }
    ```

## Publish the Shadowed JAR with Custom Artifact Name

It is possible to configure the artifact name of the shadowed JAR via properties like `archiveBaseName`, see more
customizable properties listed in [Configuring Output Name](../configuration/README.md#configuring-output-name). e.g.

=== "Kotlin"

    ```kotlin
    plugins {
      java
      `maven-publish`
      id("com.gradleup.shadow")
    }

    group = "my-group"
    version = "1.0"

    tasks.shadowJar {
      archiveClassifier = "my-classifier"
      archiveExtension = "my-ext"
      archiveBaseName = "maven-all"
    }

    publishing {
      publications {
        create<MavenPublication>("shadow") {
          from(components["shadow"])
          // This will override `archiveBaseName`.
          artifactId = "my-artifact"
        }
      }
      repositories {
        maven("https://repo.myorg.com")
      }
    }
    ```

=== "Groovy"

    ```groovy
    plugins {
      id 'java'
      id 'maven-publish'
      id 'com.gradleup.shadow'
    }

    group = 'my-group'
    version = '1.0'

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      archiveClassifier = 'my-classifier'
      archiveExtension = 'my-ext'
      archiveBaseName = 'maven-all'
    }

    publishing {
      publications {
        shadow(MavenPublication) {
          from components.shadow
          // This will override `archiveBaseName`.
          artifactId = 'my-artifact'
        }
      }
      repositories {
        maven { url = 'https://repo.myorg.com' }
      }
    }
    ```

We modified `archiveClassifier`, `archiveExtension` and `archiveBaseName` in this example, the published artifact will
be named `my-artifact-2.0-my-classifier.my-ext` instead of `1.0-all.jar`.



[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
[MavenPublication.artifact]: https://docs.gradle.org/current/dsl/org.gradle.api.publish.maven.MavenPublication.html#org.gradle.api.publish.maven.MavenPublication:artifact(java.lang.Object)
[ShadowJar]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
[maven-publish]: https://docs.gradle.org/current/userguide/publishing_maven.html
