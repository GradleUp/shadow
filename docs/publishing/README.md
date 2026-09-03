# Publishing Shadow JARs

## Publishing with Maven-Publish Plugin

The Shadow plugin will automatically configure the necessary tasks in the presence of Gradle's
[`maven-publish`][maven-publish] plugin. The plugin provides the `shadow` component to configure the publication with
the necessary artifact and dependencies in the POM file.

=== ":material-language-kotlin: build.gradle.kts"

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

=== ":simple-apachegroovy: build.gradle"

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

=== ":material-language-kotlin: build.gradle.kts"

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

=== ":simple-apachegroovy: build.gradle"

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

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    shadow {
      addTargetJvmVersionAttribute = false
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    shadow {
      addTargetJvmVersionAttribute = false
    }
    ```

The BUNDLING attribute (`org.gradle.dependency.bundling`) of the shadowed variant is set to `shadowed` by default, it is
useful for consumers to distinguish between normal and shadowed dependencies. You can override this attribute by setting
the `bundlingAttribute` property in the `shadow` extension:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    shadow {
      // Per description of the attribute, you should set it to either `Bundling.SHADOWED` or `Bundling.EMBEDDED`.
      bundlingAttribute = Bundling.EMBEDDED
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    shadow {
      // Per description of the attribute, you should set it to either `Bundling.SHADOWED` or `Bundling.EMBEDDED`.
      bundlingAttribute = Bundling.EMBEDDED
    }
    ```

## Shadow Configuration and Publishing

The Shadow plugin provides a custom configuration (`configurations.shadow`) to specify runtime dependencies that are
**not** merged into the final JAR file. When configuring publishing with the Shadow plugin, the dependencies in the
`shadow` configuration, are translated to become `RUNTIME` scoped dependencies of the published artifact.

No other dependencies are automatically configured for inclusion in the POM file. For example, excluded dependencies are
**not** automatically added to the POM file or if the configuration for merging are modified by specifying
`shadowJar.configurations = [configurations.myConfiguration]`, there is no automatic configuration of the POM file.

This automatic configuration occurs _only_ when using the above methods for configuring publishing. If this behavior is
not desirable, then publishing **must** be manually configured.

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    plugins {
      java
      `maven-publish`
      id("com.gradleup.shadow")
    }

    dependencies {
      // This will be bundled in the shadowed JAR and not declared in the POM.
      implementation("com.squareup.retrofit2:retrofit:<version>")
      // This will be excluded from the shadowed JAR but declared as a runtime dependency in `META-INF/MANIFEST.MF`
      // file's `Class-Path` entry, and also in the POM file.
      shadow("com.squareup.retrofit2:converter-java8:<version>")
      // This will be excluded from the shadowed JAR and not declared in the POM or `META-INF/MANIFEST.MF`.
      compileOnly("com.squareup.retrofit2:converter-scalars:<version>")
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
            node.appendNode("version", "<version>")
            node.appendNode("scope", "runtime")
          }
        }
      }
      repositories {
        maven("https://repo.myorg.com")
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    plugins {
      id 'java'
      id 'maven-publish'
      id 'com.gradleup.shadow'
    }

    dependencies {
      // This will be bundled in the shadowed JAR and not declared in the POM.
      implementation "com.squareup.retrofit2:retrofit:<version>"
      // This will be excluded from the shadowed JAR but declared as a runtime dependency in `META-INF/MANIFEST.MF`
      // file's `Class-Path` entry, and also in the POM file.
      shadow "com.squareup.retrofit2:converter-java8:<version>"
      // This will be excluded from the shadowed JAR and not declared in the POM or `META-INF/MANIFEST.MF`.
      compileOnly "com.squareup.retrofit2:converter-scalars:<version>"
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
            node.appendNode('version', '<version>')
            node.appendNode('scope', 'runtime')
          }
        }
      }
      repositories {
        maven { url = 'https://repo.myorg.com' }
      }
    }
    ```

## Publishing the Shadowed JAR instead of the Original JAR

You may want to publish the shadowed JAR instead of the original JAR. This can be done by trimming the
`archiveClassifier` of the shadowed JAR like the following:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    plugins {
      java
      `maven-publish`
      id("com.gradleup.shadow")
    }

    tasks.shadowJar {
      archiveClassifier = ""
    }

    publishing {
      publications {
        create<MavenPublication>("shadow")
      }
      repositories {
        maven("https://repo.myorg.com")
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    plugins {
      id 'java'
      id 'maven-publish'
      id 'com.gradleup.shadow'
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      archiveClassifier = ''
    }

    publishing {
      publications {
        shadow(MavenPublication)
      }
      repositories {
        maven { url = 'https://repo.myorg.com' }
      }
    }
    ```

Because the default `archiveClassifier` of [`Jar`][Jar] is `""` (empty), setting the `archiveClassifier` of
[`ShadowJar`][ShadowJar] to `""` (empty) will make collisions between the outputs of these two tasks in some cases. If
you don't need the standard JAR, you can disable the `jar` task like:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.jar {
      enabled = false
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('jar', Jar) {
      enabled = false
    }
    ```

Or set a different `archiveClassifier` for the standard [`Jar`][Jar] like:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.jar {
      archiveClassifier = "ignored"
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('jar', Jar) {
      archiveClassifier = 'ignored'
    }
    ```

