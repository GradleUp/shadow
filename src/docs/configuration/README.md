---
api: api/com/github/jengelman/gradle/plugins/shadow
---

# Configuring Shadow

The [`ShadowJar`](http://imperceptiblethoughts.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html) task type extends from Gradle's
[`Jar`](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html) type.
This means that all attributes and methods available on `Jar` are also available on
[`ShadowJar`](http://imperceptiblethoughts.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html).
Refer the _Gradle User Guide_ for [Jar](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html) for
details.

## Configuring Output Name

Shadow configures the default `shadowJar` task to set the output JAR's `destinationDirectory`, `archiveBaseName`, `appendix`,
`archiveVersion`, and `extension` to the same default values as Gradle does for all `Jar` tasks.
Additionally, it configures the `archiveClassifier` to be `all`.

If working with a Gradle project with the name `myApp` and archiveVersion `1.0`, the default `shadowJar` task will output a
file at: `build/libs/myApp-1.0-all.jar`

As with all `Jar` tasks in Gradle, these values can be overridden:

```groovy
// Output to build/libs/shadow.jar
shadowJar {
   archiveBaseName.set('shadow')
   archiveClassifier.set('')
   archiveVersion.set('')
}
```

## Configuring the Runtime Classpath

Each Java JAR file contains a manifest file that provides meta data about the contents of the JAR file itself.
When using a shadowed JAR file as an executable JAR, it is assumed that all necessary runtime classes are contained
within the JAR itself.
There may be situations where the desire is to **not** bundle select dependencies into the shadowed JAR file but
they are still required for runtime execution.

In these scenarios, Shadow creates a `shadow` configuration to declare these dependencies.
Dependencies added to the `shadow` configuration are **not** bundled into the output JAR.
Think of `configurations.shadow` as unmerged, runtime dependencies.
The integration with the `maven-publish` plugin will automatically configure dependencies added
to `configurations.shadow` as `RUNTIME` scope dependencies in the resulting POM file.

Additionally, Shadow automatically configures the manifest of the `shadowJar` task to contain a `Class-Path` entry
in the JAR manifest.
The value of the `Class-Path` entry is the name of all dependencies resolved in the `shadow` configuration
for the project.

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

Beyond the automatic configuration of the `Class-Path` entry, the `shadowJar` manifest is configured in a number of ways.
First, the manifest for the `shadowJar` task is configured to __inherit__ from the manifest of the standard `jar` task.
This means that any configuration performed on the `jar` task will propagate to the `shadowJar` tasks.

```groovy
jar {
   manifest {
       attributes 'Class-Path': '/libs/a.jar'
   }
}
```

Inspecting the `META-INF/MANIFEST.MF` entry in the JAR file will reveal the following attribute:

```property
Class-Path: /libs/a.jar
```

If it is desired to inherit a manifest from a JAR task other than the standard `jar` task, the `inheritFrom` methods
on the `shadowJar.manifest` object can be used to configure the upstream.

```groovy
task testJar(type: Jar) {
  manifest {
    attributes 'Description': 'This is an application JAR'
  }
}

shadowJar {
  manifest {
    inheritFrom(project.tasks.testJar.manifest)
  }
}
```
