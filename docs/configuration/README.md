# Configuring Shadow

The [ShadowJar] task type extends from Gradle's [Jar] type.
This means that all attributes and methods available on [Jar] are also available on [ShadowJar].
Refer the _Gradle User Guide_ for [Jar] for details.

## Configuring Output Name

Shadow configures the default [ShadowJar] task to set the output JAR's

- [`archiveAppendix`](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:archiveAppendix)
- [`archiveBaseName`](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:archiveBaseName)
- [`archiveExtension`](https://docs.gradle.org/current/dsl/org.gradle.jvm.tasks.Jar.html#org.gradle.jvm.tasks.Jar:archiveExtension)
- [`archiveFile`](https://docs.gradle.org/current/dsl/org.gradle.jvm.tasks.Jar.html#org.gradle.jvm.tasks.Jar:archiveFile)
- [`archiveFileName`](https://docs.gradle.org/current/dsl/org.gradle.jvm.tasks.Jar.html#org.gradle.jvm.tasks.Jar:archiveFileName)
- [`archiveVersion`](https://docs.gradle.org/current/dsl/org.gradle.jvm.tasks.Jar.html#org.gradle.jvm.tasks.Jar:archiveVersion)
- [`destinationDirectory`](https://docs.gradle.org/current/dsl/org.gradle.jvm.tasks.Jar.html#org.gradle.jvm.tasks.Jar:destinationDirectory)

to the same default values as Gradle does for all [Jar] tasks.
Additionally, it configures the
[`archiveClassifier`](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:archiveClassifier)
to be `all`. The listed ones are not full, you can view all the properties in 
[Jar].
The output shadowed JAR file will be named with the following format:

```
archiveBaseName-$archiveAppendix-$archiveVersion-$archiveClassifier.$archiveExtension
```

If working with a Gradle project with the name `myApp` and version `1.0`, the default [ShadowJar] task will output a
file at: `build/libs/myApp-1.0-all.jar`. You can override the properties listed above to change the output name of the 
shadowed JAR file. e.g.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      archiveVersion = ""
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      archiveVersion = ''
    }
    ```

This will result in the output file being named `myApp-all.jar` instead of `myApp-1.0-all.jar`.


## Configuring the Runtime Classpath

Each Java JAR file contains a manifest file that provides metadata about the contents of the JAR file itself.
When using a shadowed JAR file as an executable JAR, it is assumed that all necessary runtime classes are contained
within the JAR itself.
There may be situations where the desire is to **not** bundle select dependencies into the shadowed JAR file, but
they are still required for runtime execution.

In these scenarios, Shadow creates a `shadow` configuration to declare these dependencies.
Dependencies added to the `shadow` configuration are **not** bundled into the output JAR.
Think of `configurations.shadow` as unmerged, runtime dependencies.
The integration with the `maven-publish` plugin will automatically configure dependencies added
to `configurations.shadow` as `RUNTIME` scope dependencies in the resulting POM file.

Additionally, Shadow automatically configures the manifest of the [ShadowJar] task to contain a `Class-Path` entry
in the JAR manifest.
The value of the `Class-Path` entry is the name of all dependencies resolved in the `shadow` configuration
for the project.

=== "Kotlin"

    ```kotlin
    dependencies {
      shadow("junit:junit:3.8.2")
    }
    ```

=== "Groovy"

    ```groovy
    dependencies {
      shadow 'junit:junit:3.8.2'
    }
    ```

Inspecting the `META-INF/MANIFEST.MF` entry in the JAR file will reveal the following attribute:

```property
Class-Path: junit-3.8.2.jar
```

When deploying a shadowed JAR as an execution JAR, it is important to note that any non-bundled runtime dependencies
**must** be deployed in the location specified in the `Class-Path` entry in the manifest.

## Configuring the JAR Manifest

Beyond the automatic configuration of the `Class-Path` entry, the [ShadowJar] manifest is configured in a number of ways.
First, the manifest for the [ShadowJar] task is configured to __inherit__ from the manifest of the standard [Jar] task.
This means that any configuration performed on the [Jar] task will propagate to the [ShadowJar] tasks.

=== "Kotlin"

    ```kotlin
    tasks.jar {
      manifest {
        attributes["Class-Path"] = "/libs/a.jar"
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('jar', Jar) {
      manifest {
        attributes 'Class-Path': '/libs/a.jar'
      }
    }
    ```

Inspecting the `META-INF/MANIFEST.MF` entry in the JAR file will reveal the following attribute:

```property
Class-Path: /libs/a.jar
```

If it is desired to inherit a manifest from a JAR task other than the standard [Jar] task, the `inheritFrom` methods
on the `shadowJar.manifest` object can be used to configure the upstream.

=== "Kotlin"

    ```kotlin
    val testJar by tasks.registering(Jar::class) {
      manifest {
        attributes["Description"] = "This is an application JAR"
      }
    }

    tasks.shadowJar {
      manifest.inheritFrom(testJar.get().manifest)
    }
    ```

=== "Groovy"

    ```groovy
    def testJar = tasks.register('testJar', Jar) {
      manifest {
        attributes 'Description': 'This is an application JAR'
      }
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      manifest.inheritFrom(testJar.get().manifest)
    }
    ```

## Adding Extra Files

The [ShadowJar] task is a subclass of the [Jar] task, which means that the 
[Jar.from](https://docs.gradle.org/current/dsl/org.gradle.jvm.tasks.Jar.html#org.gradle.jvm.tasks.Jar:from(java.lang.Object,%20org.gradle.api.Action)) 
method can be used to add extra files.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      from("Foo") {
        // Copy Foo file into Bar/ in the shadowed JAR.
        into("Bar")
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      from('Foo') {
        // Copy Foo file into Bar/ in the shadowed JAR.
        into('Bar')
      }
    }
    ```



[ShadowJar]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
