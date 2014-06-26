v1.0.0
======

+ Previously known as v0.9.0
+ All changes from 0.9.0-M1 to 0.9.0-M5

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