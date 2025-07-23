# Change Log

## [Unreleased](https://github.com/GradleUp/shadow/compare/9.0.0-rc1...HEAD) - 2025-xx-xx

**Added**

- Support skipping string constant remapping. ([#1401](https://github.com/GradleUp/shadow/pull/1401))
- Let `assemble` depend on `shadowJar`. ([#1524](https://github.com/GradleUp/shadow/pull/1524))
- Fail build when inputting AAR files or using Shadow with AGP. ([#1530](https://github.com/GradleUp/shadow/pull/1530))

**Fixed**

- Honor `options.release` for target JVM attribute. ([#1502](https://github.com/GradleUp/shadow/pull/1502))

**Changed**
- Restore Develocity Build Scan integration. ([#1505](https://github.com/GradleUp/shadow/pull/1505))  
  It is still disabled by default, you can enable it by setting `com.gradleup.shadow.enableDevelocityIntegration = true`.
- Expose `AbstractDependencyFilter` from `internal` to `public`. ([#1538](https://github.com/GradleUp/shadow/pull/1538))  
  You can access it via `com.github.jengelman.gradle.plugins.shadow.tasks.DependencyFilter.AbstractDependencyFilter`.

## [9.0.0-rc1](https://github.com/GradleUp/shadow/releases/tag/9.0.0-rc1) - 2025-07-02

> This release is a major update from the 8.3.x series. The plugin has been fully rewritten in Kotlin, bringing
significant improvements to maintainability, performance, and future extensibility. It introduces many new features,
enhancements, and bug fixes, and includes several breaking changes. Please review the changelog carefully and consult
the [new doc site](https://gradleup.com/shadow/) before upgrading.

**BREAKING**

- Rewrite this plugin in Kotlin. ([#1012](https://github.com/GradleUp/shadow/pull/1012))
  Some APIs are marked as `internal`, and there are serial ABI changes.
- Remove Develocity integration. ([#1014](https://github.com/GradleUp/shadow/pull/1014))
- Migrate `Transformer`s to using lazy properties. ([#1036](https://github.com/GradleUp/shadow/pull/1036))
- Migrate `ShadowJar` to using lazy properties. `isEnableRelocation` is removed, use `enableRelocation` instead. ([#1044](https://github.com/GradleUp/shadow/pull/1044))
- Resolve `Configuration` directly in `DependencyFilter`. ([#1045](https://github.com/GradleUp/shadow/pull/1045))
- Some public getters are removed from `SimpleRelocator`, `includes` and `excludes` are exposed as `SetProperty`s. ([#1079](https://github.com/GradleUp/shadow/pull/1079))
- Migrate all `ListProperty` usages to `SetProperty`. Some public `List` parameters are also changed to `Set`. ([#1103](https://github.com/GradleUp/shadow/pull/1103))
- Remove `JavaJarExec`, now use `JavaExec` directly for `runShadow` task. ([#1197](https://github.com/GradleUp/shadow/pull/1197))
- `ServiceFileTransformer.ServiceStream` has been removed. ([#1218](https://github.com/GradleUp/shadow/pull/1218))
- Mark `RelocatorRemapper` as `internal`. ([#1227](https://github.com/GradleUp/shadow/pull/1227))
- Remove `KnowsTask` as it's useless. ([#1236](https://github.com/GradleUp/shadow/pull/1236))
- Remove `TransformerContext.getEntryTimestamp`. ([#1245](https://github.com/GradleUp/shadow/pull/1245))
- Move tracking unused classes logic out of `ShadowCopyAction`. ([#1257](https://github.com/GradleUp/shadow/pull/1257))
- Remove `BaseStreamAction`. ([#1258](https://github.com/GradleUp/shadow/pull/1258))
- Remove `ShadowStats`. ([#1264](https://github.com/GradleUp/shadow/pull/1264))
- Move `DependencyFilter` from `com.github.jengelman.gradle.plugins.shadow.internal` into `com.github.jengelman.gradle.plugins.shadow.tasks`. ([#1272](https://github.com/GradleUp/shadow/pull/1272))
- Rename `Transformer` to `ResourceTransformer`. Aims to better align with the name of `org.apache.maven.plugins.shade.resource.ResourceTransformer.java` and to distinguish itself from `org.gradle.api.Transformer.java`. ([#1288](https://github.com/GradleUp/shadow/pull/1288))
- Mark `DefaultInheritManifest` as `internal`. ([#1303](https://github.com/GradleUp/shadow/pull/1303))
- Polish `ShadowSpec`. Return values of `ShadowSpec` functions are changed to `Unit` to avoid confusion. `ShadowSpec` no longer extends `CopySpec`. Overload `relocate`, `transform` and things for better usability in Kotlin. ([#1307](https://github.com/GradleUp/shadow/pull/1307))
- Remove redundant types from function returning. ([#1308](https://github.com/GradleUp/shadow/pull/1308))
- Reduce dependency and project overloads in `DependencyFilter`. ([#1328](https://github.com/GradleUp/shadow/pull/1328))
- Align the behavior of `ShadowTask.from` with Gradle's `AbstractCopyTask.from`. In the previous versions, `ShadowTask.from` would always unzip the files before processing them, which caused serial issues that are hard to fix. Now it behaves like Gradle's `AbstractCopyTask.from`, which means it will not unzip the files, only copy the files as-is. ([#1233](https://github.com/GradleUp/shadow/pull/1233))
- Remove `ShadowCopyAction.ArchiveFileTreeElement` and `RelativeArchivePath`. ([#1233](https://github.com/GradleUp/shadow/pull/1233))

**Added**

- Add .md support to the Apache License and Notice transformers. ([#1041](https://github.com/GradleUp/shadow/pull/1041))
- Sync `SimpleRelocator` changes from maven-shade-plugin. ([#1076](https://github.com/GradleUp/shadow/pull/1076))
- Exclude `module-info.class` in Multi-Release folders by default. ([#1177](https://github.com/GradleUp/shadow/pull/1177))
- Inject `TargetJvmVersion` attribute for Gradle Module Metadata. ([#1199](https://github.com/GradleUp/shadow/pull/1199))
- Support Java 24. ([#1222](https://github.com/GradleUp/shadow/pull/1222))
- Sync `ShadowApplicationPlugin` with `ApplicationPlugin`. ([#1224](https://github.com/GradleUp/shadow/pull/1224))
- Inject `Multi-Release` manifest attribute if any dependency contains it. ([#1239](https://github.com/GradleUp/shadow/pull/1239))
- Mark `Transformer` as throwing `IOException`. ([#1248](https://github.com/GradleUp/shadow/pull/1248))
- Compat Kotlin Multiplatform plugin. You still need to manually configure `manifest.attributes` (e.g. `Main-Class` attr) in the `shadowJar` task if necessary. ([#1280](https://github.com/GradleUp/shadow/pull/1280))
- Add Kotlin DSL examples in docs. ([#1306](https://github.com/GradleUp/shadow/pull/1306))
- Support using type-safe dependency accessors in `ShadowJar.dependencies`. ([#1322](https://github.com/GradleUp/shadow/pull/1322))
- Set `Main-Class` attr for KMP 2.1.0 or above. ([#1337](https://github.com/GradleUp/shadow/pull/1337))
- Support command line options for `ShadowJar`. ([#1365](https://github.com/GradleUp/shadow/pull/1365))

**Changed**

- Exclude kotlin-stdlib from plugin dependencies. ([#1093](https://github.com/GradleUp/shadow/pull/1093))
- Replace deprecated `SelfResolvingDependency` with `FileCollectionDependency`. ([#1114](https://github.com/GradleUp/shadow/pull/1114))
- Support configuring `separator` in `AppendingTransformer`. This is useful for handling files like `resources/application.yml`. ([#1169](https://github.com/GradleUp/shadow/pull/1169))
- Update start script templates. ([#1183](https://github.com/GradleUp/shadow/pull/1183)) ([#1419](https://github.com/GradleUp/shadow/pull/1419))
- Mark `ShadowJar.dependencyFilter` as `@Input`. `ShadowSpec.stats` is removed and `ShadowJar.stats` is `internal` for now. ([#1206](https://github.com/GradleUp/shadow/pull/1206))
- Mark more `Transformer`s cacheable. ([#1210](https://github.com/GradleUp/shadow/pull/1210))
- Polish `startShadowScripts` task registering. ([#1216](https://github.com/GradleUp/shadow/pull/1216))
- Bump min Java requirement to 11. ([#1242](https://github.com/GradleUp/shadow/pull/1242))
- Reduce duplicated `SimpleRelocator` to improve performance. ([#1271](https://github.com/GradleUp/shadow/pull/1271))
- Migrate doc sites to MkDocs. ([#1302](https://github.com/GradleUp/shadow/pull/1302))
- `runShadow` no longer depends on `installShadowDist`. ([#1353](https://github.com/GradleUp/shadow/pull/1353))
- Move the group of `ShadowJar` from `shadow` to `build`. ([#1355](https://github.com/GradleUp/shadow/pull/1355))
- Set `Main-Class` attr for KMP 1.9.0 or above. ([#1410](https://github.com/GradleUp/shadow/pull/1410))
- In-development snapshots are now published to the Central Portal Snapshots repository at https://central.sonatype.com/repository/maven-snapshots/. ([#1414](https://github.com/GradleUp/shadow/pull/1414))
- Update ASM to 9.8 to support Java 25. ([#1380](https://github.com/GradleUp/shadow/pull/1380))
- Refactor file visiting logic in `StreamAction`, handle file unzipping via `Project.zipTree`. ([#1233](https://github.com/GradleUp/shadow/pull/1233))
- Don't re-add suppressed Gradle API to `compileOnly` configuration. ([#1422](https://github.com/GradleUp/shadow/pull/1422))
- Bump the min Gradle requirement to 8.11. ([#1479](https://github.com/GradleUp/shadow/pull/1479))
- Expose Ant as `compile` scope. ([#1488](https://github.com/GradleUp/shadow/pull/1488))

**Fixed**

- Fix single Log4j2Plugins.dat isn't included into fat jar. ([#1039](https://github.com/GradleUp/shadow/issues/1039))
- Adjust property initializations and modifiers in `ShadowJar`. This fixes the regression for registering custom `ShadowJar` tasks. ([#1090](https://github.com/GradleUp/shadow/pull/1090))
- Fail builds if processing bad jars. ([#1146](https://github.com/GradleUp/shadow/pull/1146))
- Fix `Log4j2PluginsCacheFileTransformer` not working for merging `Log4j2Plugins.dat` files. ([#1175](https://github.com/GradleUp/shadow/pull/1175))
- Support overriding `mainClass` provided by `JavaApplication`. ([#1182](https://github.com/GradleUp/shadow/pull/1182))
- Fix `ShadowJar` not being successful after `includes` or `excludes` are changed. ([#1200](https://github.com/GradleUp/shadow/pull/1200))
- Fix the last modified time of shadowed directories. ([#1277](https://github.com/GradleUp/shadow/pull/1277))
- Fix relocation exclusion for file patterns like `kotlin/kotlin.kotlin_builtins`. ([#1313](https://github.com/GradleUp/shadow/pull/1313))
- Avoid creating jvm targets eagerly for KMP. ([#1378](https://github.com/GradleUp/shadow/pull/1378))
- Allow using file trees of JARs together with the configuration cache. ([#1441](https://github.com/GradleUp/shadow/pull/1441))
- Fallback `RelocateClassContext` and `RelocatePathContext` to data classes. ([#1445](https://github.com/GradleUp/shadow/pull/1445))
- Pin the plugin's Kotlin language level on 2.0. The language level used in `9.0.0-beta14` is 2.2, which may cause compatibility issues for the plugins depending on Shadow. ([#1448](https://github.com/GradleUp/shadow/pull/1448))
- Fix compatibility for Gradle 9.0.0 RC1. ([#1468](https://github.com/GradleUp/shadow/pull/1468))
- Honor `DuplicatesStrategy`. Shadow recognized `DuplicatesStrategy.EXCLUDE` as the default, but the other strategies didn't work properly. Now we honor `DuplicatesStrategy.INCLUDE` as the default, and align all the strategy behaviors with the Gradle side. ([#1233](https://github.com/GradleUp/shadow/pull/1233))
- Honor unzipped jars via `from`. ([#1233](https://github.com/GradleUp/shadow/pull/1233))

## [9.0.0-beta17](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta17) - 2025-06-18

**Fixed**

- Fix compatibility for Gradle 9.0.0 RC1. ([#1468](https://github.com/GradleUp/shadow/pull/1468))

## [9.0.0-beta16](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta16) - 2025-06-14

**Changed**

- Update ASM to 9.8 to support Java 25. ([#1380](https://github.com/GradleUp/shadow/pull/1380))

**Fixed**

- Restore removed `Named` from `ResourceTransformer`. ([#1449](https://github.com/GradleUp/shadow/pull/1449))

## [9.0.0-beta15](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta15) - 2025-05-28

**Fixed**

- Pin the plugin's Kotlin language level on 2.0. ([#1448](https://github.com/GradleUp/shadow/pull/1448))  
  The language level used in `9.0.0-beta14` is 2.2, which may cause compatibility issues for the plugins depending on
  Shadow.

## [9.0.0-beta14](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta14) - 2025-05-28

**Changed**

- Update start script templates. ([#1419](https://github.com/GradleUp/shadow/pull/1419))
- In-development snapshots are now published to the Central Portal Snapshots repository at https://central.sonatype.com/repository/maven-snapshots/. ([#1414](https://github.com/GradleUp/shadow/pull/1414))

**Fixed**

- Allow using file trees of JARs together with the configuration cache. ([#1441](https://github.com/GradleUp/shadow/pull/1441))
- Fallback `RelocateClassContext` and `RelocatePathContext` to data classes. ([#1445](https://github.com/GradleUp/shadow/pull/1445))

## [9.0.0-beta13](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta13) - 2025-04-29

**Changed**

- Set `Main-Class` attr for KMP 1.9.0 or above. ([#1410](https://github.com/GradleUp/shadow/pull/1410))

**Fixed**

- Avoid creating jvm targets eagerly for KMP. ([#1378](https://github.com/GradleUp/shadow/pull/1378))

## [9.0.0-beta12](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta12) - 2025-04-01

**Added**

- Support command line options for `ShadowJar`. ([#1365](https://github.com/GradleUp/shadow/pull/1365))

```
Options:

--enable-relocation       Enable relocation of packages in the jar
--no-enable-relocation    Disables option --enable-relocation
--minimize-jar            Minimize the jar by removing unused classes
--no-minimize-jar         Disables option --minimize-jar
--relocation-prefix       Prefix to use for relocated packages
--rerun                   Causes the task to be re-run even if up-to-date
```

**Changed**

- Move the group of `ShadowJar` from `shadow` to `build`. ([#1355](https://github.com/GradleUp/shadow/pull/1355))

## [9.0.0-beta11](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta11) - 2025-03-18

**Added**

- Add Kotlin DSL examples in docs. ([#1306](https://github.com/GradleUp/shadow/pull/1306))
- Support using type-safe dependency accessors in
  `ShadowJar.dependencies`. ([#1322](https://github.com/GradleUp/shadow/pull/1322))
- Set `Main-Class` attr for KMP 2.1.0 or above. ([#1337](https://github.com/GradleUp/shadow/pull/1337))

**Changed**

- **BREAKING CHANGE:** Polish `ShadowSpec`. ([#1307](https://github.com/GradleUp/shadow/pull/1307))
  - Return values of `ShadowSpec` functions are changed to `Unit` to avoid confusion.
  - `ShadowSpec` no longer extends `CopySpec`.
  - Overload `relocate`, `transform` and things for better usability in Kotlin.
- **BREAKING CHANGE:** Remove redundant types from function
  returning. ([#1308](https://github.com/GradleUp/shadow/pull/1308))
- `runShadow` no longer depends on `installShadowDist`. ([#1353](https://github.com/GradleUp/shadow/pull/1353))

**Fixed**

- Fix relocation exclusion for file patterns like
  `kotlin/kotlin.kotlin_builtins`. ([#1313](https://github.com/GradleUp/shadow/pull/1313))

**Removed**

- **BREAKING CHANGE:** Reduce dependency and project overloads in
  `DependencyFilter`. ([#1328](https://github.com/GradleUp/shadow/pull/1328))

## [9.0.0-beta10](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta10) - 2025-03-05

**Added**

- Compat Kotlin Multiplatform plugin. ([#1280](https://github.com/GradleUp/shadow/pull/1280))  
  You still need to manually configure `manifest.attributes` (e.g. `Main-Class` attr) in the `shadowJar` task if
  necessary.

**Changed**

- **BREAKING CHANGE:** Rename `Transformer` to
  `ResourceTransformer`. ([#1288](https://github.com/GradleUp/shadow/pull/1288))  
  Aims to better align with the name
  of [org.apache.maven.plugins.shade.resource.ResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/ResourceTransformer.java)
  and to distinguish itself
  from [org.gradle.api.Transformer.java](https://docs.gradle.org/current/javadoc/org/gradle/api/Transformer.html).
- **BREAKING CHANGE:** Mark `DefaultInheritManifest` as
  `internal`. ([#1303](https://github.com/GradleUp/shadow/pull/1303))
- Migrate doc sites to MkDocs. ([#1302](https://github.com/GradleUp/shadow/pull/1302))

**Fixed**

- Fix the last modified time of shadowed directories. ([#1277](https://github.com/GradleUp/shadow/pull/1277))

**Removed**

- **BREAKING CHANGE:** Remove `Named` from the parents of
  `Transformer`. ([#1289](https://github.com/GradleUp/shadow/pull/1289))

## [9.0.0-beta9](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta9) - 2025-02-24

**Added**

- Mark `Transformer` as throwing `IOException`. ([#1248](https://github.com/GradleUp/shadow/pull/1248))

**Changed**

- **BREAKING CHANGE:** Move tracking unused classes logic out of
  `ShadowCopyAction`. ([#1257](https://github.com/GradleUp/shadow/pull/1257))
- Reduce duplicated `SimpleRelocator` to improve performance. ([#1271](https://github.com/GradleUp/shadow/pull/1271))
- **BREAKING CHANGE:** Move `DependencyFilter` from `com.github.jengelman.gradle.plugins.shadow.internal` into
  `com.github.jengelman.gradle.plugins.shadow.tasks`. ([#1272](https://github.com/GradleUp/shadow/pull/1272))
- **BREAKING CHANGE:** Align the behavior of `ShadowTask.from` with Gradle's `AbstractCopyTask.from`. ([#1233](https://github.com/GradleUp/shadow/pull/1233))  
  In the previous versions, `ShadowTask.from` would always unzip the files before processing them, which caused serial
  issues that are hard to fix. Now it behaves like Gradle's `AbstractCopyTask.from`, which means it will not unzip
  the files, only copy the files as-is. If you still want to shadow the unzipped files, try out something like:
  ```kotlin
    tasks.shadowJar {
      from(zipTree(files('path/to/your/file.zip')))
    }
  ```
  or
  ```kotlin
    dependencies {
      implementation(files('path/to/your/file.zip'))
    }
  ```
- Refactor file visiting logic in `StreamAction`, handle file unzipping via
  `Project.zipTree`. ([#1233](https://github.com/GradleUp/shadow/pull/1233))

**Fixed**

- Honor `DuplicatesStrategy`. ([#1233](https://github.com/GradleUp/shadow/pull/1233))  
  Shadow recognized `DuplicatesStrategy.EXCLUDE` as the default, but the other strategies didn't work properly.
  Now we honor `DuplicatesStrategy.INCLUDE` as the default, and align all the strategy behaviors with the Gradle side.
- Honor unzipped jars via `from`. ([#1233](https://github.com/GradleUp/shadow/pull/1233))

**Removed**

- **BREAKING CHANGE:** Remove `BaseStreamAction`. ([#1258](https://github.com/GradleUp/shadow/pull/1258))
- **BREAKING CHANGE:** Remove `ShadowStats`. ([#1264](https://github.com/GradleUp/shadow/pull/1264))
- **BREAKING CHANGE:** Remove `ShadowCopyAction.ArchiveFileTreeElement` and
  `RelativeArchivePath`. ([#1233](https://github.com/GradleUp/shadow/pull/1233))
- **BREAKING CHANGE:** Remove `TransformerContext.getEntryTimestamp`. ([#1245](https://github.com/GradleUp/shadow/pull/1245))

## [9.0.0-beta8](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta8) - 2025-02-08

**Added**

- Sync `ShadowApplicationPlugin` with `ApplicationPlugin`. ([#1224](https://github.com/GradleUp/shadow/pull/1224))
- Inject `Multi-Release` manifest attribute if any dependency contains
  it. ([#1239](https://github.com/GradleUp/shadow/pull/1239))

**Changed**

- **BREAKING CHANGE:** Mark `RelocatorRemapper` as `internal`. ([#1227](https://github.com/GradleUp/shadow/pull/1227))
- Bump min Java requirement to 11. ([#1242](https://github.com/GradleUp/shadow/pull/1242))

**Removed**

- **BREAKING CHANGE:** `ServiceFileTransformer.ServiceStream` has been
  removed. ([#1218](https://github.com/GradleUp/shadow/pull/1218))
- **BREAKING CHANGE:** Remove `KnowsTask` as it's useless. ([#1236](https://github.com/GradleUp/shadow/pull/1236))

## [9.0.0-beta7](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta7) - 2025-02-02

**Added**

- Inject `TargetJvmVersion` attribute for Gradle Module
  Metadata. ([#1199](https://github.com/GradleUp/shadow/pull/1199))
- Support Java 24. ([#1222](https://github.com/GradleUp/shadow/pull/1222))

**Changed**

- Update start script templates. ([#1183](https://github.com/GradleUp/shadow/pull/1183))
- Mark more `Transformer`s cacheable. ([#1210](https://github.com/GradleUp/shadow/pull/1210))
- Mark `ShadowJar.dependencyFilter` as `@Input`. ([#1206](https://github.com/GradleUp/shadow/pull/1206))  
  `ShadowSpec.stats` is removed and `ShadowJar.stats` is `internal` for now.
- Polish `startShadowScripts` task registering. ([#1216](https://github.com/GradleUp/shadow/pull/1216))

**Fixed**

- Support overriding `mainClass` provided by `JavaApplication`. ([#1182](https://github.com/GradleUp/shadow/pull/1182))
- Fix `ShadowJar` not being successful after `includes` or `excludes` are
  changed. ([#1200](https://github.com/GradleUp/shadow/pull/1200))

**Removed**

- **BREAKING CHANGE:** Remove `JavaJarExec`, now use `JavaExec` directly for `runShadow`
  task. ([#1197](https://github.com/GradleUp/shadow/pull/1197))

## [9.0.0-beta6](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta6) - 2025-01-23

**Added**

- Exclude `module-info.class` in Multi-Release folders by
  default. ([#1177](https://github.com/GradleUp/shadow/pull/1177))

**Fixed**

- Fix `Log4j2PluginsCacheFileTransformer` not working for merging `Log4j2Plugins.dat`
  files. ([#1175](https://github.com/GradleUp/shadow/pull/1175))

## [9.0.0-beta5](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta5) - 2025-01-21

**Added**

- Sync `SimpleRelocator` changes from maven-shade-plugin. ([#1076](https://github.com/GradleUp/shadow/pull/1076))

**Changed**

- Exclude kotlin-stdlib from plugin dependencies. ([#1093](https://github.com/GradleUp/shadow/pull/1093))
- **BREAKING CHANGE:** Migrate all `ListProperty` usages to
  `SetProperty`. ([#1103](https://github.com/GradleUp/shadow/pull/1103))  
  Some public `List` parameters are also changed to `Set`.
- Replace deprecated `SelfResolvingDependency` with
  `FileCollectionDependency`. ([#1114](https://github.com/GradleUp/shadow/pull/1114))
- Support configuring `separator` in `AppendingTransformer`. ([#1169](https://github.com/GradleUp/shadow/pull/1169))  
  This is useful for handling files like `resources/application.yml`.

**Fixed**

- Fail builds if processing bad jars.  ([#1146](https://github.com/GradleUp/shadow/pull/1146))

## [9.0.0-beta4](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta4) - 2024-12-06

**Changed**

- **BREAKING CHANGE:** Some public getters are removed from `SimpleRelocator`, `includes` and `excludes` are exposed as
  `SetProperty`s. ([#1079](https://github.com/GradleUp/shadow/pull/1079))

**Fixed**

- Adjust property initializations and modifiers in
  `ShadowJar`. ([#1090](https://github.com/GradleUp/shadow/pull/1090))  
  This fixes the regression for registering custom `ShadowJar` tasks.

## [9.0.0-beta2](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta2) - 2024-11-28

**Fixed**

- Revert "Migrate SimpleRelocator to using lazy properties" ([#1052](https://github.com/GradleUp/shadow/pull/1052))  
  This fixes the relocation not working in `v9.0.0-beta1`.

## [9.0.0-beta1](https://github.com/GradleUp/shadow/releases/tag/9.0.0-beta1) - 2024-11-27

**Added**

- Add .md support to the Apache License and Notice transformers. ([#1041](https://github.com/GradleUp/shadow/pull/1041))

**Changed**

- **BREAKING CHANGE:** Rewrite this plugin in Kotlin. ([#1012](https://github.com/GradleUp/shadow/pull/1012))
- **BREAKING CHANGE:** Migrate `Transformer`s to using lazy
  properties. ([#1036](https://github.com/GradleUp/shadow/pull/1036))
- **BREAKING CHANGE:** Migrate `ShadowJar` to using lazy
  properties. ([#1044](https://github.com/GradleUp/shadow/pull/1044))
  `isEnableRelocation` is removed, use `enableRelocation` instead.
- **BREAKING CHANGE:** Resolve `Configuration` directly in
  `DependencyFilter`. ([#1045](https://github.com/GradleUp/shadow/pull/1045))
- **BREAKING CHANGE:** Migrate `SimpleRelocator` to using lazy
  properties. ([#1047](https://github.com/GradleUp/shadow/pull/1047))

**Removed**

- **BREAKING CHANGE:** Remove Develocity integration. ([#1014](https://github.com/GradleUp/shadow/pull/1014))

**Fixed**

- Fix single Log4j2Plugins.dat isn't included into fat jar. ([#1039](https://github.com/GradleUp/shadow/issues/1039))

## [8.3.8](https://github.com/GradleUp/shadow/releases/tag/8.3.8) - 2025-07-01

**Fixed**

- Fix the regression of `PropertiesFileTransformer` in `8.3.7`. ([#1493](https://github.com/GradleUp/shadow/pull/1493))

**Changed**

- Expose Ant as `compile` scope. ([#1488](https://github.com/GradleUp/shadow/pull/1488))

## [8.3.7](https://github.com/GradleUp/shadow/releases/tag/8.3.7) - 2025-06-24

**Fixed**

- Fix compatibility for Gradle 9.0.0 RC1. ([#1470](https://github.com/GradleUp/shadow/pull/1470))

## [8.3.6](https://github.com/GradleUp/shadow/releases/tag/8.3.6) - 2025-02-02

**Added**

- Support Java 24. ([#1222](https://github.com/GradleUp/shadow/pull/1222))

## [8.3.5](https://github.com/GradleUp/shadow/releases/tag/8.3.5) - 2024-11-03

**Fixed**

- Revert "Bump Java level to 11" ([#1011](https://github.com/GradleUp/shadow/issues/1011)).  
  This reverts the change to maintain compatibility with 8.x versions. The Java level will be bumped to 11 or above in
  the next major release.

## [8.3.4](https://github.com/GradleUp/shadow/releases/tag/8.3.4) - 2024-10-29

**Fixed**

- Apply legacy plugin last, and declare capabilities for old plugins,
  fixes [#964](https://github.com/GradleUp/shadow/issues/964). ([#991](https://github.com/GradleUp/shadow/pull/991))

## [8.3.3](https://github.com/GradleUp/shadow/releases/tag/8.3.3) - 2024-10-02

**Changed**

- Disable Develocity integration by default. ([#993](https://github.com/GradleUp/shadow/pull/993))

## [8.3.2](https://github.com/GradleUp/shadow/releases/tag/8.3.2) - 2024-09-18

**Added**

- Support Java 23. ([#974](https://github.com/GradleUp/shadow/pull/974))

**Changed**

- `ShadowExtension.component` has been deprecated, now you can use `component.shadow`
  instead. ([#956](https://github.com/GradleUp/shadow/pull/956))
- **BREAKING CHANGE:** update
  to [jdependency 2.11](https://github.com/tcurdt/jdependency/releases/tag/jdependency-2.11), this requires Java 11 or
  above to run. ([#974](https://github.com/GradleUp/shadow/pull/974))

**Fixed**

- Stop publishing Shadow self fat jar to Maven repository. ([#967](https://github.com/GradleUp/shadow/pull/967))

## [8.3.1](https://github.com/GradleUp/shadow/releases/tag/8.3.1) - 2024-09-10

**Added**

- Apply an empty plugin that has the legacy `com.github.johnrengelman.shadow` plugin ID.
  This allows existing build logic to keep on reacting to the legacy plugin as the replacement is drop-in currently.

**Fixed**

- Explicitly add classifier to maven publication. ([#904](https://github.com/GradleUp/shadow/pull/904))
- Refix excluding Gradle APIs for java-gradle-plugin. ([#948](https://github.com/GradleUp/shadow/pull/948))

## [8.3.0](https://github.com/GradleUp/shadow/releases/tag/8.3.0) - 2024-08-08

**Changed**

- **BREAKING CHANGE:** the GitHub has been transferred from `johnrengelman/shadow` to `GradleUp/shadow`, you can view
  more details in [GradleUp/shadow/issues/908](https://github.com/GradleUp/shadow/issues/908).  
  We also update the plugin ID from `com.github.johnrengelman.shadow` to `com.gradleup.shadow`, and the
  Maven coordinate from `com.github.johnrengelman:shadow` to `com.gradleup.shadow:shadow-gradle-plugin`.
- Bump the min Gradle requirement from `8.0.0` to `8.3`. ([#876](https://github.com/GradleUp/shadow/pull/876))
- Support Java 21. ([#876](https://github.com/GradleUp/shadow/pull/876))
- Use new file permission API from Gradle 8.3. ([#876](https://github.com/GradleUp/shadow/pull/876))

**Fixed**

- Fix for PropertiesFileTransformer breaks Reproducible builds in
  `8.1.1`. ([#858](https://github.com/GradleUp/shadow/pull/858))

## [8.1.1](https://github.com/GradleUp/shadow/releases/tag/8.1.1) - 2023-03-20

**NOTE:** As of this version, the GitHub repository has migrated to the `main` branch as the default branch for
releases.

### What's Changed

* Replace deprecated ConfigureUtil by [@Goooler](https://github.com/Goooler)
  in [#826](https://github.com/GradleUp/shadow/pull/826)
* Polish outdated configs by [@Goooler](https://github.com/Goooler)
  in [#831](https://github.com/GradleUp/shadow/pull/831)
* Update plugin com.gradle.enterprise to v3.12.5 by [@renovate](https://github.com/renovate-bot)
  in [#838](https://github.com/GradleUp/shadow/pull/838)
* Update dependency gradle to v8.0.2 by [@renovate](https://github.com/renovate-bot)
  in [#844](https://github.com/GradleUp/shadow/pull/844)
* fix(deps): update dependency org.codehaus.plexus:plexus-utils to v3.5.1
  by [@renovate](https://github.com/renovate-bot) in [#837](https://github.com/GradleUp/shadow/pull/837)
* chore(deps): update dependency prismjs to v1.27.0 [security] by [@renovate](https://github.com/renovate-bot)
  in [#828](https://github.com/GradleUp/shadow/pull/828)
* Encode transformed properties files with specified Charset by [@scottsteen](https://github.com/scottsteen)
  in [#819](https://github.com/GradleUp/shadow/pull/819)
* chore(deps): update dependency vuepress to v1.9.9 by [@renovate](https://github.com/renovate-bot)
  in [#842](https://github.com/GradleUp/shadow/pull/842)

### New Contributors

* [@renovate](https://github.com/renovate-bot) made their first contribution
  in [#838](https://github.com/GradleUp/shadow/pull/838)
* [@scottsteen](https://github.com/scottsteen) made their first contribution
  in [#819](https://github.com/GradleUp/shadow/pull/819)

**Full Changelog**: [`8.1.0...8.1.1`](https://github.com/GradleUp/shadow/compare/8.1.0...8.1.1)

## [8.1.0](https://github.com/GradleUp/shadow/releases/tag/8.1.0) - 2023-02-26

**BREAKING CHANGE:** Due to adoption of the latest version of the `com.gradle.plugin-publish` plugin, the maven GAV
coordinates have changed as of this version.
The correct coordinates now align with the plugin ID itself:
`group=com.github.johnrengelman, artifact=shadow, version=<version>`.
For example, `classpath("com.github.johnrengelman:shadow:8.1.0")` is the correct configuration for this version.

**BREAKING CHANGE:** The `ConfigureShadowRelocation` task was removed as of this version to better support Gradle
configuration caching.
Instead, use the `enableRelocation = true` and `relocationPrefix = "<new package>"` settings on the `ShadowJar` task
type.

### What's Changed

* Minor cleanups by [@Goooler](https://github.com/Goooler) in [#823](https://github.com/GradleUp/shadow/pull/823)
* Support config cache by [@Goooler](https://github.com/Goooler) in [#824](https://github.com/GradleUp/shadow/pull/824)
* Fix RelocatorRemapper: do not map inner class name if not changed by [@Him188](https://github.com/Him188)
  in [#793](https://github.com/GradleUp/shadow/pull/793)

### New Contributors

* [@Him188](https://github.com/Him188) made their first contribution
  in [#793](https://github.com/GradleUp/shadow/pull/793)

**Full Changelog**: [`8.0.0...8.1.0`](https://github.com/GradleUp/shadow/compare/8.0.0...8.1.0)

## [8.0.0](https://github.com/GradleUp/shadow/releases/tag/8.0.0) - 2023-02-24

### What's Changed

* Fix the plugin dependency identifier in the docs by [@lnhrdt](https://github.com/lnhrdt)
  in [#754](https://github.com/GradleUp/shadow/pull/754)
* mergeGroovyExtensionModules() not working with Groovy 2.5+ by [@paulk-asert](https://github.com/paulk-asert)
  in [#779](https://github.com/GradleUp/shadow/pull/779)
* Upgrade to ASM 9.3 to support JDK 19. by [@vyazelenko](https://github.com/vyazelenko)
  in [#770](https://github.com/GradleUp/shadow/pull/770)
* Do not add a dependencies block if it's already there by [@desiderantes](https://github.com/desiderantes)
  in [#769](https://github.com/GradleUp/shadow/pull/769)
* Update README with new badge and links by [@ThexXTURBOXx](https://github.com/ThexXTURBOXx)
  in [#743](https://github.com/GradleUp/shadow/pull/743)
* Fix value not set when rawString is true. by [@qian0817](https://github.com/qian0817)
  in [#765](https://github.com/GradleUp/shadow/pull/765)
* Mark the Log4j2PluginsCacheFileTransformer as cacheable. by [@staktrace](https://github.com/staktrace)
  in [#724](https://github.com/GradleUp/shadow/pull/724)
* Fix retrieval of dependencies node when publishing by [@netomi](https://github.com/netomi)
  in [#798](https://github.com/GradleUp/shadow/pull/798)
* Upgrade dependency ASM from `9.3` to `9.4` by [@codecholeric](https://github.com/codecholeric)
  in [#817](https://github.com/GradleUp/shadow/pull/817)
* Fix a typo of code comment in the minimizing page by [@jebnix](https://github.com/jebnix)
  in [#800](https://github.com/GradleUp/shadow/pull/800)
* Prefer using plugin extensions over deprecated conventions by [@eskatos](https://github.com/eskatos)
  in [#821](https://github.com/GradleUp/shadow/pull/821)
* Introduce CleanProperties by [@simPod](https://github.com/simPod)
  in [#622](https://github.com/GradleUp/shadow/pull/622)
* Support Gradle 8.0 by [@Goooler](https://github.com/Goooler) in [#822](https://github.com/GradleUp/shadow/pull/822)
* Updated dependencies, Gradle versions and Fix Test by [@ElisaMin](https://github.com/ElisaMin)
  in [#791](https://github.com/GradleUp/shadow/pull/791)

### New Contributors

* [@lnhrdt](https://github.com/lnhrdt) made their first contribution
  in [#754](https://github.com/GradleUp/shadow/pull/754)
* [@paulk-asert](https://github.com/paulk-asert) made their first contribution
  in [#779](https://github.com/GradleUp/shadow/pull/779)
* [@desiderantes](https://github.com/desiderantes) made their first contribution
  in [#769](https://github.com/GradleUp/shadow/pull/769)
* [@ThexXTURBOXx](https://github.com/ThexXTURBOXx) made their first contribution
  in [#743](https://github.com/GradleUp/shadow/pull/743)
* [@qian0817](https://github.com/qian0817) made their first contribution
  in [#765](https://github.com/GradleUp/shadow/pull/765)
* [@staktrace](https://github.com/staktrace) made their first contribution
  in [#724](https://github.com/GradleUp/shadow/pull/724)
* [@netomi](https://github.com/netomi) made their first contribution
  in [#798](https://github.com/GradleUp/shadow/pull/798)
* [@codecholeric](https://github.com/codecholeric) made their first contribution
  in [#817](https://github.com/GradleUp/shadow/pull/817)
* [@jebnix](https://github.com/jebnix) made their first contribution
  in [#800](https://github.com/GradleUp/shadow/pull/800)
* [@eskatos](https://github.com/eskatos) made their first contribution
  in [#821](https://github.com/GradleUp/shadow/pull/821)
* [@simPod](https://github.com/simPod) made their first contribution
  in [#622](https://github.com/GradleUp/shadow/pull/622)
* [@Goooler](https://github.com/Goooler) made their first contribution
  in [#822](https://github.com/GradleUp/shadow/pull/822)
* [@ElisaMin](https://github.com/ElisaMin) made their first contribution
  in [#791](https://github.com/GradleUp/shadow/pull/791)

**Full Changelog**: [`7.1.2...8.0.0`](https://github.com/GradleUp/shadow/compare/7.1.2...8.0.0)

## [7.1.2](https://github.com/GradleUp/shadow/releases/tag/7.1.2) - 2021-12-28

- Upgrade log4j to 2.17.1 due to CVE-2021-45105 and CVE-2021-44832

## [7.1.1](https://github.com/GradleUp/shadow/releases/tag/7.1.1) - 2021-12-14

- Upgrade log4j to 2.16.0 due to CVE-2021-44228 and CVE-2021-45046

## [7.1.0](https://github.com/GradleUp/shadow/releases/tag/7.1.0) - 2021-10-04

- **BREAKING** - The maven coordinates for the plugins have changed as of this version. The proper `group:artifact` is
  `gradle.plugin.com.github.johnrengelman:shadow`
- [Jeff](https://github.com/mathjeff) - Fix `shadowJar` Out-Of-Date with configuration
  caching [#708](https://github.com/GradleUp/shadow/pull/708)
- [Fiouz](https://github.com/Fiouz) - Better support for statically typed languages. This change may require code
  changes if you are utilizing the Groovy generated getters for properties in some Shadow
  transformers [#706](https://github.com/GradleUp/shadow/pull/706)
- [Helder Pereira](https://github.com/helfper) - Various
  cleanups [#672](https://github.com/GradleUp/shadow/pull/672), [#700](https://github.com/GradleUp/shadow/pull/700), [#701](https://github.com/GradleUp/shadow/pull/701), [#702](https://github.com/GradleUp/shadow/pull/702)
- [Roberto Perez Alcolea](https://github.com/rpalcolea) - Support JVM
  Toolchains [#691](https://github.com/GradleUp/shadow/pull/691)
- [mjulianotq](https://github.com/mjulianotq) - Fix `Project.afterEvaluate`
  conflicts [#675](https://github.com/GradleUp/shadow/pull/675)
- [Ilya Muradyan](https://github.com/ileasile) - Fix relocation for
  `ComponentsXmlResourceTransformer` [#678](https://github.com/GradleUp/shadow/pull/678)
- [Vaidotas Valuckas](https://github.com/rieske) - Fix `JavaExec.main`
  deprecation [#686](https://github.com/GradleUp/shadow/pull/686)
- [Dmitry Vyazelenko](https://github.com/vyazelenko) - Support Java 18 with ASM
  9.2 [#698](https://github.com/GradleUp/shadow/pull/698)
- [Jason](https://github.com/jpenilla) - Support Records with JDependency
  2.7.0 [#681](https://github.com/GradleUp/shadow/pull/681)

## [7.0.0](https://github.com/GradleUp/shadow/releases/tag/7.0.0) - 2021-04-26

- Required Gradle 7.0+
- Support for Java 16
- Removes JCenter references
- **Breaking Change!** - The maven group coordinate has changed to be
  `gradle.plugin.com.github.jengelman.gradle.plugins`. Users explicitly declaring the buildscript classpath will need to
  update their configuration.

  ```
  buildscript {
    repositories {
      gradlePluginPortal()
    }
    dependencies {
      classpath "gradle.plugin.com.github.jengelman.gradle.plugins:shadow:7.0.0"
    }
  }

  apply plugin: "com.gradleup.shadow"
  ```

- [Cédric Champeau](https://github.com/melix) - Support Gradle 7 [#624](https://github.com/GradleUp/shadow/pull/624)
- [Daniel Oakey](https://github.com/danieloakey) - Close `FileInputStream` when remapping close to avoid classloader
  locks [#642](https://github.com/GradleUp/shadow/pull/642)
- [Maximilian Müller](https://github.com/maxm123) - Groovy error in `ServiceFileTransformer` in Gradle
  3 [#655](https://github.com/GradleUp/shadow/pull/655)
- [Helder Pereira](https://github.com/helfper) - Fix deprecations errors in transformers and add CI testing around
  future deprecations [#647](https://github.com/GradleUp/shadow/pull/647)
- [Nicolas Humblot](https://github.com/nhumblot) - Handle deprecation of `mainClassName`
  configuration [#609](https://github.com/GradleUp/shadow/pull/609), [#612](https://github.com/GradleUp/shadow/pull/612)
- [Bernie Schelberg](https://github.com/bschelberg) - Exclude `api` and `implementations` from legacy `maven`
  POM [#615](https://github.com/GradleUp/shadow/pull/615)

## [6.1.0](https://github.com/GradleUp/shadow/releases/tag/6.1.0) - 2020-10-05

- As of this version, Shadow is compiled with Java 8 source and target compatibility. This aligns the plugin with the
  minimum required Java version
  for Gradle 6.0 (https://docs.gradle.org/6.0/release-notes.html).
- Update ASM to 9.0 to support JDK 16.
- [Tim Yates](https://github.com/timyates), [Benedikt Ritter](https://github.com/britter) - Enable Configuration Caching
  for Gradle 6.6+ [#591](https://github.com/GradleUp/shadow/pull/591)
- [Caleb Larsen](https://github.com/MuffinTheMan) - doc updates [#583](https://github.com/GradleUp/shadow/pull/593)
- [Schalk W. Cronjé](https://github.com/ysb33r) - log4j version update for
  CVE-2020-9488 [#590](https://github.com/GradleUp/shadow/pull/590)
- [Victor Tso](https://github.com/roxchkplusony) - Input stream handling for large
  projects [#587](https://github.com/GradleUp/shadow/pull/587)
- [Matthew Haughton](https://github.com/3flex) - Implement Task Configuration Avoidance
  pattern [#597](https://github.com/GradleUp/shadow/pull/597)

## [6.0.0](https://github.com/GradleUp/shadow/releases/tag/6.0.0) - 2020-06-15

- Required Gradle 6.0+
- _NEW_: Support for Gradle Metadata publication via the `shadowRuntimeElements` configuration. This is a _beta_ feature
  the hasn't been tested extensively. Feedback is appreciated.
- Fix Gradle 7 deprecation warnings [#530](https://github.com/GradleUp/shadow/issues/530)
- Fix to generated start script to correctly use
  `optsEnvironmentVar`[#518](https://github.com/GradleUp/shadow/commit/7e99c02957773205c3babdd23f4bbf883330c975)
- [Yahor Berdnikau](https://github.com/Tapchicoma) - Fix issues with Gradle API being embedded into published
  JAR [#527](https://github.com/GradleUp/shadow/issues/527)
- [Dmitry Vyazelenko](https://github.com/vyazelenko) - ASM updates to support latest Java
  versions [#549](https://github.com/GradleUp/shadow/pull/549)
- [ejjcase](https://github.com/ejjcase) - Support exposing shadowed project dependencies via
  POM [#543](https://github.com/GradleUp/shadow/pull/543)
- [Artem Chubaryan](https://github.com/Armaxis) - Performance
  optimizations [#535](https://github.com/GradleUp/shadow/pull/535)
- [Trask Stalnaker](https://github.com/trask) - Fix exclude patterns on
  Windows [#539](https://github.com/GradleUp/shadow/pull/539)
- [Artem Chubaryan](https://github.com/Armaxis) - Allow usage of true regex patterns for include/exclude by the
  `%regex[<pattern>]` syntax [#536](https://github.com/GradleUp/shadow/pull/536)

## [5.2.0](https://github.com/GradleUp/shadow/releases/tag/5.2.0) - 2019-11-10

- [Inez Korczyński](https://github.com/inez) - Performance optimization when evaluating relocation
  paths [#507](https://github.com/GradleUp/shadow/pull/507)
- [Jeff Adler](https://github.com/jeffalder) - Fix remapping issues with multi release
  JARS [#526](https://github.com/GradleUp/shadow/pull/526)
- [Gary Hale](https://github.com/ghale) - Implement support for Gradle build
  cache [#524](https://github.com/GradleUp/shadow/pull/524)
- [Roberto Perez Alcolea](https://github.com/rpalcolea) - Gradle 6.x
  support [#517](https://github.com/GradleUp/shadow/pull/517)
- [Konstantin Gribov](https://github.com/grossws) - Return support for 5.0 for convention
  mapping [#502](https://github.com/GradleUp/shadow/pull/502)
- [Lai Jiang](https://github.com/jianglai) - Documentation updates on how to reconfigure `classifier` and
  `version` [#512](https://github.com/GradleUp/shadow/pull/512)

## [5.1.0](https://github.com/GradleUp/shadow/releases/tag/5.1.0) - 2019-06-29

- [Chris Rankin](https://github.com/chrisr3) - Add `ManifestAppenderTransformer` to support appending to Jar
  manifest [#474](https://github.com/GradleUp/shadow/pull/474)
- [Min-Ken Lai](https://github.com/minkenlai) - Additional escaping fixes in start
  script [#487](https://github.com/GradleUp/shadow/pull/487)
- [Alan D. Cabrera](https://github.com/maguro) - Automatically remove `gradleApi` from `compile` scope in the presence
  of `shadow` [#459](https://github.com/GradleUp/shadow/pull/459)
- [Christian Stein](https://github.com/sormuras) - Do not initialize `UnusedTracker` when not
  requested [#480](https://github.com/GradleUp/shadow/pull/480), [#479](https://github.com/GradleUp/shadow/issues/479)
- [Attila Kelemen](https://github.com/kelemen) - Fix `NullPointerException` when using java minimization and api project
  dependency with version [#477](https://github.com/GradleUp/shadow/pull/477)

## [5.0.0](https://github.com/GradleUp/shadow/releases/tag/5.0.0) - 2019-02-28

- Require Gradle 5.0+
- Fix issue with build classifier `-all` being dropped in Gradle 5.1+
- [Roberto Perez Alcolea](https://github.com/rpalcolea) - Exclude project dependencies from
  minimization [#420](https://github.com/GradleUp/shadow/pull/420)
- [Matt King](https://github.com/kyrrigle), [Richard Marbach](https://github.com/RichardMarbach) - Fix escaping in start
  script [#453](https://github.com/GradleUp/shadow/pull/454), [#455](https://github.com/GradleUp/shadow/pull/455)
- [Dennis Schumann](https://github.com/Hillkorn) - Fix Gradle 5.2 incompatibility with
  `ShadowJar.getMetaClass()` [#456](https://github.com/GradleUp/shadow/pull/456)
- [Brane F. Gračnar](https://github.com/bfg) - Fix compatibility with
  `com.palantir.docker` [#460](https://github.com/GradleUp/shadow/pull/460)

## [4.0.4](https://github.com/GradleUp/shadow/releases/tag/4.0.4) - 2019-01-19

- When using `shadow`, `application`, and `maven` plugins together, remove `shadowDistZip` and `shadowDistTar` from
  `configurations.archives` so they are not published or installed by default with the `uploadArchives` or `install`
  tasks. [#347](https://github.com/GradleUp/shadow/issues/347)
- [James Nelson](https://github.com/JamesXNelson) - Fix `null` path when using Jar minimization and Gradle's `api`
  configuration. [#424](https://github.com/GradleUp/shadow/issues/424), [#425](https://github.com/GradleUp/shadow/issues/425)

## [4.0.3](https://github.com/GradleUp/shadow/releases/tag/4.0.3) - 2018-11-21

- [Mark Vieira](https://github.com/mark-vieira) - Don't leak plugin classes to Gradle's Spec
  cache [#430](https://github.com/GradleUp/shadow/pull/430)

## [4.0.2](https://github.com/GradleUp/shadow/releases/tag/4.0.2) - 2018-10-27

- [Petar Petrov](https://github.com/petarov) - Update to ASM 7.0-beta and jdependency 2.1.1 to support Java
  11, [#415](https://github.com/GradleUp/shadow/pull/415)
- [Victor Tso](https://github.com/roxchkplusony) - Ensure input streams are
  closed, [#411](https://github.com/GradleUp/shadow/pull/411)
- [Osip Fatkullin](https://github.com/osipxd) - Exclude `api` configuration from
  minimization, [#405](https://github.com/GradleUp/shadow/pull/405)

## [4.0.1](https://github.com/GradleUp/shadow/releases/tag/4.0.1) - 2018-09-30

- **Breaking Change!** `Transform.modifyOutputStream(ZipOutputStream os)` to
  `Transform.modifyOutputStream(ZipOutputStream jos, boolean preserveFileTimestamps)`.
  Typically breaking changes are reserved for major version releases, but this change was necessary for
  `preserverFileTimestamps` (introduced in v4.0.0) to work correctly
  in the presence of transformers, [#404](https://github.com/GradleUp/shadow/issues/404)
- Fix regression in support Java 10+ during relocation, [#403](https://github.com/GradleUp/shadow/issues/403)

## [4.0.0](https://github.com/GradleUp/shadow/releases/tag/4.0.0) - 2018-09-25

- **Breaking Change!** Restrict Plugin to Gradle 4.0+. Shadow major versions will align with Gradle major versions going
  forward.
- **Breaking Change!** For clarity purposes `com.github.johnrengelman.plugin-shadow` has been removed. If you intend to
  use this feature, you will need to declare your own `ConfigureShadowRelocation` task. See
  section [2.9.2](https://gradleup.com/shadow/#automatically_relocating_dependencies) of the User Guide
- [Sergey Tselovalnikov](https://github.com/SerCeMan) - Upgrade to ASM 6.2.1 to support Java 11
- [Chris Cowan](https://github.com/Macil) - Add support for `shadowJar.preserveFileTimestamps` property.
  See [Jar.preserveFileTimestamps](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:preserveFileTimestamps)
- [Paul N. Baker](https://github.com/nikole-dunixi) - Add `Log4j2PluginsCacheFileTransformer` to process Log4j DAT files
  during merge.
- [Felipe Lima](https://github.com/felipecsl) - Fix the long standing "No property `mainClassName`" issue.
- [debanne](https://github.com/debanne) - Implement JAR minimization actions. This will attempt to exclude unused
  classes in your shadowed JAR.
- Configure exclusion of `module-info.class` from `shadowJar` when using the Shadow the Java
  plugin, [#352](https://github.com/GradleUp/shadow/issues/352)

## [2.0.4](https://github.com/GradleUp/shadow/releases/tag/2.0.4) - 2018-04-27

- Update to ASM 6.1.1 to address performance issues - [ASM Issue 317816](https://gitlab.ow2.org/asm/asm/issues/317816)
- Close InputStreams after using them, [#364](https://github.com/GradleUp/shadow/issues/364)
- Remove usage of Gradle internal `AbstractFileCollection`.
- Add task annotations to remove warnings when validating plugin.

## [2.0.3](https://github.com/GradleUp/shadow/releases/tag/2.0.3) - 2018-03-24

- [Martin Sadowski](https://github.com/ttsiebzehntt) - Update to ASM 6.1
- [Scott Newson](https://github.com/sgnewson) - Fix deprecated Gradle
  warnings, [#356](https://github.com/GradleUp/shadow/pull/356)

## [2.0.2](https://github.com/GradleUp/shadow/releases/tag/2.0.2) - 2017-12-12

- [Ben Adazza](https://github.com/ghost), [Tyler Benson](https://github.com/tylerbenson) - documentation
- [Marke Vieira](https://github.com/mark-vieira) - Support multi-project builds with Build-Scan integration
- Upgrade to ASM
  6, [#294]https://github.com/GradleUp/shadow/issues/294, [#303](https://github.com/GradleUp/shadow/issues/303)
- [Rob Spieldenner](https://github.com/rspieldenner) - Fix integration with `application` plugin in Gradle
  4.3, [#339](https://github.com/GradleUp/shadow/issues/339)
- Fixed deprecation warning from Gradle 4.2+, [#326](https://github.com/GradleUp/shadow/issues/326)

## [2.0.1](https://github.com/GradleUp/shadow/releases/tag/2.0.1) - 2017-06-23

- Fix `null+configuration` error, [#297](https://github.com/GradleUp/shadow/issues/297)

## [2.0.0](https://github.com/GradleUp/shadow/releases/tag/2.0.0) - 2017-05-09

- **Breaking Change!** Restrict Plugin to Gradle 3.0+
- **Breaking Change!** Build with Java 7
- **Breaking Change!** Updated `Transformer` interface to accept `TransformerContext` object instead of individual
  values
- **Breaking Change!** Updated `Relocator` interface to accept `RelocatePathContext` and `RelocateClassContext` objects
- **Breaking Change!** Distribution tasks `distShadowZip` and `distShadowTar` have been removed and replaced with the
  standard `shadowDistZip` and `shadowDistTar` from the Gradle Distribution plugin.
- **Breaking Change!** The `installShadowApp` task has been removed and replaced with the standard `installShadowDist`
  task from the Gradle Distribution plugin.
- **Breaking Change!** The new `installShadowDist` task outputs to `build/install/<project name>-shadow` per the
  standard (formerly was `build/installShadow`)
- **Breaking Change!** `component.shadow` removed in favor of `project.shadow.component(publication)` so as to remove
  dependency on internal Gradle APIs.
- _NEW_ Introducing `ConfigureShadowRelocation` task and `com.github.johnrengelman.plugin-shadow` plugin to
  automatically configure package relocation for Gradle plugins.
- _NEW_ Integration with Gradle Build Scans. When running a `ShadowJar` task with Build Scans, custom values including
  dependencies merged anc package relocations are published in the scan.
- Build Shadow w/ Shadow. This will help prevent any future classpath conflicts with Gradle.
- Replace `startShadowScripts` tasks with Gradle's built-in `CreateStartScripts` type.
- Build with Gradle 3.1
- [Marc Philipp](https://github.com/marcphilipp) - Add `keyTransformer` property to `PropertiesFileTransformer`
- Update to ASM 5.2
- [Piotr Kubowicz](https://github.com/pkubowicz) - Support `api`, `implementation`, `runtimeOnly` dependency
  configurations introdcued in Gradle 3.3

## [1.2.4](https://github.com/GradleUp/shadow/releases/tag/1.2.4) - 2016-11-03

- Don't resolve dependency configurations during config phase, [#128](https://github.com/GradleUp/shadow/issues/129)
- Build plugin with Gradle 2.14
- Fix docs regarding inheriting Jar manifest, [#251](https://github.com/GradleUp/shadow/issues/251)
- [Ethan Hall](https://github.com/ethankhall) - Support projects that configure uploading to Ivy
  repositories, [#256](https://github.com/GradleUp/shadow/pull/256)
- Force task to depend on dependency configuration, [#152](https://github.com/GradleUp/shadow/issues/152)
- Do not explode ZIP files into shadow jar, [#196](https://github.com/GradleUp/shadow/issues/196)
- [John Szakmeister](https://github.com/jszakmeister) - Preserve timestamps on merged jar
  entries, [#260](https://github.com/GradleUp/shadow/pull/260)

## [1.2.3](https://github.com/GradleUp/shadow/releases/tag/1.2.3) - 2016-01-25

- Support for Gradle 2.11-rc-1, [#177](https://github.com/GradleUp/shadow/issues/177)
- Convert internal framework to [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html)
- [Fedor Korotkov](https://github.com/fkorotkov) - Use BufferedOutputStream when writing the Zip
  file, [#171](https://github.com/GradleUp/shadow/pull/171)
- [Haw-Bin Chai](https://github.com/hbchai) - Quote Jar path in Windows start script as it may contain
  spaces, [#170](https://github.com/GradleUp/shadow/pull/170)
- [Serban Iordache](https://github.com/siordache) - Evaluate relocation specs when merging service
  descriptors, [#165](https://github.com/GradleUp/shadow/pull/165)

## [1.2.2](https://github.com/GradleUp/shadow/releases/tag/1.2.2) - 2015-07-17

- [Minecrell](https://github.com/Minecrell) - Gradle 2.5
  compatibility, [#147](https://github.com/GradleUp/shadow/issues/147)

## [1.2.1](https://github.com/GradleUp/shadow/releases/tag/1.2.1) - 2015-01-23

- Apply package relocations to dependency resources, [#114](https://github.com/GradleUp/shadow/issues/114)

## [1.2.0](https://github.com/GradleUp/shadow/releases/tag/1.2.0) - 2014-11-24

- Re-organize some code to remove need for forcing the Gradle API ClassLoader to allow the `org.apache.tools.zip`
  package.
- Upgrade JDOM library from 1.1 to 2.0.5 (change dependency from `jdom:jdom:1.1` to
  `org.jdom:jdom2:2.0.5`), [#98](https://github.com/GradleUp/shadow/issues/98)
- Convert ShadowJar.groovy to ShadowJar.java to workaround binary incompatibility introduced by Gradle
  2.2, [#106](https://github.com/GradleUp/shadow/issues/106)
- Updated ASM library to `5.0.3` to support JDK8, [#97](https://github.com/GradleUp/shadow/issues/97)
- Allows for regex pattern matching in the `dependency` string when
  including/excluding, [#83](https://github.com/GradleUp/shadow/issues/83)
- Apply package relocations to resource files, [#93](https://github.com/GradleUp/shadow/issues/93)

## [1.1.2](https://github.com/GradleUp/shadow/releases/tag/1.1.2) - 2014-09-09

- fix bug in `runShadow` where dependencies from the `shadow` configuration are not
  available, [#94](https://github.com/GradleUp/shadow/issues/94)

## [1.1.1](https://github.com/GradleUp/shadow/releases/tag/1.1.1) - 2014-08-27

- Fix bug in `'createStartScripts'` task that was causing it to not execute `'shadowJar'`
  task, [#90](https://github.com/GradleUp/shadow/issues/90)
- Do not include `null` in ShadowJar Manifest `'Class-Path'` value when `jar` task does not specify a value for
  it, [#92](https://github.com/GradleUp/shadow/issues/92)
- ShadowJar Manifest `'Class-Path'` should reference jars from `'shadow'` config as relative to location of `shadowJar`
  output, [#91](https://github.com/GradleUp/shadow/issues/91)

## [1.1.0](https://github.com/GradleUp/shadow/releases/tag/1.1.0) - 2014-08-26

- **Breaking Change!** Fix leaking of `shadowJar.manifest` into
  `jar.manifest`, [#82](https://github.com/GradleUp/shadow/issues/82)
  To simplify behavior, the `shadowJar.appendManifest` method has been removed. Replace uses with `shadowJar.manifest`
- `ShadowTask` now has a `configurations` property that is resolved to the files in the resolved configuration before
  being added to the copy spec. This allows for an easier implementation for filtering. The default 'shadowJar' task
  has the convention of adding the `'runtime'` scope to this list. Manually created instances of `ShadowTask` have no
  configurations added by default and can be configured by setting `task.configurations`.
- Properly configure integration with the `'maven'` plugin when added. When adding `'maven'` the `'uploadShadow'` task
  will now properly configure the POM dependencies by removing the `'compile'` and `'runtime'` configurations from the
  POM and adding the `'shadow'` configuration as a `RUNTIME` scope in the POM. This behavior matches the behavior when
  using the `'maven-publish'` plugin.
- [Matt Hurne](https://github.com/matthurne) - Allow `ServiceFileTransformer` to specify include/exclude patterns for
  files within the configured path to merge.
- [Matt Hurne](https://github.com/matthurne) - Added `GroovyExtensionModuleTransformer` for merging Groovy Extension
  module
  descriptor files. The existing `ServiceFileTransformer` now excludes Groovy Extension Module descriptors by default.
- `distShadowZip` and `distShadowZip` now contain the shadow library and run scripts instead of the default from the
  `'application'` plugin, [#89](https://github.com/GradleUp/shadow/issues/89)

## [1.0.3](https://github.com/GradleUp/shadow/releases/tag/1.0.3) - 2014-07-29

- Make service files root path configurable for
  `ServiceFileTransformer`, [#72](https://github.com/GradleUp/shadow/issues/72)
- [Andres Almiray](https://github.com/aalmiray - Added
  PropertiesFileTransformer, [#73](https://github.com/GradleUp/shadow/issues/73)
- [Brandon Kearby](https://github.com/brandonkearby) - Fixed StackOverflow when a cycle occurs in the resolved
  dependency grap, [#69](https://github.com/GradleUp/shadow/pull/69)
- Apply Transformers to project
  resources, [#70](https://github.com/GradleUp/shadow/issues/70), [#71](https://github.com/GradleUp/shadow/issues/71)
- [Minecrell](https://github.com/Minecrell) - Do not drop non-class files from dependencies when relocation is
  enabled, [#61](https://github.com/GradleUp/shadow/issues/61)
- Remove support for applying individual sub-plugins by Id (easier maintenance and cleaner presentation in Gradle
  Portal)

## [1.0.2](https://github.com/GradleUp/shadow/releases/tag/1.0.2) - 2014-07-07

- Do not add an empty Class-Path attribute to the manifest when the `shadow` configuration contains no dependencies.
- `runShadow` now registers `shadowJar` as an input. Previously, `runShadow` did not execute `shadowJar` and an error
  occurred.
- Support Gradle 2.0, [#66](https://github.com/GradleUp/shadow/issues/66)
- Do not override existing 'Class-Path' Manifest attribute settings from Jar configuration. Instead
  combine, [#65](https://github.com/GradleUp/shadow/issues/65)

## [1.0.1](https://github.com/GradleUp/shadow/releases/tag/1.0.1) - 2014-06-28

- Fix issue where non-class files are dropped when using relocation, [#58](https://github.com/GradleUp/shadow/issues/58)
- Do not create a `/` directory inside the output jar.
- Fix `runShadow` task to evaluate the `shadowJar.archiveFile` property at execution
  time, [#60](https://github.com/GradleUp/shadow/issues/60)

## [1.0.0](https://github.com/GradleUp/shadow/releases/tag/1.0.0) - 2014-06-27

- Previously known as v0.9.0
- All changes from 0.9.0-M1 to 0.9.0-M5
- Properly configure the ShadowJar task inputs to observe the include/excludes from the `dependencies` block. This
  allows UP-TO-DATE checking to work properly when changing the `dependencies`
  rulea, [#54](https://github.com/GradleUp/shadow/issues/54)
- Apply relocation remappings to classes and imports in source
  project, [#55](https://github.com/GradleUp/shadow/issues/55)
- Do not create directories in jar for source of remapped class, created directories in jar for destination of remapped
  classes, [#53](https://github.com/GradleUp/shadow/issues/53)

## [0.9.0-M5](https://github.com/GradleUp/shadow/releases/tag/0.9.0-M5) - 2014-06-26

- Add commons-io to compile classpath
- Update asm library to 4.1

## [0.9.0-M4](https://github.com/GradleUp/shadow/releases/tag/0.9.0-M4) - 2014-06-21

- Break plugin into multiple sub-plugins. `ShadowBasePlugin` is always applied.
  `ShadowJavaPlugin` and `ShadowApplicationPlugin` are applied in reaction to applying the `java` and `application`
  plugins respectively.
- Shadow does not applied `java` plugin automatically. `java` or `groovy` must be applied in conjunction with `shadow`.
- Moved artifact filtering to `dependencies {}` block underneath `shadowJar`. This allows better include/exclude control
  for dependencies.
- Dependencies added to the `shadow` configuration are automatically added to the `Class-Path` attribute in the manifest
  for `shadowJar`
- Applying `application` plugin and settings `mainClassName` automatically configures the `Main-Class` attribute in
  the manifest for `shadowJar`
- `runShadow` now utilizes the output of the `shadowJar` and executes using `java -jar <shadow jar file>`
- Start Scripts for shadow distribution now utilize `java -jar` to execute instead of placing all files on classpath
  and executing main class.
- Excluding/Including dependencies no longer includes transitive dependencies. All dependencies for inclusion/exclusion
  must be explicitly configured via a spec.

## [0.9.0-M3](https://github.com/GradleUp/shadow/releases/tag/0.9.0-M3) - 2014-06-14

- Use commons.io FilenameUtils to determine name of resolved jars for including/excluding

## [0.9.0-M2](https://github.com/GradleUp/shadow/releases/tag/0.9.0-M2) - 2014-06-09

- Added integration with `application` plugin to replace old `OutputSignedJars` task
- Fixed bug that resulted in duplicate file entries in the resulting Jar
- Changed plugin id to 'com.github.johnrengelman.shadow' to support Gradle 2.x plugin infrastructure.

## [0.9.0-M1](https://github.com/GradleUp/shadow/releases/tag/0.9.0-M1) - 2014-06-06

- Rewrite based on Gradle Jar Task
- `ShadowJar` now extends `Jar`
- Removed `signedCompile` and `signedRuntime` configurations in favor of `shadow` configuration
- Removed `OutputSignedJars` task
