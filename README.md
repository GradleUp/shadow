# Gradle Shadow

Shadow is an extension of the Gradle Jar task that optimizes FatJar/UberJar creation by using JarInputStream and
JarOutputStream to copy file contents. This avoids the unnecessary I/O overhead of expanding jar files to disk
before recombining them. Shadow provides the similar filtering, relocation, and transformation capabilities as the
Maven Shade plugin. Starting with version 0.9, Shadow is a complete re-write based on core Gradle classes and concepts
instead of a port of the Maven Shade code. Documentation for version 0.8 and prior can be found [here](README_old.md)

## Current Status

In Progress: 0.9-SNAPSHOT

[![Build Status](https://drone.io/github.com/johnrengelman/shadow/status.png)](https://drone.io/github.com/johnrengelman/shadow/latest)


## QuickStart

### Applying Shadow Plugin to Project

```
buildscript {
  repositories { jcenter() }
  dependencies {
    classpath 'com.github.jengelman.gradle.plugins:shadow:0.9'
  }
}

apply plugin: 'shadow'
```


### Using the default plugin task

```
$ gradle shadowJar //shadow the runtime configuration with project code into ./build/libs/
```

`shadowJar` by uses the same default configurations as `jar` and additionally configures the `classifier` to be `"all"`.
Additionally, it creates a 'shadow' configuration and assigns the jar as an artifact of it. This configuration can
be used to add dependencies that are excluded from the shadowing.

## Advanced Configuration

### Filtering shadow jar contents by file pattern


TODO - need test case for filtering file patterns
```
shadowJar {
  exclude 'LICENSE'
}
```

### Filtering shadow jar contents by maven/project dependency

Remove an external dependency and all of its transitive dependencies

```
shadowJar {
  exclude(dependency('asm:asm:3.3.1'))
}
```

Remove an external dependency but keep its transitive dependencies

```
shadowJar {
  exclude(dependency('asm:asm:3.3.1'), false)
}
```

Exclude a project dependency in a multi-project build

```
shadowJar {
  exclude(project(":myclient"))
}
```

TODO need example of excluding a dependency but including one of its transitives

### Relocating dependencies

### Transforming resources

### Changing the Default configuration for shadow jar

TODO - need to implement this

### Publishing the shadow jar

### Configuring additional POM dependencies for Shadow Jar

## ChangeLog

[ChangeLog](ChangeLog.md)
