# Configuring Shadow

The [`ShadowJar`][ShadowJar] task type extends from Gradle's [`Jar`][Jar] type.
This means that all attributes and methods available on [`Jar`][Jar] are also available on [`ShadowJar`][ShadowJar].

## ShadowJar Execution Flow

The following diagram illustrates how the `shadowJar` task processes inputs from dependency configurations
and source files to the final shadowed output JAR:

```mermaid
flowchart TD
    subgraph Inputs["1. Inputs & Configuration"]
        A1["Project Outputs & Extra Files<br/>(sourceSets.main.output / from(...))"]
        A2["Dependency Configurations<br/>(runtimeClasspath, etc.)"]
        A3["shadow Configuration<br/>(Unmerged runtime deps)"]
        A4["dependencies { include(...) / exclude(...) }"]
        A2 --> A4
        A4 -->|"includedDependencies"| A5["Dependency Artifacts"]
    end

    subgraph Preparation["2. Task Preparation (ShadowJar.copy)"]
        B1{"More Dependencies?<br/>(addIncludedDependencies)"}
        A5 --> B1
        B1 -->|"Yes"| B2{"Dependency File Type?"}
        B2 -->|"Missing"| B3["Log & Skip"]
        B2 -->|"Directory"| B4["from(dir)"]
        B2 -->|"AAR"| BFail["Fail Build<br/>(AAR not supported; use Fused Library)"]
        B2 -->|"Other File"| BArchive["from(zipTree(file))"]
        B3 & B4 & BArchive --> B1
        B5["injectManifestAttributes()<br/>• Main-Class<br/>• Class-Path (from shadow)<br/>• Multi-Release flag"]
        B1 -->|"No / Empty"| B5
        A3 -.-> B5
        B5 --> B6["super.copy()"]
    end

    subgraph CopySpecProcessing["3. Gradle Copy Engine & Duplicate Handling"]
        PreStream["createCopyAction()<br/>• findUnusedClasses()<br/>• createZipOutputStream()"]
        B6 --> PreStream
        ExecuteStream["ShadowCopyAction.execute()<br/>• zipOutStream.use { ... }<br/>• stream.process entries"]
        PreStream --> ExecuteStream
        A1 -.->|"Configured Specs"| ExecuteStream

        EntryLoop{"More Entries?"}
        ExecuteStream --> EntryLoop
        EntryLoop -->|"Yes"| C1{"Included by Patterns?<br/>(include / exclude)"}
        EntryLoop -->|"No"| F1
        C1 -->|"No"| EntryLoop
        C1 -->|"Yes"| C2{"Path Already Seen?"}
        C2 -->|"No"| D1{"Entry Type?"}
        C2 -->|"Yes"| CStrategy{"Effective<br/>duplicatesStrategy?"}
        CStrategy -->|"EXCLUDE (default)"| EntryLoop
        CStrategy -->|"INCLUDE"| D1
        CStrategy -->|"WARN"| CWarn["Log Warning"]
        CWarn --> D1
        CStrategy -->|"FAIL / unresolved INHERIT"| F4Close
    end

    subgraph StreamAction["4. Stream Processing (ShadowCopyAction)"]
        subgraph ClassBranch["Class Files (*.class)"]
            D2{"In pre-computed<br/>unusedClasses?"}
            D2 -->|"Yes"| D3["Drop Unused Class"]
            D2 -->|"No"| D4{"Relocators configured?"}
            D4 -->|"No"| D5["Write Original Bytes to ZIP"]
            D4 -->|"Yes"| D6["ASM Bytecode Remap<br/>& Relocate Class Path<br/>(handle META-INF/versions/)"]
            D6 --> D7["Write Relocated Entry to ZIP"]
        end

        subgraph ResourceBranch["Resource Files & Others"]
            E1["Compute Relocated Path<br/>(relocators.relocatePath)"]
            E1 --> E2{"Matched by<br/>ResourceTransformer?<br/>(canTransformResource using fileDetails)"}
            E2 -->|"Yes"| E3["Accumulate in Transformer<br/>(receives pre-computed relocated path)"]
            E2 -->|"No"| E4["Write Entry to ZIP<br/>(using pre-computed relocated path)"]
        end

        D1 -->|"*.class"| D2
        D1 -->|"Resource"| E1
        D1 -->|"Directory"| D8["Record in visitedDirs<br/>(timestamp & permissions)"]

        D3 & D5 & D7 & E3 & E4 & D8 --> EntryLoop
    end

    subgraph Finalization["5. Output Finalization & Post-Processing"]
        F1["processTransformers()<br/>Flush transformed/merged resources to ZIP"]
        F2["addDirs()<br/>Generate parent directory entries"]
        F1 --> F2
        D8 -.->|"Directory Metadata"| F2
        F3{"Duplicates found in ZIP?<br/>(checkDuplicateEntries)"}
        F2 --> F3
        F3 -->|"Duplicates found & failOnDuplicateEntries = true"| F4Close["Close ZIP Stream"]
        F4Close --> F4Delete["Delete Intermediate ZIP"] --> F4Fail["Fail Build"]
        F3 -->|"Duplicates found & failOnDuplicateEntries = false (default)"| F4Warn["Log Warning"]
        F3 -->|"No duplicates"| F5["Close Intermediate ZIP"]
        F4Warn --> F5

        F6{"Minimize Tool == R8?"}
        F5 --> F6
        F6 -->|"Yes"| F7["runR8Minimization()<br/>R8 shrinking (and optional optimization/obfuscation)"]
        F7 --> F8["Final Shadowed JAR"]
        F6 -->|"No"| F8
    end

    click A2 href "../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/configurations.html" "ShadowJar.configurations"
    click A3 href "../api/shadow/com.github.jengelman.gradle.plugins.shadow/-shadow-base-plugin/-companion/-c-o-n-f-i-g-u-r-a-t-i-o-n_-n-a-m-e.html" "ShadowBasePlugin.CONFIGURATION_NAME"
    click A4 href "../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/dependencies.html" "ShadowJar.dependencies"
    click A5 href "../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/included-dependencies.html" "ShadowJar.includedDependencies"
    click B5 href "../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/add-multi-release-attribute.html" "ShadowJar.addMultiReleaseAttribute"
    click CStrategy href "../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/get-duplicates-strategy.html" "ShadowJar.duplicatesStrategy"
    click D2 href "../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/minimize.html" "ShadowJar.minimize"
    click D6 href "../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/relocators.html" "ShadowJar.relocators"
    click E1 href "../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/relocators.html" "ShadowJar.relocators"
    click E2 href "../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/transformers.html" "ShadowJar.transformers"
    click F1 href "../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/transformers.html" "ShadowJar.transformers"
    click F3 href "../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/fail-on-duplicate-entries.html" "ShadowJar.failOnDuplicateEntries"
    click F7 href "../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-r8-spec/index.html" "R8Spec"
```

