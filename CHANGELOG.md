# Change Log


## [Unreleased](https://github.com/GradleUp/shadow/compare/9.6.1...HEAD) - 2026-xx-xx

### Added

- Allow configuring the final R8 configuration file with `R8Spec.configurationFile`. (#2133)
- Add `ProGuardFilesResourceTransformer` to merge R8/ProGuard rule files. (#2196)
- Improvements for `ManifestResourceTransformer`. (#2200)
  - Support removing manifest attributes using `NULL`.
  - Support manifest header relocation via configurable `attributesToRelocate` property.

### Changed

- Bump min Gradle requirement to 9.4.0. (#2114)
- Remove runtime dependencies on Commons Codec and Commons IO by using JDK APIs. (#2136)
- **POTENTIALLY BREAKING:** Remove `Serializable` from `DependencyFilter`. (#2144)
- Bump default R8 from `9.1.31` to `9.4.14`. (#2193)
- Allow repackaging Service file classes with R8. (#2174)
- Normalize line separators to LF (`\n`) in `ResourceTransformer`s for reproducible builds. (#2197)
  - `ApacheNoticeResourceTransformer`
  - `ComponentsXmlResourceTransformer`
  - `GroovyExtensionModuleTransformer`
  - `PropertiesFileTransformer`
  - `XmlAppendingTransformer`
- Append terminating newline in `ServiceFileTransformer`. (#2202)

### Deprecated

- Deprecate `keepRules` and `keepRuleFiles` in `R8Spec`. (#2120)  
  Use `proguardRules` and `proguardRuleFiles` instead. The properties will be removed in Shadow 10.
- Deprecate `ShadowJar.minimizeJar`. (#2124)  
  Use `ShadowJar.minimize()` explicitly instead. The property will be made non-public in Shadow 10.
- Deprecate `DontIncludeResourceTransformer` and `IncludeResourceTransformer`. (#2143)  
  Use `ShadowJar.exclude` or `ShadowJar.from` instead. The classes will be removed in Shadow 10.
- Deprecate `TransformerContext.Builder`. (#2184)  
  Use `TransformerContext` constructor instead. The Builder API will be removed in Shadow 10.
- Deprecate `ManifestResourceTransformer.attributes(Map)`. (#2200)  
  Use `manifestEntries` instead. The method will be removed in Shadow 10.
- Deprecate `ApacheLicenseResourceTransformer`. (#2221)  
  Use `ShadowJar.exclude` or `MergeLicenseResourceTransformer` instead. The class will be removed in Shadow 10.
- Deprecate `ManifestAppenderTransformer`. (#2221)  
  Use `ManifestResourceTransformer` or `ShadowJar.manifest` instead. The class will be removed in Shadow 10.

### Fixed

- Fix `ManifestResourceTransformer.manifestEntries` value type to `Any` and support CC. (#2198)
- Avoid overwriting entries that differ only by case on case-insensitive filesystems. (#2213)
- Fix `ManifestAppenderTransformer` clearing attributes on transform. (#2220)

## [9.6.1](https://github.com/GradleUp/shadow/releases/tag/9.6.1) - 2026-07-22

### Changed

- Use `GradleException` for expected build failures. (#2113)

### Fixed

- Preserve repeated lines in R8 rule files when using `minimize { r8 { ... } }`. (#2115)

## [9.6.0](https://github.com/GradleUp/shadow/releases/tag/9.6.0) - 2026-07-16

### Added

- Extract R8 rules from dependency JARs when using `minimize { r8 { ... } }`. (#2089)

### Changed

- Rename `ShadowDslMarker` to `ShadowDsl`. (#2091)
- **POTENTIALLY BREAKING:** Apply `@ShadowDsl` to `ShadowJar`, `ResourceTransformer`, `DependencyFilter`, and
  `Relocator`. (#2090)  
  This restricts nested DSL configuration blocks from implicitly calling outer receiver APIs in Kotlin script files.

### Fixed

- Validate ZIP entry names to prevent Zip Slip path traversal.
- Avoid resolving unused R8 dependency. (#2101)

## [9.5.1](https://github.com/GradleUp/shadow/releases/tag/9.5.1) - 2026-07-06

### Fixed

- Fix eager calls for `toolchainSpec` in Kotlin DSL. (#2087)

## [9.5.0](https://github.com/GradleUp/shadow/releases/tag/9.5.0) - 2026-07-06

!!! note

    With the introduction of `DuplicatesStrategy` checking for transformers, you may see warnings like:

    ```
    'META-INF/...kotlin_module' is matched by com.github.jengelman.gradle.plugins.shadow.transformers.KotlinModuleMetadataTransformer but its DuplicatesStrategy is EXCLUDE — duplicates may be silently dropped before the transformer processes them.
    ```

    If you do not need Kotlin module metadata remapping, you can disable it:

    ```
    tasks.shadowJar {
      @Suppress("DEPRECATION") // This flag will be disabled and removed in the next major version of Shadow.
      enableKotlinModuleRemapping = false
    }
    ```

### Added

- Check `DuplicatesStrategy` for merging transformers. (#2026)  
  This will log warnings when an incompatible `DuplicatesStrategy` (e.g., `EXCLUDE`) is applied in Gradle configuration
  for built-in `ResourceTransformer`s.
- Add `KotlinModuleMetadataTransformer`. (#2073)
- Add R8 as an opt-in `minimize { r8 { ... } }` tool for shrinking the final shadowed JAR. (#2077)

### Changed

- Bump min Gradle requirement to 9.2.0. (#2057)
- Remove `afterEvaluate` when adding variants. (#2056)

### Deprecated

- Deprecate `enableKotlinModuleRemapping` for `ShadowJar`. (#2073)  
  Apply `KotlinModuleMetadataTransformer` explicitly to support relocating inside Kotlin module metadata files. This
  flag will be disabled and removed in the next major release.
- Deprecate everything under `ShadowCopyAction`. (#2083)

### Fixed

- Fix the conflicts when using `afterEvaluate` with other plugins. (#2055)

## [9.4.3](https://github.com/GradleUp/shadow/releases/tag/9.4.3) - 2026-06-26

### Changed

- Update dependencies for resolving CVEs. (#2069)

## [9.4.2](https://github.com/GradleUp/shadow/releases/tag/9.4.2) - 2026-05-28

### Changed

- Update jdependency to support Java 27. (#2033)

## [9.4.1](https://github.com/GradleUp/shadow/releases/tag/9.4.1) - 2026-03-27

### Changed

- Update Kotlin to 2.3.20. (#1978)

## [9.4.0](https://github.com/GradleUp/shadow/releases/tag/9.4.0) - 2026-03-15

### Added

- Support Isolated Projects. (#1139)

### Changed

- Allow opting out of adding `shadowJar` into `assemble` lifecycle. (#1939)
  ```kotlin
  shadow {
    // Disable making `assemble` task depend on `shadowJar`. This is enabled by default.
    addShadowJarToAssembleLifecycle = false
  }
  ```
- Stop catching `ZipException` when writing entries. (#1970)

### Fixed

- Fix interaction with Gradle artifact transforms. (#1345)
- Fix `skipStringConstants` per-relocator behavior in `mapName`. (#1968)
- Fix failing for non-existent class directories. (#1976)

## [9.3.2](https://github.com/GradleUp/shadow/releases/tag/9.3.2) - 2026-02-27

### Changed

- Stop moving `gradleApi` dependency from `api` to `compileOnly` for Gradle 9.4+. (#1919)
- Log warnings for duplicates in the final JAR. (#1931)

### Fixed

- Fix relocation patterns not included in task fingerprint. (#1933)

## [9.3.1](https://github.com/GradleUp/shadow/releases/tag/9.3.1) - 2026-01-06

### Fixed

- Use ASM from jdependency embedded. (#1898)  
  This fixes potential classpath conflicts when using Shadow with other plugins that also use ASM.

## [9.3.0](https://github.com/GradleUp/shadow/releases/tag/9.3.0) - 2025-12-05

### Added

- Add `PatternFilterableResourceTransformer` to simplify pattern based `ResourceTransformer`s. (#1849)
- Expose `patternSet` of `ServiceFileTransformer` as `public`. (#1849)
- Expose `patternSet` of `ApacheLicenseResourceTransformer` as `public`. (#1850)
- Expose `patternSet` of `ApacheNoticeResourceTransformer` as `public`. (#1850)
- Expose `patternSet` of `PreserveFirstFoundResourceTransformer` as `public`. (#1855)
- Support overriding output path of `ApacheNoticeResourceTransformer`. (#1851)
- Add new merge strategy `Fail` to `PropertiesFileTransformer`. (#1856)
- Add `FindResourceInClasspath` task to help with debugging issues with merged duplicate resources. (#1860)
- Add `MergeLicenseResourceTransformer`. (#1858)
- Add `DeduplicatingResourceTransformer` to deduplicate on path _and_ content. (#1859)
- Support disabling Kotlin module metadata remapping. (#1875)
  ```kotlin
  tasks.shadowJar {
    // Disable remapping of Kotlin module metadata (`.kotlin_module`) files. This is enabled by default.
    enableKotlinModuleRemapping = false
  }
  ```

### Changed

- Change the group of `startShadowScripts` from `application` to `other`. (#1797)
- Update ASM and jdependency to support Java 26. (#1799)
- Bump min Gradle requirement to 9.0.0. (#1801)
- Make the output of `PropertiesFileTransformer` reproducible. (#1861)

### Deprecated

- Deprecate `PreserveFirstFoundResourceTransformer.resources`. (#1855)
- Deprecate `ShadowCopyAction`. (#1876)  
  It should not be used as a public API. Will be made internal in a future release.

### Fixed

- Fix Develocity integration when Isolated Projects enabled. (#1836)

## [9.2.2](https://github.com/GradleUp/shadow/releases/tag/9.2.2) - 2025-09-26

### Fixed

- Fix the regression of registering `ShadowJar` tasks without `ShadowPlugin` applied. (#1787)

## [9.2.1](https://github.com/GradleUp/shadow/releases/tag/9.2.1) - 2025-09-24

### Added

- Support relocating Groovy extensions in Module descriptors. (#1705)
- Add extensions for `Iterable<Relocator>`. (#1710)
- Support relocating list of types in `RelocatorRemapper`. (#1714)
- Add `mainClass` property into `ShadowJar`. (#1722)
  ```kotlin
  tasks.shadowJar {
    // This property will be used as a fallback if there is no explicit `Main-Class` attribute set.
    mainClass = "my.Main"
  }
  ```
- Honor `executableDir` and `applicationName` in `application` extension. (#1740)  
  This is useful when you want to customize the output directory of the start scripts and the application distribution.
- Provide more task accessors in `ShadowApplicationPlugin.Companion`. (#1771)
- Support relocating Kotlin module files. (#1539)  
  The current implementation relocates all properties in `KotlinModuleMetadata` but `KmModule.optionalAnnotationClasses`
  due to very limited usage of it. See more
  discussion [here](https://github.com/GradleUp/shadow/pull/1539#discussion_r2344237151).
- Allow overriding `BUNDLING_ATTRIBUTE` in GMM. (#1773)  
  The `org.gradle.dependency.bundling` in shadowed JAR's Gradle Module Metadata is set to `shadowed` by default. You can
  override it for now by:
  ```kotlin
  shadow {
    bundlingAttribute = Bundling.EMBEDDED
  }
  ```

### Changed

- Merge Groovy Module descriptors into the modern `META-INF` path. (#1706)  
  The Groovy Module descriptors (`org.codehaus.groovy.runtime.ExtensionModule` files) defined under `META-INF/services/`
  and `META-INF/groovy` will be merged into `META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule`.
- Move injecting `Class-Path` manifest attr logic from `doFirst` into `copy`. (#1720)
- Move injecting `Main-Class` manifest attr logic from `doFirst` into `copy`. (#1724)
- Use default `JavaExec` error message when main class is not set. (#1725)
- Update `RelocatorRemapper` class pattern to cover more Java method descriptors. (#1731)
- Stop using start script templates bundled in Shadow. (#1738)
- Bump min Java requirement to 17. (#1744)
- Require most optional properties non-null. (#1745)
- Make assemble depend on shadowJar even if it is added later. (#1766)

### Deprecated

- Deprecate `InheritManifest` and `inheritFrom`. (#1722)
  ```kotlin
  tasks.shadowJar {
    // Before (deprecated):
    manifest.inheritFrom(tasks.jar.get().manifest)

    // After (recommended):
    manifest.from(tasks.jar.get().manifest)

    // Note: You don't need to inherit the manifest from `jar` task as it's done by default for the `shadowJar` task.
    // But if you want to inherit the manifest for your custom `ShadowJar` task, you still need to do it explicitly.
  }
  ```

### Fixed

- Fix excluding dependencies whose versions contain `+`. (#1597)

## [9.1.0](https://github.com/GradleUp/shadow/releases/tag/9.1.0) - 2025-08-29

### Added

- Allow opting out of `shadowRuntimeElements` variant. (#1662)
  ```kotlin
  shadow {
    // Disable publishing `shadowRuntimeElements` as an optional variant of the `java` component.
    addShadowVariantIntoJavaComponent = false
  }

  // configuration must be done in the `afterEvaluate` phase, you cannot access `shadowRuntimeElements` before that.
  val javaComponent = components["java"] as AdhocComponentWithVariants
  javaComponent.withVariantsFromConfiguration(configurations["shadowRuntimeElements"]) {
    // See more details in https://github.com/GradleUp/shadow/pull/1662.
    skip()
  }
  ```
- Allow opting out of `TARGET_JVM_VERSION_ATTRIBUTE`. (#1674)
  ```kotlin
  shadow {
    // Disable adding `TargetJvmVersion` attribute into the Gradle Module Metadata of the shadowed jar.
    addTargetJvmVersionAttribute = false
  }
  ```
- Allow opting out of `Multi-Release` attribute. (#1675)
  ```kotlin
  tasks.shadowJar {
    // Disable adding `Multi-Release` attribute into the manifest of the shadowed jar.
    addMultiReleaseAttribute = false
  }
  ```

### Changed

- Don't inject `TargetJvmVersion` attribute when automatic JVM targeting is disabled. (#1666)
- Do not write modified class files for no-op relocations. (#1694)
- **BREAKING CHANGE:** The introduction of some `afterEvaluate` usages may cause configuration issues in rare cases.

## [9.0.2](https://github.com/GradleUp/shadow/releases/tag/9.0.2) - 2025-08-15

### Fixed

- Fix missing space in `ApacheNoticeResourceTransformer` preamble causing malformed NOTICE header. (#1623)
- Fix using `ApacheNoticeResourceTransformer` without `projectName`. (#1627)
- Fix extra indents of `ApacheNoticeResourceTransformer` output. (#1628)
- Fix resolving BOM dependencies when `minimize` is enabled. (#1637)

## [9.0.1](https://github.com/GradleUp/shadow/releases/tag/9.0.1) - 2025-08-09

!!! note

    If you are upgrading from 8.x versions, please read 9.0.0 release notes first.

!!! tip

    You can diff the shadowed JARs when upgrading from 8.x to 9.x by using [Diffuse](https://github.com/JakeWharton/diffuse).  
    If there are any things missing in the changelog or the doc site, please report them to us.

### Changed

- Improve the error message for empty `mainClassName`. (#1601)
- Default `duplicatesStrategy` back to `EXCLUDE`. (#1617)
  - This strategy is consistent with 8.x series behavior, which is more compatible for most users upgrading.
  - For most `ResourceTransformer` users, you need to override the strategy to `INCLUDE` to make them work.
  - Strongly suggest declaring the `duplicatesStrategy` explicitly in your `ShadowJar` configuration to avoid confusion.
  - See more details about the strategies
    at [Handling Duplicates Strategy](https://gradleup.com/shadow/configuration/merging/#handling-duplicates-strategy).

### Fixed

- Fix the regression of can't shadow directory inputs. (#1606)
- Fix the regression of `MinimizeDependencyFilter`. (#1611)

## [9.0.0](https://github.com/GradleUp/shadow/releases/tag/9.0.0) - 2025-08-07

!!! warning

    This release is a major update from the 8.x series. The plugin has been fully rewritten in Kotlin, bringing
    significant improvements to maintainability, performance, and future extensibility. It introduces many new features,
    enhancements, and bug fixes, and includes several breaking changes. Please review the changelog carefully and consult
    the [new doc site](https://gradleup.com/shadow/) before upgrading.  

    *If you really don't want to upgrade, you can still use the 8.3.x, which is also Gradle 9 compatible. But no additional features or crucial bug fixes will be included in the 8.x line.*

!!! tip

    You can diff the shadowed JARs when upgrading from 8.x to 9.x by using [Diffuse](https://github.com/JakeWharton/diffuse).  
    If there are any things missing in the changelog or the doc site, please report them to us.

!!! note

    Release notes for 9.0.0 beta and rc versions are available on [GitHub Releases](https://github.com/GradleUp/shadow/releases).

### Added

- Add .md support to the Apache License and Notice transformers. (#1041)
- Sync `SimpleRelocator` changes from maven-shade-plugin. (#1076)
- Support configuring `separator` in `AppendingTransformer`. (#1169)  
  This is useful for handling files like `resources/application.yml`.
- Exclude `module-info.class` in Multi-Release folders by default. (#1177)
- Inject `TargetJvmVersion` attribute for Gradle Module Metadata. (#1199)
- Sync `ShadowApplicationPlugin` with `ApplicationPlugin`. (#1224)
- Inject `Multi-Release` manifest attribute if any dependency contains it. (#1239)
- Mark `Transformer` as throwing `IOException`. (#1248)
- Reduce duplicate `SimpleRelocator` to improve performance. (#1271)
- Compat Kotlin Multiplatform plugin. (#1280)
- Add Kotlin DSL examples in docs. (#1306)
- Support using type-safe dependency accessors in `ShadowJar.dependencies`. (#1322)
- Support command line options for `ShadowJar`. (#1365)
  ```
  --enable-auto-relocation          Enables auto relocation of packages in the dependencies.
  --no-enable-auto-relocation       Disables option --enable-auto-relocation.
  --fail-on-duplicate-entries       Fails build if the ZIP entries in the shadowed JAR are duplicate.
  --no-fail-on-duplicate-entries    Disables option --fail-on-duplicate-entries.
  --minimize-jar                    Minimizes the jar by removing unused classes.
  --no-minimize-jar                 Disables option --minimize-jar.
  --relocation-prefix               Prefix used for auto relocation of packages in the dependencies.
  --rerun                           Causes the task to be re-run even if up-to-date.
  ```
- Support skipping string constant remapping. (#1401)
- Let `assemble` depend on `shadowJar`. (#1524)
- Fail build when inputting AAR files or using Shadow with AGP. (#1530)
- Add `PreserveFirstFoundResourceTransformer`. (#1548)  
  This is useful when you set `shadowJar.duplicatesStrategy = DuplicatesStrategy.INCLUDE` and want to ensure that only
  the first found resource is included in the final JAR.
- Fail build if the ZIP entries in the shadowed JAR are duplicate. (#1552)  
  This feature is controlled by the `shadowJar.failOnDuplicateEntries` property, which is `false` by default.  
  Related to setting `duplicatesStrategy = DuplicatesStrategy.FAIL` but there are some differences:
  - It only checks the entries in the shadowed jar, not the input files.
  - It works with setting `duplicatesStrategy` to any value.
  - It provides a stricter fallback check before the JAR is created.

### Changed

- **BREAKING CHANGE:** Rewrite this plugin in Kotlin. (#1012)
- **BREAKING CHANGE:** Migrate `Transformer`s to using lazy properties. (#1036)
- **BREAKING CHANGE:** Migrate `ShadowJar` to using lazy properties. (#1044)
- **BREAKING CHANGE:** Resolve `Configuration` directly in `DependencyFilter`. (#1045)
- **BREAKING CHANGE:** Migrate `SimpleRelocator` to using lazy properties. (#1047)
- **BREAKING CHANGE:** Some public getters have been updated in `SimpleRelocator`. (#1079)
- **BREAKING CHANGE:** Migrate all `ListProperty` usages to `SetProperty`. (#1103)  
  Some public `List` parameters are also changed to `Set`.
- **BREAKING CHANGE:** Mark `RelocatorRemapper` as `internal`. (#1227)
- **BREAKING CHANGE:** Bump min Java requirement to 11. (#1242)
- **BREAKING CHANGE:** Move tracking unused classes logic out of `ShadowCopyAction`. (#1257)
- **BREAKING CHANGE:** Move `DependencyFilter` into `tasks` package. (#1272)
- **BREAKING CHANGE:** Change the default `duplicatesStrategy` from `EXCLUDE` to `INCLUDE`. (#1233)
  - `ShadowJar` recognized `EXCLUDE` as the default, but the other strategies didn't work properly.
  - Now `ShadowJar` honors `INCLUDE` as the default, and aligns all the strategy behaviors with the Gradle side.
  - Some `ResourceTransformer`s (e.g. `ServiceFileTransformer`) do not work with `EXCLUDE`, as it will exclude duplicate
    resources to be merged.
  - Duplicate entries might be bundled due to this change, but you can reduce them by using the newly added
    `PreserveFirstFoundResourceTransformer`.
  - Use `filesMatching` to override the default strategy for specific files.
  - Set `failOnDuplicateEntries = true` to fail the build to check for duplicate entries.
  - See more details
    at [Handling Duplicates Strategy](https://gradleup.com/shadow/configuration/merging/#handling-duplicates-strategy).
  - **Note:** The default `duplicatesStrategy` is changed back to `EXCLUDE` in 9.0.1 release.
- **BREAKING CHANGE:** Align the behavior of `ShadowTask.from` with Gradle's `AbstractCopyTask.from`. (#1233)  
  In the previous versions, `ShadowTask.from` would always unzip the files before processing them, which caused serial
  issues that are hard to fix. Now it behaves like Gradle's `AbstractCopyTask.from`, which means it will not unzip the
  files, only copy the files as-is. If you still want to shadow the unzipped files, try out something like:
  ```kotlin
  tasks.shadowJar {
    // Unzip the files before pass them to `from` by using `zipTree`.
    from(zipTree(files("path/to/your/file.zip")))
  }
  ```
  or
  ```groovy
  dependencies {
    // Add the files to `implementation` configuration, Shadow will unzip them automatically.
    implementation(files('path/to/your/file.zip'))
  }
  ```
- **BREAKING CHANGE:** Rename `Transformer` to `ResourceTransformer`. (#1288)  
  Aims to better align with the name `org.apache.maven.plugins.shade.resource.ResourceTransformer.java`
  and to distinguish itself from `org.gradle.api.Transformer.java`.
- **BREAKING CHANGE:** Mark `DefaultInheritManifest` as `internal`. (#1303)
- **BREAKING CHANGE:** Polish `ShadowSpec`. (#1307)
  - Return values of `ShadowSpec` functions are changed to `Unit` to avoid confusion.
  - `ShadowSpec` no longer extends `CopySpec`.
  - Overload `relocate`, `transform` and things for better usability in Kotlin.
- **BREAKING CHANGE:** Remove redundant types from function returning. (#1308)
- **BREAKING CHANGE:** Rename `ShadowJar`'s `isEnableRelocation` to `enableAutoRelocation`. (#1541)
- **BREAKING CHANGE:** Some const values in `ShadowBasePlugin` and `ShadowJavaPlugin` are moved. (#1589)  
  You can find them in `ShadowJar`, `ShadowApplicationPlugin`, and `ShadowJavaPlugin`.
- Replace deprecated `SelfResolvingDependency` with `FileCollectionDependency`. (#1114)
- Update start script templates. (#1183)
- Mark more `Transformer`s cacheable. (#1210)
- Mark `ShadowJar.dependencyFilter` as `@Input`. (#1206)
- Polish `startShadowScripts` task registering. (#1216)
- Refactor file visiting logic in `StreamAction`, handle file unzipping via `Project.zipTree`. (#1233)
- Migrate doc sites to MkDocs. (#1302)
- `runShadow` no longer depends on `installShadowDist`. (#1353)
- Move the group of `ShadowJar` from `shadow` to `build`. (#1355)
- In-development snapshots are now published to the Central Portal Snapshots repository. (#1414)
- Expose `AbstractDependencyFilter` from `internal` to `public`. (#1538)  
  You can access it via `com.github.jengelman.gradle.plugins.shadow.tasks.DependencyFilter.AbstractDependencyFilter`.
- Mark `Action` parameters as non-null. (#1555)
- Use `BufferedOutputStream` when writing the Zip file to improve performance. (#1580)

### Fixed

- Fix single Log4j2Plugins.dat isn't included into fat jar. (#1039)
- Fail builds if processing bad jars. (#1146)
- Fix `Log4j2PluginsCacheFileTransformer` not working for merging `Log4j2Plugins.dat` files. (#1175)
- Support overriding `mainClass` provided by `JavaApplication`. (#1182)
- Fix `ShadowJar` not being successful after `includes` or `excludes` are changed. (#1200)
- Honor `DuplicatesStrategy`. (#1233)
- Honor unzipped jars via `from`. (#1233)
- Fix the last modified time of shadowed directories. (#1277)
- Fix relocation exclusion for file patterns like `kotlin/kotlin.kotlin_builtins`. (#1313)
- Allow using file trees of JARs together with the configuration cache. (#1441)

### Removed

- **BREAKING CHANGE:** Some public getters and setters have been removed in `SimpleRelocator`. (#1079)
- **BREAKING CHANGE:** Remove `JavaJarExec`, now use `JavaExec` directly for `runShadow` task. (#1197)
- **BREAKING CHANGE:** `ServiceFileTransformer.ServiceStream` has been removed. (#1218)
- **BREAKING CHANGE:** Remove `KnowsTask` as it's useless. (#1236)
- **BREAKING CHANGE:** Remove `BaseStreamAction`. (#1258)
- **BREAKING CHANGE:** Remove `ShadowStats`. (#1264)
- **BREAKING CHANGE:** Remove `ShadowCopyAction.ArchiveFileTreeElement` and `RelativeArchivePath`. (#1233)
- **BREAKING CHANGE:** Remove `TransformerContext.getEntryTimestamp`. (#1245)
- **BREAKING CHANGE:** Reduce dependency and project overloads in `DependencyFilter`. (#1328)
- **BREAKING CHANGE:** Remove `ShadowSpec`. (#1560)
- **BREAKING CHANGE:** Remove `Relocator.ROLE`. (#1563)
- **BREAKING CHANGE:** Remove deprecated `ShadowExtension.component`. (#1586)

### Migration Example

**8.x**

```kotlin
tasks.shadowJar {
  isEnableRelocation = true
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  mergeServiceFiles()
  from("foo.jar")
}
```

**9.x**

```kotlin
tasks.shadowJar {
  // `isEnableRelocation` has been renamed to `enableAutoRelocation`.
  enableAutoRelocation = true

  // If you want to make `mergeServiceFiles` or most resource transformers work, you should set the `duplicatesStrategy` to `INCLUDE`.
  // Because `EXCLUDE` will exclude extra service files to be merged.
  duplicatesStrategy = DuplicatesStrategy.INCLUDE
  mergeServiceFiles()
  // Optionally, you can enable the new `failOnDuplicateEntries` property to fail the build if there are duplicate entries.
  failOnDuplicateEntries = true

  // If you want to keep the `foo.jar` as-is (zipped), you can use the `from` method directly. This is different from the previous.
  from("foo.jar")
  // If you want to unzip the `foo.jar` before processing, you can use `zipTree` to unzip it.
  from(zipTree("foo.jar"))
}
```

If you used Shadow for merging service files, the following steps are recommended:

1. Make sure to leave `duplicatesStrategy` as `INCLUDE` or `WARN`.
2. Apply `mergeServiceFiles` or `ServiceFileTransformer` stuff as you did in your previous setup.
3. Diff the JARs from upgrading or not.
4. Remove the extra entries that are added by `INCLUDE` by `eachFile`, `filesMatching`, or
   `PreserveFirstFoundResourceTransformer`.
5. Diff the JARs again, and check that only the entries you want to preserve remain.
6. Optionally, if you want a stricter check for the shadowed JAR entries, enable `failOnDuplicateEntries`. This can also
   ensure the regressions are caught in the future.

See more details about the fixed `DuplicatesStrategy` behaviors
at [Handling Duplicates Strategy](https://gradleup.com/shadow/configuration/merging/#handling-duplicates-strategy).

## [8.3.11](https://github.com/GradleUp/shadow/releases/tag/8.3.11) - 2026-05-28

!!! warning

    Only Gradle 9 support is being backported to this version. No additional features or crucial bug fixes will be
    included in the 8.x line. Please migrate to Shadow 9 as soon as possible.

### Changed

- Update jdependency to support Java 27. (#2040)

### Deprecated

- Deprecate `KnowsTask`, it will be removed in the next major release. (#1957)

### Fixed

- Fix compatibility with Isolated Projects. (#1947)
- Fix interaction with Gradle artifact transforms. (#1949)
- Fix `Log4j2PluginsCacheFileTransformer` not working for merging `Log4j2Plugins.dat` files. (#1955)

## [8.3.10](https://github.com/GradleUp/shadow/releases/tag/8.3.10) - 2026-02-26

### Changed

- Stop using start script templates bundled in Shadow. (#1750)
- Update ASM and jdependency to support Java 26. (#1810)

### Fixed

- Fix resolving BOM dependencies when `minimize` is enabled. (#1638)
- Use ASM from jdependency embedded. (#1898)  
  This fixes potential classpath conflicts when using Shadow with other plugins that also use ASM.

## [8.3.9](https://github.com/GradleUp/shadow/releases/tag/8.3.9) - 2025-08-05

### Changed

- Use `BufferedOutputStream` when writing the Zip file to improve performance. (#1579)

## [8.3.8](https://github.com/GradleUp/shadow/releases/tag/8.3.8) - 2025-07-01

### Fixed

- Fix the regression of `PropertiesFileTransformer` in `8.3.7`. (#1493)

### Changed

- Expose Ant as `compile` scope. (#1488)

## [8.3.7](https://github.com/GradleUp/shadow/releases/tag/8.3.7) - 2025-06-24

### Fixed

- Fix compatibility for Gradle 9.0.0 RC1. (#1470)

## [8.3.6](https://github.com/GradleUp/shadow/releases/tag/8.3.6) - 2025-02-02

### Added

- Support Java 24. (#1222)

## [8.3.5](https://github.com/GradleUp/shadow/releases/tag/8.3.5) - 2024-11-03

### Fixed

- Revert "Bump Java level to 11". (#1011)  
  This reverts the change to maintain compatibility with 8.x versions. The Java level will be bumped to 11 or above in
  the next major release.

## [8.3.4](https://github.com/GradleUp/shadow/releases/tag/8.3.4) - 2024-10-29

### Fixed

- Apply legacy plugin last, and declare capabilities for old plugins, fixes #964. (#991)

## [8.3.3](https://github.com/GradleUp/shadow/releases/tag/8.3.3) - 2024-10-02

### Changed

- Disable Develocity integration by default. (#993)

## [8.3.2](https://github.com/GradleUp/shadow/releases/tag/8.3.2) - 2024-09-18

### Added

- Support Java 23. (#974)

### Changed

- **BREAKING CHANGE:** update to [jdependency 2.11](https://github.com/tcurdt/jdependency/releases/tag/jdependency-2.11),
  this requires Java 11 or above to run. (#974)

### Deprecated

- `ShadowExtension.component` has been deprecated, now you can use `component.shadow`
  instead. (#956)

### Fixed

- Stop publishing Shadow self fat jar to Maven repository. (#967)

## [8.3.1](https://github.com/GradleUp/shadow/releases/tag/8.3.1) - 2024-09-10

### Added

- Apply an empty plugin that has the legacy `com.github.johnrengelman.shadow` plugin ID. This allows existing build
  logic to keep on reacting to the legacy plugin as the replacement is drop-in currently.

### Fixed

- Explicitly add classifier to maven publication. (#904)
- Refix excluding Gradle APIs for java-gradle-plugin. (#948)

## [8.3.0](https://github.com/GradleUp/shadow/releases/tag/8.3.0) - 2024-08-08

### Changed

- **BREAKING CHANGE:** the GitHub has been transferred from `johnrengelman/shadow` to `GradleUp/shadow`, you can view
  more details in #908.  
  We also update the plugin ID from `com.github.johnrengelman.shadow` to `com.gradleup.shadow`, and the Maven coordinate
  from `com.github.johnrengelman:shadow` to `com.gradleup.shadow:shadow-gradle-plugin`.
- Bump the min Gradle requirement from `8.0.0` to `8.3`. (#876)
- Support Java 21. (#876)
- Use new file permission API from Gradle 8.3. (#876)

### Fixed

- Fix for PropertiesFileTransformer breaks Reproducible builds in
  `8.1.1`. (#858)

## [8.1.1](https://github.com/GradleUp/shadow/releases/tag/8.1.1) - 2023-03-20

!!! note

    As of this version, the GitHub repository has migrated to the `main` branch as the default branch for
    releases.

### What's Changed

- Replace deprecated ConfigureUtil by @Goooler in #826
- Polish outdated configs by @Goooler in #831
- Update plugin com.gradle.enterprise to v3.12.5 by @renovate-bot in #838
- Update dependency gradle to v8.0.2 by @renovate-bot in #844
- fix(deps): update dependency org.codehaus.plexus:plexus-utils to v3.5.1 by @renovate-bot in #837
- chore(deps): update dependency prismjs to v1.27.0 [security] by @renovate-bot in #828
- Encode transformed properties files with specified Charset by @scottsteen in #819
- chore(deps): update dependency vuepress to v1.9.9 by @renovate-bot in #842

### New Contributors

- @renovate-bot made their first contribution in #838
- @scottsteen made their first contribution in #819

**Full Changelog**: [`8.1.0...8.1.1`](https://github.com/GradleUp/shadow/compare/8.1.0...8.1.1)

## [8.1.0](https://github.com/GradleUp/shadow/releases/tag/8.1.0) - 2023-02-26

**BREAKING CHANGE:** Due to adoption of the latest version of the `com.gradle.plugin-publish` plugin, the maven GAV
coordinates have changed as of this version. The correct coordinates now align with the plugin ID itself:
`group=com.github.johnrengelman, artifact=shadow, version=<version>`. For example,
`classpath("com.github.johnrengelman:shadow:8.1.0")` is the correct configuration for this version.

**BREAKING CHANGE:** The `ConfigureShadowRelocation` task was removed as of this version to better support Gradle
configuration caching. Instead, use the `enableRelocation = true` and `relocationPrefix = "<new package>"` settings on
the `ShadowJar` task type.

### What's Changed

- Minor cleanups by @Goooler in #823
- Support config cache by @Goooler in #824
- Fix RelocatorRemapper: do not map inner class name if not changed by @Him188 in #793

### New Contributors

- @Him188 made their first contribution in #793

**Full Changelog**: [`8.0.0...8.1.0`](https://github.com/GradleUp/shadow/compare/8.0.0...8.1.0)

## [8.0.0](https://github.com/GradleUp/shadow/releases/tag/8.0.0) - 2023-02-24

### What's Changed

- Fix the plugin dependency identifier in the docs by @lnhrdt in #754
- mergeGroovyExtensionModules() not working with Groovy 2.5+ by @paulk-asert in #779
- Upgrade to ASM 9.3 to support JDK 19. by @vyazelenko in #770
- Do not add a dependencies block if it's already there by @desiderantes in #769
- Update README with new badge and links by @ThexXTURBOXx in #743
- Fix value not set when rawString is true. by @qian0817 in #765
- Mark the Log4j2PluginsCacheFileTransformer as cacheable. by @staktrace in #724
- Fix retrieval of dependencies node when publishing by @netomi in #798
- Upgrade dependency ASM from `9.3` to `9.4` by @codecholeric in #817
- Fix a typo of code comment in the minimizing page by @jebnix in #800
- Prefer using plugin extensions over deprecated conventions by @eskatos in #821
- Introduce CleanProperties by @simPod in #622
- Support Gradle 8.0 by @Goooler in #822
- Updated dependencies, Gradle versions and Fix Test by @ElisaMin in #791

### New Contributors

- @lnhrdt made their first contribution in #754
- @paulk-asert made their first contribution in #779
- @desiderantes made their first contribution in #769
- @ThexXTURBOXx made their first contribution in #743
- @qian0817 made their first contribution in #765
- @staktrace made their first contribution in #724
- @netomi made their first contribution in #798
- @codecholeric made their first contribution in #817
- @jebnix made their first contribution in #800
- @eskatos made their first contribution in #821
- @simPod made their first contribution in #622
- @Goooler made their first contribution in #822
- @ElisaMin made their first contribution in #791

**Full Changelog**: [`7.1.2...8.0.0`](https://github.com/GradleUp/shadow/compare/7.1.2...8.0.0)

## [7.1.2](https://github.com/GradleUp/shadow/releases/tag/7.1.2) - 2021-12-28

- Upgrade log4j to 2.17.1 due to CVE-2021-45105 and CVE-2021-44832

## [7.1.1](https://github.com/GradleUp/shadow/releases/tag/7.1.1) - 2021-12-14

- Upgrade log4j to 2.16.0 due to CVE-2021-44228 and CVE-2021-45046

## [7.1.0](https://github.com/GradleUp/shadow/releases/tag/7.1.0) - 2021-10-04

- **BREAKING** - The maven coordinates for the plugins have changed as of this version. The proper `group:artifact` is
  `gradle.plugin.com.github.johnrengelman:shadow`
- Fix `shadowJar` Out-Of-Date with configuration caching #708 by @mathjeff
- Better support for statically typed languages. This change may require code changes if you are utilizing the Groovy
  generated getters for properties in some Shadow transformers #706 by @Fiouz
- Various cleanups #672, #700, #701, #702 by @helfper
- Support JVM Toolchains #691 by @rpalcolea
- Fix `Project.afterEvaluate` conflicts #675 by @mjulianotq
- Fix relocation for `ComponentsXmlResourceTransformer` #678 by @ileasile
- Fix `JavaExec.main` deprecation #686 by @rieske
- Support Java 18 with ASM 9.2 #698 by @vyazelenko
- Support Records with JDependency 2.7.0 #681 by @jpenilla

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

- Support Gradle 7 #624 by @melix
- Close `FileInputStream` when remapping close to avoid classloader locks #642 by @ghost
- Groovy error in `ServiceFileTransformer` in Gradle 3 #655 by @maxm123
- Fix deprecations errors in transformers and add CI testing around future deprecations #647 by @helfper
- Handle deprecation of `mainClassName` configuration #609, #612 by @nhumblot
- Exclude `api` and `implementations` from legacy `maven` POM #615 by @bschelberg

## [6.1.0](https://github.com/GradleUp/shadow/releases/tag/6.1.0) - 2020-10-05

- As of this version, Shadow is compiled with Java 8 source and target compatibility. This aligns the plugin with the
  minimum required Java version for Gradle 6.0 (https://docs.gradle.org/6.0/release-notes.html).
- Update ASM to 9.0 to support JDK 16.
- Enable Configuration Caching for Gradle 6.6+ #591 by @timyates, @britter
- doc updates #593 by @MuffinTheMan
- log4j version update for CVE-2020-9488 #590 by @ysb33r
- Input stream handling for large projects #587 by @roxchkplusony
- Implement Task Configuration Avoidance pattern #597 by @3flex

## [6.0.0](https://github.com/GradleUp/shadow/releases/tag/6.0.0) - 2020-06-15

- Required Gradle 6.0+
- _NEW_: Support for Gradle Metadata publication via the `shadowRuntimeElements` configuration. This is a _beta_ feature
  the hasn't been tested extensively. Feedback is appreciated.
- Fix Gradle 7 deprecation warnings #530
- Fix to generated start script to correctly use
  `optsEnvironmentVar` #518
- Fix issues with Gradle API being embedded into published JAR #527 by @Tapchicoma
- ASM updates to support latest Java versions #549 by @vyazelenko
- Support exposing shadowed project dependencies via POM #543 by @ejjcase
- Performance optimizations #535 by @Armaxis
- Fix exclude patterns on Windows #539 by @trask
- Allow usage of true regex patterns for include/exclude by the `%regex[<pattern>]` syntax #536 by @Armaxis

## [5.2.0](https://github.com/GradleUp/shadow/releases/tag/5.2.0) - 2019-11-10

- Performance optimization when evaluating relocation paths #507 by @inez
- Fix remapping issues with multi release JARS #526 by @jeffalder
- Implement support for Gradle build cache #524 by @ghale
- Gradle 6.x support #517 by @rpalcolea
- Return support for 5.0 for convention mapping #502 by @grossws
- Documentation updates on how to reconfigure `classifier` and `version` #512 by @jianglai

## [5.1.0](https://github.com/GradleUp/shadow/releases/tag/5.1.0) - 2019-06-29

- Add `ManifestAppenderTransformer` to support appending to Jar manifest #474 by @chrisr3
- Additional escaping fixes in start script #487 by @minkenlai
- Automatically remove `gradleApi` from `compile` scope in the presence of `shadow` #459 by @maguro
- Do not initialize `UnusedTracker` when not requested #480, #479 by @sormuras
- Fix `NullPointerException` when using java minimization and api project dependency with version #477 by @kelemen

## [5.0.0](https://github.com/GradleUp/shadow/releases/tag/5.0.0) - 2019-02-28

- Require Gradle 5.0+
- Fix issue with build classifier `-all` being dropped in Gradle 5.1+
- Exclude project dependencies from minimization #420 by @rpalcolea
- Fix escaping in start script #454, #455 by @kyrrigle, @RichardMarbach
- Fix Gradle 5.2 incompatibility with `ShadowJar.getMetaClass()` #456 by @Hillkorn
- Fix compatibility with `com.palantir.docker` #460 by @bfg

## [4.0.4](https://github.com/GradleUp/shadow/releases/tag/4.0.4) - 2019-01-19

- When using `shadow`, `application`, and `maven` plugins together, remove `shadowDistZip` and `shadowDistTar` from
  `configurations.archives` so they are not published or installed by default with the `uploadArchives` or `install`
  tasks. #347
- Fix `null` path when using Jar minimization and Gradle's `api` configuration. #424, #425 by @JamesXNelson

## [4.0.3](https://github.com/GradleUp/shadow/releases/tag/4.0.3) - 2018-11-21

- Don't leak plugin classes to Gradle's Spec cache #430 by @mark-vieira

## [4.0.2](https://github.com/GradleUp/shadow/releases/tag/4.0.2) - 2018-10-27

- Update to ASM 7.0-beta and jdependency 2.1.1 to support Java 11, #415 by @petarov
- Ensure input streams are closed, #411 by @roxchkplusony
- Exclude `api` configuration from minimization, #405 by @osipxd

## [4.0.1](https://github.com/GradleUp/shadow/releases/tag/4.0.1) - 2018-09-30

- **Breaking Change!** `Transform.modifyOutputStream(ZipOutputStream os)` to
  `Transform.modifyOutputStream(ZipOutputStream jos, boolean preserveFileTimestamps)`. Typically breaking changes are
  reserved for major version releases, but this change was necessary for
  `preserverFileTimestamps` (introduced in v4.0.0) to work correctly in the presence of transformers, #404
- Fix regression in support Java 10+ during relocation, #403

## [4.0.0](https://github.com/GradleUp/shadow/releases/tag/4.0.0) - 2018-09-25

- **Breaking Change!** Restrict Plugin to Gradle 4.0+. Shadow major versions will align with Gradle major versions going
  forward.
- **Breaking Change!** For clarity purposes `com.github.johnrengelman.plugin-shadow` has been removed. If you intend to
  use this feature, you will need to declare your own `ConfigureShadowRelocation` task. See
  section [2.9.2](https://gradleup.com/shadow/#automatically_relocating_dependencies) of the User Guide
- Upgrade to ASM 6.2.1 to support Java 11 by @SerCeMan
- Add support for `shadowJar.preserveFileTimestamps` property.
  See [Jar.preserveFileTimestamps](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:preserveFileTimestamps)
  by @Macil
- Add `Log4j2PluginsCacheFileTransformer` to process Log4j DAT files during merge. by @nikole-dunixi
- Fix the long standing "No property `mainClassName`" issue. by @felipecsl
- Implement JAR minimization actions. This will attempt to exclude unused classes in your shadowed JAR. by @debanne
- Configure exclusion of `module-info.class` from `shadowJar` when using the Shadow the Java plugin, #352

## [2.0.4](https://github.com/GradleUp/shadow/releases/tag/2.0.4) - 2018-04-27

- Update to ASM 6.1.1 to address performance issues - [ASM Issue 317816](https://gitlab.ow2.org/asm/asm/issues/317816)
- Close InputStreams after using them, #364
- Remove usage of Gradle internal `AbstractFileCollection`.
- Add task annotations to remove warnings when validating plugin.

## [2.0.3](https://github.com/GradleUp/shadow/releases/tag/2.0.3) - 2018-03-24

- Update to ASM 6.1 by @ttsiebzehntt
- Fix deprecated Gradle warnings, #356 by @sgnewson

## [2.0.2](https://github.com/GradleUp/shadow/releases/tag/2.0.2) - 2017-12-12

- documentation by @ghost, @tylerbenson
- Support multi-project builds with Build-Scan integration by @mark-vieira
- Upgrade to ASM 6, #294, #303
- Fix integration with `application` plugin in Gradle 4.3, #339 by @rspieldenner
- Fixed deprecation warning from Gradle 4.2+, #326

## [2.0.1](https://github.com/GradleUp/shadow/releases/tag/2.0.1) - 2017-06-23

- Fix `null+configuration` error, #297

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
- Add `keyTransformer` property to `PropertiesFileTransformer` by @marcphilipp
- Update to ASM 5.2
- Support `api`, `implementation`, `runtimeOnly` dependency configurations introdcued in Gradle 3.3 by @pkubowicz

## [1.2.4](https://github.com/GradleUp/shadow/releases/tag/1.2.4) - 2016-11-03

- Don't resolve dependency configurations during config phase, #128
- Build plugin with Gradle 2.14
- Fix docs regarding inheriting Jar manifest, #251
- Support projects that configure uploading to Ivy repositories, #256 by @ethankhall
- Force task to depend on dependency configuration, #152
- Do not explode ZIP files into shadow jar, #196
- Preserve timestamps on merged jar entries, #260 by @jszakmeister

## [1.2.3](https://github.com/GradleUp/shadow/releases/tag/1.2.3) - 2016-01-25

- Support for Gradle 2.11-rc-1, #177
- Convert internal framework to [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html)
- Use BufferedOutputStream when writing the Zip file, #171 by @fkorotkov
- Quote Jar path in Windows start script as it may contain spaces, #170 by @hbchai
- Evaluate relocation specs when merging service descriptors, #165 by @siordache

## [1.2.2](https://github.com/GradleUp/shadow/releases/tag/1.2.2) - 2015-07-17

- Gradle 2.5 compatibility, #147 by @Minecrell

## [1.2.1](https://github.com/GradleUp/shadow/releases/tag/1.2.1) - 2015-01-23

- Apply package relocations to dependency resources, #114

## [1.2.0](https://github.com/GradleUp/shadow/releases/tag/1.2.0) - 2014-11-24

- Re-organize some code to remove need for forcing the Gradle API ClassLoader to allow the `org.apache.tools.zip`
  package.
- Upgrade JDOM library from 1.1 to 2.0.5 (change dependency from `jdom:jdom:1.1` to
  `org.jdom:jdom2:2.0.5`), #98
- Convert ShadowJar.groovy to ShadowJar.java to workaround binary incompatibility introduced by Gradle 2.2, #106
- Updated ASM library to `5.0.3` to support JDK8, #97
- Allows for regex pattern matching in the `dependency` string when including/excluding, #83
- Apply package relocations to resource files, #93

## [1.1.2](https://github.com/GradleUp/shadow/releases/tag/1.1.2) - 2014-09-09

- fix bug in `runShadow` where dependencies from the `shadow` configuration are not available, #94

## [1.1.1](https://github.com/GradleUp/shadow/releases/tag/1.1.1) - 2014-08-27

- Fix bug in `'createStartScripts'` task that was causing it to not execute `'shadowJar'`
  task, #90
- Do not include `null` in ShadowJar Manifest `'Class-Path'` value when `jar` task does not specify a value for it, #92
- ShadowJar Manifest `'Class-Path'` should reference jars from `'shadow'` config as relative to location of `shadowJar`
  output, #91

## [1.1.0](https://github.com/GradleUp/shadow/releases/tag/1.1.0) - 2014-08-26

- **Breaking Change!** Fix leaking of `shadowJar.manifest` into `jar.manifest`, #82.  
  To simplify behavior, the `shadowJar.appendManifest` method has been removed. Replace uses with
  `shadowJar.manifest`
- `ShadowTask` now has a `configurations` property that is resolved to the files in the resolved configuration before
  being added to the copy spec. This allows for an easier implementation for filtering. The default 'shadowJar' task has
  the convention of adding the `'runtime'` scope to this list. Manually created instances of `ShadowTask` have no
  configurations added by default and can be configured by setting `task.configurations`.
- Properly configure integration with the `'maven'` plugin when added. When adding `'maven'` the `'uploadShadow'` task
  will now properly configure the POM dependencies by removing the `'compile'` and `'runtime'` configurations from the
  POM and adding the `'shadow'` configuration as a `RUNTIME` scope in the POM. This behavior matches the behavior when
  using the `'maven-publish'` plugin.
- Allow `ServiceFileTransformer` to specify include/exclude patterns for files within the configured path to merge. by
  @matthurne
- Added `GroovyExtensionModuleTransformer` for merging Groovy Extension module descriptor files. The existing
  `ServiceFileTransformer` now excludes Groovy Extension Module descriptors by default. by @matthurne
- `distShadowZip` and `distShadowZip` now contain the shadow library and run scripts instead of the default from the
  `'application'` plugin, #89

## [1.0.3](https://github.com/GradleUp/shadow/releases/tag/1.0.3) - 2014-07-29

- Make service files root path configurable for
  `ServiceFileTransformer`, #72
- Added PropertiesFileTransformer, #73 by @aalmiray
- Fixed StackOverflow when a cycle occurs in the resolved dependency grap, #69 by @brandonkearby
- Apply Transformers to project resources, #70, #71
- Do not drop non-class files from dependencies when relocation is enabled, #61 by @Minecrell
- Remove support for applying individual sub-plugins by Id (easier maintenance and cleaner presentation in Gradle
  Portal)

## [1.0.2](https://github.com/GradleUp/shadow/releases/tag/1.0.2) - 2014-07-07

- Do not add an empty Class-Path attribute to the manifest when the `shadow` configuration contains no dependencies.
- `runShadow` now registers `shadowJar` as an input. Previously, `runShadow` did not execute `shadowJar` and an error
  occurred.
- Support Gradle 2.0, #66
- Do not override existing 'Class-Path' Manifest attribute settings from Jar configuration. Instead combine, #65

## [1.0.1](https://github.com/GradleUp/shadow/releases/tag/1.0.1) - 2014-06-28

- Fix issue where non-class files are dropped when using relocation, #58
- Do not create a `/` directory inside the output jar.
- Fix `runShadow` task to evaluate the `shadowJar.archiveFile` property at execution time, #60

## [1.0.0](https://github.com/GradleUp/shadow/releases/tag/1.0.0) - 2014-06-27

- Previously known as v0.9.0
- All changes from 0.9.0-M1 to 0.9.0-M5
- Properly configure the ShadowJar task inputs to observe the include/excludes from the `dependencies` block. This
  allows UP-TO-DATE checking to work properly when changing the `dependencies`
  rulea, #54
- Apply relocation remappings to classes and imports in source project, #55
- Do not create directories in jar for source of remapped class, created directories in jar for destination of remapped
  classes, #53

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
- Applying `application` plugin and settings `mainClassName` automatically configures the `Main-Class` attribute in the
  manifest for `shadowJar`
- `runShadow` now utilizes the output of the `shadowJar` and executes using `java -jar <shadow jar file>`
- Start Scripts for shadow distribution now utilize `java -jar` to execute instead of placing all files on classpath and
  executing main class.
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
