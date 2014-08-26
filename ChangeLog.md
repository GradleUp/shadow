v1.1.0
======

+ (Breaking Change!) Fix leaking of `shadowJar.manifest` into `jar.manifest`. ([Issue #82](https://github.com/johnrengelman/shadow/issues/82))
  To simplify behavior, the `shadowJar.appendManifest` method has been removed. Replace uses with `shadowJar.manifest`
+ `ShadowTask` now has a `configurations` property that is resolved to the files in the resolved configuration before
  being added to the copy spec. This allows for an easier implementation for filtering. The default 'shadowJar' task
  has the convention of adding the `'runtime'` scope to this list. Manually created instances of `ShadowTask` have no
  configurations added by default and can be configured by setting `task.configurations`.
+ Properly configure integration with the `'maven'` plugin when added. When adding `'maven'` the `'uploadShadow'` task
  will now properly configure the POM dependencies by removing the `'compile'` and `'runtime'` configurations from the
  POM and adding the `'shadow'` configuration as a `RUNTIME` scope in the POM. This behavior matches the behavior when
  using the `'maven-publish'` plugin.
+ [Matt Hurne](https://github.com/mhurne) - Allow `ServiceFileTransformer` to specify include/exclude patterns for
  files within the configured path to merge.
+ [Matt Hurne](https://github.com/mhurne) - Added `GroovyExtensionModuleTransformer` for merging Groovy Extension module
  descriptor files. The existing `ServiceFileTransformer` now excludes Groovy Extension Module descriptors by default.
+ `distShadowZip` and `distShadowZip` now contain the shadow library and run scripts instead of the default from the `'application'` plugin ([Issue #89](https://github.com/johnrengelman/shadow/issues/89))

v1.0.3
======

+ Make service files root path configurable for `ServiceFileTransformer` ([Issue #72](https://github.com/johnrengelman/shadow/issues/72))
+ [Andres Almiray](https://github.com/aalmiray) - Added PropertiesFileTransformer ([Issue #73](https://github.com/johnrengelman/shadow/issues/73))
+ [Brandon Kearby](https://github.com/brandonkearby) - Fixed StackOverflow when a cycle occurs in the resolved dependency graph ([Issue #69](https://github.com/johnrengelman/shadow/pull/69))
+ Apply Transformers to project resources ([Issue #70](https://github.com/johnrengelman/shadow/issues/70), [Issue #71](https://github.com/johnrengelman/shadow/issues/71))
+ Do not drop non-class files from dependencies when relocation is enabled. Thanks to [Minecrell](https://github.com/Minecrell) for digging into this. ([Issue #61](https://github.com/johnrengelman/shadow/issues/61))
+ Remove support for applying individual sub-plugins by Id (easier maintenance and cleaner presentation in Gradle Portal)

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