See also [Handling Duplicates Strategy][handling-duplicates-strategy] for best practices on combining
`duplicatesStrategy` and resource transformers.

## Configuring Output Name

Shadow configures the default [`ShadowJar`][ShadowJar] task to set the output JAR's

- [`archiveAppendix`][archiveAppendix]
- [`archiveBaseName`][archiveBaseName]
- [`archiveExtension`][archiveExtension]
- [`archiveFile`][archiveFile]
- [`archiveFileName`][archiveFileName]
- [`archiveVersion`][archiveVersion]
- [`destinationDirectory`][destinationDirectory]

to the same default values as Gradle does for all [`Jar`][Jar] tasks. Additionally, it configures the
[`archiveClassifier`][archiveClassifier] to be `all`. The listed ones are not full, you can view all the properties in
[`Jar`][Jar]. The output shadowed JAR file will be named with the following format:

```
archiveBaseName-$archiveAppendix-$archiveVersion-$archiveClassifier.$archiveExtension
```

If working with a Gradle project with the name `myApp` and version `1.0`, the default [`ShadowJar`][ShadowJar] task will
output a file at: `build/libs/myApp-1.0-all.jar`. You can override the properties listed above to change the output name
of the shadowed JAR file. e.g.

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
The integration with the [`maven-publish`][maven-publish] plugin will automatically configure dependencies added
to `configurations.shadow` as `RUNTIME` scope dependencies in the resulting POM file.

