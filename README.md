# Gradle Shadow

Shadow is an extension of the Gradle Jar task that optimizes FatJar/UberJar creation by using JarInputStream and
JarOutputStream to copy file contents. This avoids the unnecessary I/O overhead of expanding jar files to disk
before recombining them. Shadow provides the similar filtering, relocation, and transformation capabilities as the
Maven Shade plugin. Starting with version 0.9, Shadow is a complete re-write based on core Gradle classes and concepts
instead of a port of the Maven Shade code. Documentation for version 0.8 and prior can be found [here](README_old.md).

## Current Status

<a href='https://bintray.com/johnrengelman/gradle-plugins/gradle-shadow-plugin/view?source=watch' alt='Get automatic notifications about new "gradle-shadow-plugin" versions'><img src='https://www.bintray.com/docs/images/bintray_badge_color.png'></a>
[ ![Download](https://api.bintray.com/packages/johnrengelman/gradle-plugins/gradle-shadow-plugin/images/download.png) ](https://bintray.com/johnrengelman/gradle-plugins/gradle-shadow-plugin/_latestVersion)
[ ![Codeship Status for johnrengelman/shadow](https://codeship.io/projects/13678020-e2b0-0131-bc4c-4231d3e9af6a/status)](https://codeship.io/projects/25349)


## QuickStart

### Applying Shadow Plugin to Project

#### Gradle 1.x and 2.0

```
buildscript {
  repositories { jcenter() }
  dependencies {
    classpath 'com.github.jengelman.gradle.plugins:shadow:1.2.0'
  }
}

apply plugin: 'java' // or 'groovy'. Must be explicitly applied
apply plugin: 'com.github.johnrengelman.shadow'
```

#### Gradle 2.1 and higher

```
plugins {
  id 'java' // or 'groovy' Must be explicitly applied
  id 'com.github.johnrengelman.shadow' version '1.2.0'
}
```

Note: Applying the `ShadowPlugin` to a project applies the majority of its settings via a callback on the application of
other plugins. For example, the bulk of `shadow` is only added to the project if the `java` or `groovy` plugins are also
added. Shadow will **not** add them automatically, but instead listens for their application and responds.

### Using the default plugin task

```
$ gradle shadowJar //shadow the runtime configuration with project code into ./build/libs/
```

`shadowJar` uses the same default configurations as `jar` and additionally configures the `classifier` to be `'all'`.
Additionally, it creates a `'shadow'` configuration and assigns the jar as an artifact of it. This configuration can
be used to add dependencies that are excluded from the shadowing.

### Integrating with Application Plugin

```
apply plugin: 'application'
apply plugin: 'com.github.johnrengelman.shadow'
```

Applying both `shadow` and `application` to a project will create a number of additional tasks to be created. These
tasks mimic the `application` plugin but execute using the output of the `shadowJar` task.

Applying the `application` plugin will cause the `shadowJar` to include the `Main-Class` attribute in the manifest of
the `shadowJar` output. This is configured via the `mainClassName` attribute from the `application` plugin.

## Advanced Configuration

### Configure MANIFEST file

By default, shadowJar.manifest inherits from jar.manifest.

```
jar {
  manifest {
    attributes("Implementation-Title": "Gradle", "Implementation-Version": version)
  }
}
```

### Modifying the MANIFEST file

Append to the Jar MANIFEST. Values specified here, override the values in jar.manifest.

```
shadowJar {
  manifest {
    attributes 'Test-Entry': 'PASSED'
  }
}
```

### Merging Service files

```
shadowJar {
  mergeServiceFiles()
}
```

**OR**

```
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

shadowJar {
  transform(ServiceFileTransformer)
}
```

### Merging service files in a different directory

```
shadowJar {
  mergeServiceFiles('META-INF/griffon')
}
```

**OR**

```
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

shadowJar {
  transform(ServiceFileTransformer) {
    path = 'META-INF/griffon'
  }
}
```

### Merging service files specified by include and exclude patterns

```
shadowJar {
  mergeServiceFiles {
    exclude 'META-INF/services/com.acme.*'
  }
}
```

**OR**

```
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

shadowJar {
  transform(ServiceFileTransformer) {
    exclude 'META-INF/services/com.acme.*'
  }
}
```

### Merging Groovy extension modules

```
shadowJar {
  mergeGroovyExtensionModules()
}
```

**OR**

```
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer

shadowJar {
  transform(GroovyExtensionModuleTransformer)
}
```


### Appending Files

```
shadowJar {
  append('NOTICE')
}
```

**OR**

```
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer

shadowJar {
  transform(AppendingTransformer) {
    resource = 'NOTICE'
  }
}
```

### Filtering shadow jar contents by file pattern

```
shadowJar {
  exclude 'LICENSE'
}
```

### Filtering shadow jar contents by maven/project dependency

Exclude specific dependency (transitive dependencies are **not** excluded).

```
shadowJar {
  dependencies {
    exclude(dependency('asm:asm:3.3.1'))
  }
}
```

Include specific dependency (transitive dependencies are **not** included). Note that dependency inclusion is based
on the same core classes as Gradle's `CopySpec` inclusion/exclusion. By default, there is a global include, however,
declaring a specific `include` effectively creates a global `exclude`. That is, once an `include` is made, only items
that are specifically listed for inclusion will be include in the final output.

```
shadowJar {
  dependencies {
    include(dependency('asm:asm:3.3.1'))
  }
}
```

Exclude a project dependency in a multi-project build.

```
shadowJar {
  dependencies {
    exclude(project(":myclient"))
  }
}
```

### Relocating dependencies

```
shadowJar {
  relocate 'org.objectweb.asm', 'myjarjarasm.asm'
}
```

### Filtering files in relocation

```
shadowJar {
  relocate('org.objectweb.asm', 'myjarjarasm.asm') {
    exclude 'org.objectweb.asm.ClassReader'
  }
}
```

### Transforming resources

Uses the [Transformer](src/main/groovy/com/github/jengelman/gradle/plugins/shadow/transformers/Transformer.groovy) interface.

```
shadowJar {
  transform(<Transformer class>) {
    //..configure the Transformer class instance
  }
}
```

### Publishing the shadow jar as an additional resource to the main jar

```
apply plugin: 'com.github.johnrengelman.shadow'
apply plugin: 'maven-publish'

publishing {
  publications {
    shadow(MavenPublication) {
      from components.java
      artifact shadowJar
    }
  }
}
```

### Publishing the shadow jar as a standalone artifact

```
apply plugin: 'com.github.johnrengelman.shadow'
apply plugin: 'maven-publish'

shadowJar {
  baseName = 'myproject-all'
  classifier = ''
}

publishing {
  publications {
    shadow(MavenPublication) {
      from components.shadow
      artifactId = 'myproject-all'
    }
  }
}
```

OR

```
apply plugin: 'com.github.johnrengelman.shadow'
apply plugin: 'maven'

shadowJar {
  baseName = 'myproject-all'
  classifier = ''
}

uploadShadow {
  repositories {
    mavenDeployer {
      //configure maven deployment
    }
  }
}
```

*_NOTE_*: When using the `'maven'` plugin, the `'compile'` and `'runtime'` configurations are removed from the POM and
the `'shadow'` configuration is mapped as `'runtime'` scope. This is identical to the behavior with the
`'maven-publish'` plugin

### Configuring additional POM dependencies for Shadow Jar

```
dependencies {
  compile 'asm:asm:3.3.1'
  compile 'org.bouncycastle:bcprov-jdk15on:1.47'
  shadow 'org.bouncycastle:bcprov-jdk15on:1.47'
}

shadowJar {
  dependencies {
    exclude(dependency('org.bouncycastle:bcprov-jdk15on:1.47'))
  }
}
```

This examples allows the project to compile against the BouncyCastle encryption library, but then excludes it from
the shadowed jar, but including it as a dependency on the 'shadow' configuration.

Additionally, any dependencies added to the `shadow` configuration will be added to the `Class-Path` attribute in
the JAR Manifest for the output of `shadowJar`.

## ChangeLog

[ChangeLog](ChangeLog.md)
