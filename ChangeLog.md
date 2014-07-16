v1.0.3
======

+ Make service files root path configurable for `ServiceFileTransformer` ([Issue #72](https://github.com/johnrengelman/shadow/issues/72))

v1.0.2
======

+ Do not add an empty Class-Path attribute to the manifest when the `shadow` configuration contains no dependencies.
+ `runShadow` now registers `shadowJar` as an input. Previously, `runShadow` did not execute `shadowJar` and an error occurred.
+ Support Gradle 2.0 ([Issue #66](https://github.com/johnrengelman/shadow/issues/66))
+ Do not override existing 'Class-Path' Manifest attribute settings from Jar configuration. Instead combine. ([Issue #65](https://github.com/johnrengelman/shadow/issues/65))

v1.0.1
======

+ Fix issue where non-class files are dropped when using relocation ([Issue #58](https://github.com/johnrengelman/shadow/issues/58))
+ Do not create a / directory inside the output jar.
+ Fix `runShadow` task to evaluate the `shadowJar.archiveFile` property at execution time. ([Issue #60](https://github.com/johnrengelman/shadow/issues/60))

v1.0.0
======

+ Previously known as v0.9.0
+ All changes from 0.9.0-M1 to 0.9.0-M5
+ Properly configure the ShadowJar task inputs to observe the include/excludes from the `dependencies` block. This
  allows UP-TO-DATE checking to work properly when changing the `dependencies` rules ([Issue #54](https://github.com/johnrengelman/shadow/issues/54))
+ Apply relocation remappings to classes and imports in source project ([Issue #55](https://github.com/johnrengelman/shadow/issues/55))
+ Do not create directories in jar for source of remapped class, created directories in jar for destination of remapped classes ([Issue #53](https://github.com/johnrengelman/shadow/issues/53))

v0.9.0-M5
=========

+ Add commons-io to compile classpath
+ Update asm library to 4.1

v0.9.0-M4
=========

+ Break plugin into multiple sub-plugins. `ShadowBasePlugin` is always applied.
  `ShadowJavaPlugin` and `ShadowApplicationPlugin` are applied in reaction to applying the `java` and `application`
  plugins respectively.
+ Shadow does not applied `java` plugin automatically. `java` or `groovy` must be applied in conjunction with `shadow`.
+ Moved artifact filtering to `dependencies {}` block underneath `shadowJar`. This allows better include/exclude control
  for dependencies.
+ Dependencies added to the `shadow` configuration are automatically added to the `Class-Path` attribute in the manifest
  for `shadowJar`
+ Applying `application` plugin and settings `mainClassName` automatically configures the `Main-Class` attribute in
  the manifest for `shadowJar`
+ `runShadow` now utilizes the output of the `shadowJar` and executes using `java -jar <shadow jar file>`
+ Start Scripts for shadow distribution now utilize `java -jar` to execute instead of placing all files on classpath
  and executing main class.
+ Excluding/Including dependencies no longer includes transitive dependencies. All dependencies for inclusion/exclusion
  must be explicitly configured via a spec.

v0.9.0-M3
=========

+ Use commons.io FilenameUtils to determine name of resolved jars for including/excluding

v0.9.0-M2
=========

+ Added integration with `application` plugin to replace old `OutputSignedJars` task
+ Fixed bug that resulted in duplicate file entries in the resulting Jar
+ Changed plugin id to 'com.github.johnrengelman.shadow' to support Gradle 2.x plugin infrastructure.

v0.9.0-M1
=========

+ Rewrite based on Gradle Jar Task
+ `ShadowJar` now extends `Jar`
+ Removed `signedCompile` and `signedRuntime` configurations in favor of `shadow` configuration
+ Removed `OutputSignedJars` task

<= v0.8
=======

See [here](README_old.md)