Additionally, Shadow automatically configures the manifest of the [`ShadowJar`][ShadowJar] task to contain a
`Class-Path` entry in the JAR manifest.
The value of the `Class-Path` entry is the name of all dependencies resolved in the `shadow` configuration for the
project.

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

!!! important

    When deploying a shadowed JAR as an execution JAR, any non-bundled runtime dependencies **must** be deployed in the
    location specified in the `Class-Path` entry in the manifest.

## Configuring the JAR Manifest

The [`ShadowJar`][ShadowJar] manifest is configured in a number of ways. First, the manifest for the `shadowJar` task
is configured to __inherit__ from the manifest of the standard `jar` task.

=== "Kotlin"

    ```kotlin
    tasks.jar {
      manifest {
        attributes["Main-Class"] = "my.Main"
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('jar', Jar) {
      manifest {
        attributes 'Main-Class': 'my.Main'
      }
    }
    ```

Inspecting the `META-INF/MANIFEST.MF` entry in the JAR files will reveal the following attribute:

```property
Main-Class: my.Main
```

If it is desired to merge a manifest from another [`Jar`][Jar] task, the `manifest.from` methods can be used to
configure the upstream.

=== "Kotlin"

    ```kotlin
    val testJar = tasks.register<Jar>("testJar") {
      manifest {
        attributes["Description"] = "This is an application JAR"
      }
    }

    tasks.shadowJar {
      manifest.from(testJar.get().manifest)
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
      manifest.from testJar.get().manifest
    }
    ```

## Adding Multi-Release Manifest Attribute

The [`ShadowJar`][ShadowJar] task can automatically add the `Multi-Release` attribute to the JAR manifest if any of
the included dependencies contain this attribute. This is controlled by the `addMultiReleaseAttribute` property.

By default, `addMultiReleaseAttribute` is set to `true`. When enabled, Shadow will scan all dependencies being merged
into the shadow JAR. If any dependency JAR has the `Multi-Release` manifest attribute set to `true`, Shadow will add
`Multi-Release: true` to the manifest of the resulting shadow JAR.

You can disable this behavior by setting `addMultiReleaseAttribute` to `false`:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      addMultiReleaseAttribute = false
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      addMultiReleaseAttribute = false
    }
    ```

This is useful if you want to control the presence of the `Multi-Release` attribute manually or avoid inheriting it
from dependencies.

## Adding Extra Files

The [`ShadowJar`][ShadowJar] task is a subclass of the [`Jar`][Jar] task, which means that the[`Jar.from`][Jar.from]
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

See also [Embedding Local Jar Files Into Your Shadowed Jar][embedding-local-jar-files-into-your-shadowed-jar].


[Jar.from]: https://docs.gradle.org/current/dsl/org.gradle.jvm.tasks.Jar.html#org.gradle.jvm.tasks.Jar:from(java.lang.Object,%20org.gradle.api.Action)
[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
[ShadowJar]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
[application]: https://docs.gradle.org/current/userguide/application_plugin.html
[archiveAppendix]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:archiveAppendix
[archiveBaseName]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:archiveBaseName
[archiveClassifier]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:archiveClassifier
[archiveExtension]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:archiveExtension
[archiveFileName]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:archiveFileName
[archiveFile]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:archiveFile
[archiveVersion]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:archiveVersion
[destinationDirectory]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:destinationDirectory
[embedding-local-jar-files-into-your-shadowed-jar]: dependencies/README.md#embedding-local-jar-files-into-your-shadowed-jar
[handling-duplicates-strategy]: merging/README.md#handling-duplicates-strategy
[maven-publish]: https://docs.gradle.org/current/userguide/publishing_maven.html