## Publishing the Shadowed Gradle Plugins

The Gradle Publish Plugin introduced support for plugins packaged with Shadow in version 1.0.0. Starting with this
version, plugin projects that apply both Shadow and the Gradle Plugin Publish plugin will be automatically configured to
publish the output of the [`ShadowJar`][ShadowJar] tasks as the consumable artifact for the plugin. See
the [Gradle Plugin Publish docs][gradle-plugin-publish-docs] for details. The only thing you need to do from the Shadow
side is to empty the `archiveClassifier` like:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    plugins {
      id("com.gradle.plugin-publish") version "latest"
      id("com.gradleup.shadow")
    }

    dependencies {
      // Your plugin dependencies.
    }

    tasks.shadowJar {
      archiveClassifier = ""
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    plugins {
      id 'com.gradle.plugin-publish' version 'latest'
      id 'com.gradleup.shadow'
    }

    dependencies {
      // Your plugin dependencies.
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      archiveClassifier = ''
    }
    ```

## Publishing Custom ShadowJar Task Outputs

It is possible to publish a custom [`ShadowJar`][ShadowJar] task's output via the
[`MavenPublication.artifact()`][MavenPublication.artifact] method.

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    plugins {
      java
      `maven-publish`
      id("com.gradleup.shadow")
    }

    val testShadowJar = tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("testShadowJar") {
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

=== ":simple-apachegroovy: build.gradle"

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

## Publishing the Shadowed JAR with Custom Artifact Name

It is possible to configure the artifact name of the shadowed JAR via properties like `archiveBaseName`, see more
customizable properties listed in [Configuring Output Name][configuring-output-name]. e.g.

=== ":material-language-kotlin: build.gradle.kts"

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

=== ":simple-apachegroovy: build.gradle"

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

## Shadowed Sources JAR

When publishing a shadowed library, consumers and IDEs need a corresponding sources JAR to navigate source code and
inspect implementations. A standard sources JAR only contains your project's original un-relocated sources, which
causes broken navigation when consumers reference relocated packages.

Shadow automatically generates a **Shadowed Sources JAR** containing:

- Source files from your project's source sets (`Java`, `Kotlin`, `Groovy`, `Scala`).
- Source files resolved and merged from all bundled dependencies' `-sources.jar` archives.
- Relocated package declarations, imports, and symbol references that match your [`relocate`][ShadowJar.relocate] rules.
- Normalized package directory layout matching the declared `package` in each source file.
- Automatic filtering: dependencies excluded in `dependencies { exclude(...) }` or unused classes removed via
  `minimize()` are automatically excluded from the shadowed sources JAR as well.

### Publishing with `withSourcesJar()`

When Gradle's standard `java.withSourcesJar()` is enabled, the Shadow plugin automatically registers the
`shadowSourcesElements` variant and publishes the shadowed sources JAR alongside the shadowed binary JAR:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    plugins {
      java
      `maven-publish`
      id("com.gradleup.shadow")
    }

    java {
      withSourcesJar()
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

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    plugins {
      id 'java'
      id 'maven-publish'
      id 'com.gradleup.shadow'
    }

    java {
      withSourcesJar()
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

The published Maven publication will include both `<artifactId>-<version>-all.jar` and
`<artifactId>-<version>-all-sources.jar`.

> [!NOTE]
> Generating the companion shadowed sources JAR is controlled by [`generateSourcesJar`][ShadowJar.generateSourcesJar].
> In Java projects, it defaults to `true` when `java.withSourcesJar()` is enabled, and `false` otherwise to avoid
> unnecessary build overhead for application builds. If `withSourcesJar()` is omitted, publishing from
> `components["shadow"]` will only publish the shadowed binary JAR, preserving backward compatibility for existing builds.
> You can also explicitly toggle generation via `generateSourcesJar = true` (or `--generate-sources-jar`).

### Customizing the Sources Archive File

The companion shadowed sources JAR output location is configured via [`ShadowJar.archiveSourcesFile`][ShadowJar.archiveSourcesFile],
which defaults to the same destination and base name as `archiveFile` with `-sources.jar` suffix:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.shadowJar {
      archiveSourcesFile = layout.buildDirectory.file("custom-libs/my-sources.jar")
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      archiveSourcesFile = layout.buildDirectory.file('custom-libs/my-sources.jar')
    }
    ```

You can also customize the source inputs included in the companion sources JAR using
[`sourceSetsSourceDirs`][ShadowJar.sourceSetsSourceDirs] and
[`includedSourcesJars`][ShadowJar.includedSourcesJars]:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.shadowJar {
      // Add custom source directories
      sourceSetsSourceDirs.from("src/extra/java")

      // Add additional dependency sources JARs
      includedSourcesJars.from("libs/external-lib-sources.jar")
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      // Add custom source directories
      sourceSetsSourceDirs.from('src/extra/java')

      // Add additional dependency sources JARs
      includedSourcesJars.from('libs/external-lib-sources.jar')
    }
    ```

### Publishing with Kotlin Multiplatform (KMP)

In Kotlin Multiplatform (KMP) projects, publications are managed by the Kotlin Gradle Plugin (KGP) per target (such as
the `jvm` publication). You can attach the shadowed sources JAR artifact to the `jvm` Maven publication:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    plugins {
      id("org.jetbrains.kotlin.multiplatform")
      id("com.gradleup.shadow")
      `maven-publish`
    }

    kotlin {
      jvm()
    }

    publishing {
      publications {
        withType<MavenPublication>().configureEach {
          if (name == "jvm") {
            artifact(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").flatMap { it.archiveSourcesFile }) {
              classifier = "sources"
            }
          }
        }
      }
      repositories {
        maven("https://repo.myorg.com")
      }
    }

    tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
      generateSourcesJar = true
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    plugins {
      id 'org.jetbrains.kotlin.multiplatform'
      id 'com.gradleup.shadow'
      id 'maven-publish'
    }

    kotlin {
      jvm()
    }

    publishing {
      publications {
        withType(MavenPublication).configureEach {
          if (name == 'jvm') {
            artifact(tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar).flatMap { it.archiveSourcesFile }) {
              classifier = 'sources'
            }
          }
        }
      }
      repositories {
        maven { url = 'https://repo.myorg.com' }
      }
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      generateSourcesJar = true
    }
    ```

## Generating Javadoc or Dokka from Shadowed Sources

When creating fat / shadowed libraries, you may want to generate a complete Javadoc or Dokka JAR covering both your
project sources and shadowed dependency sources with relocated packages.

Because `shadowJar` outputs the shadowed sources archive at `archiveSourcesFile` (where relocated packages and source
contents have already been transformed), you can configure the `javadoc` task (or Dokka task) to consume the shadowed
sources and classes directly from `shadowJar`. The generated documentation will reflect the relocated package names
(e.g. `shadow.com.Example` instead of `com.Example`).

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    tasks.javadoc {
      classpath = files(tasks.shadowJar.flatMap { it.archiveFile })
      source = zipTree(tasks.shadowJar.flatMap { it.archiveSourcesFile })
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    tasks.named('javadoc', Javadoc) {
      classpath = files(tasks.named('shadowJar').flatMap { it.archiveFile })
      source = zipTree(tasks.named('shadowJar').flatMap { it.archiveSourcesFile })
    }
    ```

If using [Dokka][dokka] for Kotlin projects, you can extract the shadowed sources and configure `sourceRoots`:

=== ":material-language-kotlin: build.gradle.kts"

    ```kotlin
    plugins {
      kotlin("jvm")
      id("org.jetbrains.dokka")
      id("com.gradleup.shadow")
    }

    val extractShadowedSources = tasks.register<Sync>("extractShadowedSources") {
      from(zipTree(tasks.shadowJar.flatMap { it.archiveSourcesFile }))
      into(layout.buildDirectory.dir("extracted-shadowed-sources"))
    }

    dokka {
      dokkaSourceSets.configureEach {
        classpath.setFrom(tasks.shadowJar.flatMap { it.archiveFile })
        sourceRoots.setFrom(extractShadowedSources.map { it.destinationDir })
      }
    }
    ```

=== ":simple-apachegroovy: build.gradle"

    ```groovy
    plugins {
      id 'org.jetbrains.kotlin.jvm'
      id 'org.jetbrains.dokka'
      id 'com.gradleup.shadow'
    }

    tasks.register('extractShadowedSources', Sync) {
      from zipTree(tasks.named('shadowJar').flatMap { it.archiveSourcesFile })
      into layout.buildDirectory.dir('extracted-shadowed-sources')
    }

    dokka {
      dokkaSourceSets.configureEach {
        classpath.from(tasks.named('shadowJar').flatMap { it.archiveFile })
        sourceRoots.from(extractShadowedSources.map { it.destinationDir })
      }
    }
    ```

[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
[MavenPublication.artifact]: https://docs.gradle.org/current/dsl/org.gradle.api.publish.maven.MavenPublication.html#org.gradle.api.publish.maven.MavenPublication:artifact(java.lang.Object)
[ShadowJar]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
[ShadowJar.archiveSourcesFile]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/archive-sources-file.html
[ShadowJar.generateSourcesJar]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/generate-sources-jar.html
[ShadowJar.includedSourcesJars]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/included-sources-jars.html
[ShadowJar.relocate]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/relocate.html
[ShadowJar.sourceSetsSourceDirs]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/source-sets-source-dirs.html
[maven-publish]: https://docs.gradle.org/current/userguide/publishing_maven.html
[gradle-plugin-publish-docs]: https://docs.gradle.org/current/userguide/publishing_gradle_plugins.html#shadow_dependencies
[configuring-output-name]: ../configuration/README.md#configuring-output-name
[dokka]: https://kotlinlang.org/docs/dokka-introduction.html
