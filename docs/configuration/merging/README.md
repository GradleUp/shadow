# Controlling JAR Content Merging

Shadow allows for customizing the process by which the output JAR is generated through the
[`ResourceTransformer`][ResourceTransformer] interface. This is a concept that has been carried over from the original
Maven Shade implementation. A [`ResourceTransformer`][ResourceTransformer] is invoked for each entry in the JAR before
being written to the final output JAR. This allows a [`ResourceTransformer`][ResourceTransformer] to determine if it
should process a particular entry and apply any modifications before writing the stream to the output.

!!! important "Guaranteed Processing Order"

    [`ResourceTransformer`][ResourceTransformer] follows a guaranteed processing order:

    1. **Project files first**: All files in projects are processed before any dependency files.
    2. **Dependency files second**: Files from configurations (runtime dependencies) or added via
       [`ShadowJar.from`][ShadowJar.from] are processed after project files.

    This ordering is crucial when merging configuration files where you want to preserve project-specific values while
    merging in additional data from dependencies.

## Handling Duplicates Strategy

`ShadowJar` is a subclass of [`org.gradle.api.tasks.AbstractCopyTask`][AbstractCopyTask], which means it honors the
`duplicatesStrategy` property as its parent classes do. There are several strategies to handle:

- `EXCLUDE`: Do not allow duplicates by ignoring subsequent items to be created at the same path.
- `FAIL`: Throw a `DuplicateFileCopyingException` when subsequent items are to be created at the same path.
- `INCLUDE`: Do not attempt to prevent duplicates.
- `INHERIT`: Use the same strategy as the parent copy specification.
- `WARN`: Do not attempt to prevent duplicates, but log a warning message when multiple items are to be created at the
  same path.

see more details about them in [`DuplicatesStrategy`][DuplicatesStrategy].

`ShadowJar` recognizes `EXCLUDE` as the default, if you want to change the strategy, you can override it like:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      duplicatesStrategy = DuplicatesStrategy.INCLUDE // Or something else.
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      duplicatesStrategy = DuplicatesStrategy.INCLUDE // Or something else.
    }
    ```

Different strategies will lead to different results for `foo/bar` files in the JARs to be merged:

- `EXCLUDE`: The **first** `foo/bar` file will be included in the final JAR.
- `FAIL`: **Fail** the build with a `DuplicateFileCopyingException` if there are duplicate `foo/bar` files.
- `INCLUDE`: **Duplicate** `foo/bar` entries will be included in the final JAR.
- `INHERIT`: **Inherit** the strategy from the parent copy specification. If explicitly set to `INHERIT` on the root
  task (where no parent specification exists to inherit from), encountering duplicates will fail the build with an
  exception like `Entry .* is a duplicate but no duplicate handling strategy has been set`.
- `WARN`: **Warn** about duplicates in the build log; this behaves exactly as `INCLUDE` otherwise.

!!! note "Precedence of DuplicatesStrategy"

    The `duplicatesStrategy` evaluation takes precedence over transforming and relocating.
    Because `ShadowJar` is a subclass of Gradle's `AbstractCopyTask`, duplicate filtering configured via
    `duplicatesStrategy` is performed at Gradle's `CopySpec` processing layer **before** entries are passed to Shadow's
    internal [`ResourceTransformer`][ResourceTransformer] engine.
    See the [ShadowJar Execution Flow][shadowjar-execution-flow] for the complete lifecycle diagram.

If you mix the usages of `duplicatesStrategy = DuplicatesStrategy.EXCLUDE` and
[`ResourceTransformer`][ResourceTransformer] like below:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE // The default strategy.
      mergeServiceFiles()
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE // The default strategy.
      mergeServiceFiles()
    }
    ```

The [`ResourceTransformer`][ResourceTransformer]s like [`ServiceFileTransformer`][ServiceFileTransformer] will not work
as expected because duplicate resource files are filtered out and dropped by Gradle before reaching the transformer.

If Shadow detects a resource matched by a built-in [`ResourceTransformer`][ResourceTransformer] while its
`duplicatesStrategy` is `EXCLUDE`, it will log a warning during the build:

    'META-INF/services/foo' is matched by
    com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer but its DuplicatesStrategy is
    EXCLUDE — duplicates may be silently dropped before the transformer processes them.
    Set it to INCLUDE or WARN to ensure all duplicates are processed by the transformer.

Want [`ResourceTransformer`][ResourceTransformer]s and `duplicatesStrategy` to work together? There are several common
steps to take:

1. Set the default strategy to `INCLUDE` or `WARN`.
2. Apply your [`ResourceTransformer`][ResourceTransformer]s.
3. Remove duplicate entries by
  - overriding the default strategy for specific files to `EXCLUDE` or `FAIL` using
    [`filesMatching`][Jar.filesMatching], [`filesNotMatching`][Jar.filesNotMatching], or [`eachFile`][Jar.eachFile]
    functions
  - or applying [`PreserveFirstFoundResourceTransformer`][PreserveFirstFoundResourceTransformer] for specific files
  - or write your own [`ResourceTransformer`][ResourceTransformer] to handle duplicates
  - or mechanism similar.

Alternatively, you can follow these steps:

1. Set the default strategy to `EXCLUDE` or `FAIL`.
2. Apply your [`ResourceTransformer`][ResourceTransformer]s.
3. Bypass the duplicate entries which should be handled by the [`ResourceTransformer`][ResourceTransformer]s using
   [`filesMatching`][Jar.filesMatching], [`filesNotMatching`][Jar.filesNotMatching], or [`eachFile`][Jar.eachFile]
   functions to set their `duplicatesStrategy` to `INCLUDE` or `WARN`.

!!! warning "Build Cache Impact"

    Functions inherited from [`CopySpec`][CopySpec], such as [`filesMatching`][Jar.filesMatching],
    [`filesNotMatching`][Jar.filesNotMatching], [`eachFile`][Jar.eachFile], or others, disable the output caching.

Optional steps:

- Enable [`ShadowJar.failOnDuplicateEntries`][ShadowJar.failOnDuplicateEntries] to check duplicate entries in the final
  JAR. This can also ensure the regressions are caught in the future.
- Use [Diffuse][Diffuse] to diff the JARs.

Here are some examples:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      // Step 1.
      duplicatesStrategy = DuplicatesStrategy.INCLUDE // Or WARN.
      // Step 2.
      mergeServiceFiles()
      // Step 3. Using `filesNotMatching`:
      filesNotMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Or FAIL.
      }
      // Step 3. Using `PreserveFirstFoundResourceTransformer`:
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.PreserveFirstFoundResourceTransformer> {
        include("META-INF/foo/**") // Or something else where the first occurrence should be preserved.
      }
    }

    tasks.shadowJar {
      // Step 1.
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Or FAIL.
      // Step 2.
      mergeServiceFiles()
      // Step 3. Using `filesMatching`:
      filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE // Or WARN.
      }
      // Step 3. Using `eachFile`:
      eachFile {
        if (path.startsWith("META-INF/services/")) {
          duplicatesStrategy = DuplicatesStrategy.INCLUDE // Or WARN.
        }
      }
    }

    tasks.shadowJar {
      // Optional step.
      failOnDuplicateEntries = true
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      // Step 1.
      duplicatesStrategy = DuplicatesStrategy.INCLUDE // Or WARN.
      // Step 2.
      mergeServiceFiles()
      // Step 3. Using `filesNotMatching`:
      filesNotMatching('META-INF/services/**') {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Or FAIL.
      }
      // Step 3. Using `PreserveFirstFoundResourceTransformer`:
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.PreserveFirstFoundResourceTransformer) {
        include 'META-INF/foo/**' // Or something else where the first occurrence should be preserved.
      }
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      // Step 1.
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Or FAIL.
      // Step 2.
      mergeServiceFiles()
      // Step 3. Using `filesMatching`:
      filesMatching('META-INF/services/**') {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE // Or WARN.
      }
      // Step 3. Using `eachFile`:
      eachFile {
        if (it.path.startsWith('META-INF/services/')) {
          it.duplicatesStrategy = DuplicatesStrategy.INCLUDE // Or WARN.
        }
      }
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      // Optional step.
      failOnDuplicateEntries = true
    }
    ```

## Basic ResourceTransformer Usage

For simpler use cases, you can create a basic transformer:

=== "Kotlin"

    ```kotlin
    import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
    import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
    import org.apache.tools.zip.ZipOutputStream
    import org.gradle.api.file.FileTreeElement

    class MyTransformer : ResourceTransformer {
      override fun canTransformResource(element: FileTreeElement): Boolean = true
      override fun transform(context: TransformerContext) {}
      override fun hasTransformedResource(): Boolean = true
      override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {}
    }

    tasks.shadowJar {
      transform<MyTransformer>()
    }
    ```

=== "Groovy"

    ```groovy
    import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
    import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
    import org.apache.tools.zip.ZipOutputStream
    import org.gradle.api.file.FileTreeElement

    class MyTransformer implements ResourceTransformer {
      @Override boolean canTransformResource(FileTreeElement element) { return true }
      @Override void transform(TransformerContext context) {}
      @Override boolean hasTransformedResource() { return true }
      @Override void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {}
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(MyTransformer)
    }
    ```

Additionally, a [`ResourceTransformer`][ResourceTransformer] can accept a closure to configure the provided
[`ResourceTransformer`][ResourceTransformer]. An instantiated instance of a [`ResourceTransformer`][ResourceTransformer]
can also be provided.

=== "Kotlin"

    ```kotlin
    import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
    import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
    import org.apache.tools.zip.ZipOutputStream
    import org.gradle.api.file.FileTreeElement

    class MyTransformer(@get:Input var enabled: Boolean = false) : ResourceTransformer {
      override fun canTransformResource(element: FileTreeElement): Boolean = enabled
      override fun transform(context: TransformerContext) {}
      override fun hasTransformedResource(): Boolean = enabled
      override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {}
    }

    tasks.shadowJar {
      // Initialize with default constructor and configure with closure.
      transform<MyTransformer> {
        enabled = true
      }

      // Or use the instantiated instance with closure.
      transform(MyTransformer(false)) {
        enabled = true
      }
    }
    ```

=== "Groovy"

    ```groovy
    import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
    import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
    import org.apache.tools.zip.ZipOutputStream
    import org.gradle.api.file.FileTreeElement

    class MyTransformer implements ResourceTransformer {
      @Input boolean enabled
      MyTransformer(boolean enabled = false) { this.enabled = enabled }
      @Override boolean canTransformResource(FileTreeElement element) { return enabled }
      @Override void transform(TransformerContext context) {}
      @Override boolean hasTransformedResource() { return enabled }
      @Override void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {}
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      // Initialize with default constructor and configure with closure.
      transform(MyTransformer) {
        enabled = true
      }

      // Or use the instantiated instance with closure.
      transform(new MyTransformer(false)) {
        enabled = true
      }
    }
    ```

## Merging Service Descriptor Files

Java libraries often contain service descriptors files in the `META-INF/services` directory of the JAR. A service
descriptor typically contains a line delimited list of classes that are supported for a particular _service_. At
runtime, this file is read and used to configure library or application behavior.

Multiple dependencies may use the same service descriptor file name. In this case, it is generally desired to merge the
content of each instance of the file into a single output file. The [`ServiceFileTransformer`][ServiceFileTransformer]
class is used to perform this merging. By default, it will merge each copy of a file under `META-INF/services` into a
single file in the output JAR. You can use either the short syntax method
[`mergeServiceFiles()`][ShadowJar.mergeServiceFiles] or the full syntax method [`transform`][ShadowJar.transform] to add
the [`ServiceFileTransformer`][ServiceFileTransformer]:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      // Short syntax.
      mergeServiceFiles()

      // Full syntax.
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer>()
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      // Short syntax.
      mergeServiceFiles()

      // Full syntax.
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer)
    }
    ```

!!! note "Groovy Extension Modules"

    Groovy Extension Module descriptor files (located at
    `META-INF/services/org.codehaus.groovy.runtime.ExtensionModule`) are ignored by the
    [`ServiceFileTransformer`][ServiceFileTransformer].
    This is due to these files having a different syntax than standard service descriptor files.
    Use the [`mergeGroovyExtensionModules()`][mergeGroovyExtensionModules] method to merge these files if your
    dependencies contain them.

### Configuring the Location of Service Descriptor Files

By default, the [`ServiceFileTransformer`][ServiceFileTransformer] is configured to merge files in `META-INF/services`.
This directory can be overridden to merge descriptor files in a different location.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      // Short syntax.
      mergeServiceFiles {
        path = "META-INF/custom"
      }

      // Full syntax.
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer> {
        path = "META-INF/custom"
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      // Short syntax.      
      mergeServiceFiles {
        path = 'META-INF/custom'
      }

      // Full syntax.
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer) {
        path = 'META-INF/custom'
      }
    }
    ```

### Excluding/Including Specific Service Descriptor Files From Merging

The [`ServiceFileTransformer`][ServiceFileTransformer] class supports specifying specific files to include or exclude
from merging.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      // Short syntax.
      mergeServiceFiles {
        exclude("META-INF/services/com.acme.*")
      }

      // Full syntax.
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer> {
        exclude("META-INF/services/com.acme.*")
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      // Short syntax.
      mergeServiceFiles {
        exclude 'META-INF/services/com.acme.*'
      }

      // Full syntax.
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer) {
        exclude 'META-INF/services/com.acme.*'
      }
    }
    ```

## Merging Groovy Extension Modules

Shadow provides a specific transformer for dealing with Groovy extension module files. This is due to their special
syntax and how they need to be merged together. The
[`GroovyExtensionModuleTransformer`][GroovyExtensionModuleTransformer] will handle these files. The
[`ShadowJar`][ShadowJar] task also provides a short syntax method to add this transformer.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      // Short syntax.
      mergeGroovyExtensionModules()

      // Full syntax.
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer>()
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      // Short syntax.
      mergeGroovyExtensionModules()

      // Full syntax.
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer)
    }
    ```

## Merging Log4j2 Plugin Cache Files (`Log4j2Plugins.dat`)

[`Log4j2PluginsCacheFileTransformer`][Log4j2PluginsCacheFileTransformer] is a
[`ResourceTransformer`][ResourceTransformer] that merges
`META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat` plugin caches from all the jars containing
Log4j 2.x Core components. It's a Gradle equivalent of
[Log4j Plugin Descriptor Transformer][log4j-plugin-descriptor-transformer].

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer>()
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer)
    }
    ```

## Appending Text Files

Generic text files can be appended together using the [`AppendingTransformer`][AppendingTransformer]. Each file is
appended using separators (defaults to `\n`) to separate content. The [`ShadowJar`][ShadowJar] task provides a short
syntax method of [`append(String)`][ShadowJar.append] to configure this transformer.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      append("test.properties")
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      append 'test.properties'
    }
    ```

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      // short syntax
      append("resources/application.yml", "\n---\n")
      // full syntax
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer> {
        resource = "resources/custom-config/application.yml"
        separator = "\n---\n"
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      // short syntax
      append('resources/application.yml', '\n---\n')
      // full syntax
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer) {
        resource = 'resources/custom-config/application.yml'
        separator = '\n---\n'
      }
    }
    ```

## Appending XML Files

XML files require a special transformer for merging. The [`XmlAppendingTransformer`][XmlAppendingTransformer] reads each
XML document and merges each root element into a single document. There is no short syntax method for the
[`XmlAppendingTransformer`][XmlAppendingTransformer]. It must be added using the [`transform`][ShadowJar.transform]
methods.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer> {
        resource = "properties.xml"
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer) {
        resource = 'properties.xml'
      }
    }
    ```

## Merging R8/ProGuard Rule Files

Dependencies may publish ProGuard or R8 rules under `META-INF/proguard`. When multiple dependencies have files with the
same name under `META-INF/proguard`, the [`ProGuardFilesResourceTransformer`][ProGuardFilesResourceTransformer] merges
them into a single file in the output JAR, while retaining distinct file names for non-conflicting rules. It also
relocates matched class names and package patterns within the rules according to configured relocators.

You can add this transformer using [`transform`][ShadowJar.transform]:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.ProGuardFilesResourceTransformer>()
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.ProGuardFilesResourceTransformer)
    }
    ```

## Configuring Resource Transformer Filtering by Pattern

There are lots of built-in [`ResourceTransformer`][ResourceTransformer]s provided by Shadow. Some of them extend
[`PatternFilterableResourceTransformer`][PatternFilterableResourceTransformer], which extends
[`PatternFilterable`][PatternFilterable] to provide `include`/`exclude` pattern filtering capabilities. For example:

- [`ApacheNoticeResourceTransformer`][ApacheNoticeResourceTransformer]
- [`DeduplicatingResourceTransformer`][DeduplicatingResourceTransformer]
- [`KotlinModuleMetadataTransformer`][KotlinModuleMetadataTransformer]
- [`MergeLicenseResourceTransformer`][MergeLicenseResourceTransformer]
- [`PreserveFirstFoundResourceTransformer`][PreserveFirstFoundResourceTransformer]
- [`ProGuardFilesResourceTransformer`][ProGuardFilesResourceTransformer]
- [`ServiceFileTransformer`][ServiceFileTransformer]

You can use `include`/`exclude` and more methods to configure the patterns for those
[`ResourceTransformer`][ResourceTransformer]s that support it. For example:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer> {
        include("META-INF/services/com.example.*")
        exclude("META-INF/services/com.example.Internal")
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer) {
        include 'META-INF/services/com.example.*'
        exclude 'META-INF/services/com.example.Internal'
      }
    }
    ```

## Merging Properties Files

Properties files can be merged across dependencies and project resources using
[`PropertiesFileTransformer`][PropertiesFileTransformer].

By default, the transformer merges all `.properties` files using `MergeStrategy.First`, which keeps the first value
found for duplicate property keys. You can customize the merge behavior with several strategies:

- `MergeStrategy.First`: Keeps the first occurrence of a property key (default).
- `MergeStrategy.Latest`: Overwrites previous property values with the latest occurrence.
- `MergeStrategy.Append`: Combines duplicate property values using `mergeSeparator` (default `,`).
- `MergeStrategy.Fail`: Fails the build if duplicate keys have conflicting values.

You can also specify specific file paths or regular expressions to match using `paths`, configure per-path merge
strategies using `mappings`, rewrite property keys using `keyTransformer`, or change file encoding using
`charsetName` (defaults to `ISO-8859-1`).

=== "Kotlin"

    ```kotlin
    import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer
    import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy

    tasks.shadowJar {
      transform<PropertiesFileTransformer> {
        paths.add("META-INF/test.properties")
        mergeStrategy = MergeStrategy.Append
        mergeSeparator = ";"
      }
    }
    ```

=== "Groovy"

    ```groovy
    import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer
    import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(PropertiesFileTransformer) {
        paths.add('META-INF/test.properties')
        mergeStrategy = MergeStrategy.Append
        mergeSeparator = ';'
      }
    }
    ```

## Merging License Files

When multiple dependencies contain license files (such as `META-INF/LICENSE*` or `LICENSE*`), you can merge them into a
single license file in the output JAR using the [`MergeLicenseResourceTransformer`][MergeLicenseResourceTransformer].

You can configure:

- `artifactLicense`: Path to the project's license file (required).
- `artifactLicenseSpdxId`: An SPDX identifier placed as a header (`SPDX-License-Identifier: <id>`) to avoid ambiguous
  license detection by scanning tools (defaults to `Apache-2.0`).
- `outputPath`: The destination path in the final JAR (defaults to `META-INF/LICENSE`).
- `firstSeparator`: Separator between the project's license and dependency licenses.
- `separator`: Separator between individual dependency licenses.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.MergeLicenseResourceTransformer> {
        artifactLicense = layout.projectDirectory.file("LICENSE")
        artifactLicenseSpdxId = "Apache-2.0"
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.MergeLicenseResourceTransformer) {
        artifactLicense = layout.projectDirectory.file('LICENSE')
        artifactLicenseSpdxId = 'Apache-2.0'
      }
    }
    ```

If you instead want to discard all license files from the output JAR, you can simply use
[`ShadowJar.exclude`][ShadowJar.exclude]:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      exclude("META-INF/LICENSE*", "LICENSE*")
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      exclude 'META-INF/LICENSE*', 'LICENSE*'
    }
    ```

## Merging Apache NOTICE Files

The [`ApacheNoticeResourceTransformer`][ApacheNoticeResourceTransformer] aggregates `META-INF/NOTICE*` files
(`META-INF/NOTICE`, `META-INF/NOTICE.txt`, `META-INF/NOTICE.md`) into a single `NOTICE` file following Apache NOTICE
formatting requirements.

You can configure properties such as `projectName`, `copyright`, `organizationName`, `organizationURL`,
`inceptionYear`, `outputPath` (defaults to `META-INF/NOTICE`), `addHeader`, and `charsetName` (defaults to `UTF-8`).

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.ApacheNoticeResourceTransformer> {
        projectName = "My Project"
        organizationName = "My Organization"
        organizationURL = "https://example.com"
        inceptionYear = "2024"
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.ApacheNoticeResourceTransformer) {
        projectName = 'My Project'
        organizationName = 'My Organization'
        organizationURL = 'https://example.com'
        inceptionYear = '2024'
      }
    }
    ```

## Merging Plexus Components XML Files

Maven plugins and components using the Plexus IoC container provide component definitions in
`META-INF/plexus/components.xml`. The [`ComponentsXmlResourceTransformer`][ComponentsXmlResourceTransformer]
aggregates these component definitions into a single file and relocates the `role` and `implementation` class names
matching the configured relocators.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.ComponentsXmlResourceTransformer>()
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.ComponentsXmlResourceTransformer)
    }
    ```

## Transforming and Relocating Manifest Attributes

While standard manifest attributes can be configured using Gradle's native `manifest { ... }` block, the
[`ManifestResourceTransformer`][ManifestResourceTransformer] allows modifying manifest entries while automatically
relocating class and package names within configured manifest attributes (such as `Export-Package`, `Import-Package`,
`Provide-Capability`, `Require-Capability` by default, configurable via `attributesToRelocate`):

To remove a specific attribute from the manifest, map its name to
[`ManifestResourceTransformer.NULL`][ManifestResourceTransformer.NULL].

=== "Kotlin"

    ```kotlin
    import com.github.jengelman.gradle.plugins.shadow.transformers.ManifestResourceTransformer

    tasks.shadowJar {
      transform<ManifestResourceTransformer> {
        mainClass = "com.example.Main"
        manifestEntries.put("Built-By", "Shadow")
        manifestEntries.put("Header-To-Remove", ManifestResourceTransformer.NULL)
        attributesToRelocate.add("Custom-Package-Header")
      }
    }
    ```

=== "Groovy"

    ```groovy
    import com.github.jengelman.gradle.plugins.shadow.transformers.ManifestResourceTransformer

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(ManifestResourceTransformer) {
        mainClass = 'com.example.Main'
        manifestEntries.put('Built-By', 'Shadow')
        manifestEntries.put('Header-To-Remove', ManifestResourceTransformer.NULL)
        attributesToRelocate.add('Custom-Package-Header')
      }
    }
    ```

## Preserving First-Found Resources

[`PreserveFirstFoundResourceTransformer`][PreserveFirstFoundResourceTransformer] preserves the first resource matching
the specified patterns and discards any subsequent duplicates found with the same path.

This transformer is useful when `duplicatesStrategy` is set to `INCLUDE` or `WARN`, ensuring that project resources take
precedence and duplicate dependency resources at the same path are omitted.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.PreserveFirstFoundResourceTransformer> {
        include("config.json", "META-INF/foo/**")
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.PreserveFirstFoundResourceTransformer) {
        include 'config.json', 'META-INF/foo/**'
      }
    }
    ```

## Deduplicating Resources by Content

[`DeduplicatingResourceTransformer`][DeduplicatingResourceTransformer] checks entries at the same path across all input
JARs and ensures that duplicate files with identical SHA-256 content are included only once in the output JAR.

If multiple files share the **same path** but have **different** content, the transformer will fail the build with a
detailed report of the conflicting paths and file hashes.

If certain duplicate resources at the same path legitimately have different content (such as Maven `pom.properties`
or `pom.xml` files from different dependency versions), you can exclude those paths from being checked using
`exclude(...)`:

!!! warning "Do Not Combine with PreserveFirstFoundResourceTransformer"

    Do not combine [`PreserveFirstFoundResourceTransformer`][PreserveFirstFoundResourceTransformer] with
    [`DeduplicatingResourceTransformer`][DeduplicatingResourceTransformer], as they handle duplicates differently and
    combining them leads to redundant or unexpected behavior.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.DeduplicatingResourceTransformer> {
        exclude("META-INF/maven/**/pom.*")
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.DeduplicatingResourceTransformer) {
        exclude 'META-INF/maven/**/pom.*'
      }
    }
    ```

## Finding Resources in the Classpath

When dealing with resource merge conflicts, it can be helpful to find which dependencies contain the conflicting
resources. Shadow provides a [`FindResourceInClasspath`][FindResourceInClasspath] helper task for this purpose.

To scan for resources, register a [`FindResourceInClasspath`][FindResourceInClasspath] task in your build script and
configure its `classpath` and the resource patterns to look for:

=== "Kotlin"

    ```kotlin
    tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.FindResourceInClasspath>("findResources") {
      classpath.from(configurations.runtimeClasspath)
      include("META-INF/services/org.codehaus.groovy.runtime.ExtensionModule")
    }
    ```

=== "Groovy"

    ```groovy
    tasks.register('findResources', com.github.jengelman.gradle.plugins.shadow.tasks.FindResourceInClasspath) {
      classpath.from(configurations.runtimeClasspath)
      include 'META-INF/services/org.codehaus.groovy.runtime.ExtensionModule'
    }
    ```

You can then run the task to scan each entry on the classpath and print any matched resources to the console:

```shell
./gradlew findResources
```


[AbstractCopyTask]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.AbstractCopyTask.html
[Jar.eachFile]: https://docs.gradle.org/current/dsl/org.gradle.jvm.tasks.Jar.html#org.gradle.jvm.tasks.Jar:eachFile(org.gradle.api.Action)
[Jar.filesMatching]: https://docs.gradle.org/current/dsl/org.gradle.jvm.tasks.Jar.html#org.gradle.jvm.tasks.Jar:filesMatching(java.lang.Iterable,%20org.gradle.api.Action)
[Jar.filesNotMatching]: https://docs.gradle.org/current/dsl/org.gradle.jvm.tasks.Jar.html#org.gradle.jvm.tasks.Jar:filesNotMatching(java.lang.Iterable,%20org.gradle.api.Action)
[AppendingTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-appending-transformer/index.html
[ComponentsXmlResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-components-xml-resource-transformer/index.html
[DeduplicatingResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-deduplicating-resource-transformer/index.html
[DuplicatesStrategy]: https://docs.gradle.org/current/javadoc/org/gradle/api/file/DuplicatesStrategy.html
[FindResourceInClasspath]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-find-resource-in-classpath/index.html
[GroovyExtensionModuleTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-groovy-extension-module-transformer/index.html
[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
[KotlinModuleMetadataTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-kotlin-module-metadata-transformer/index.html
[Log4j2PluginsCacheFileTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-log4j2-plugins-cache-file-transformer/index.html
[ManifestResourceTransformer.NULL]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-manifest-resource-transformer/-companion/-n-u-l-l.html
[ManifestResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-manifest-resource-transformer/index.html
[MergeLicenseResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-merge-license-resource-transformer/index.html
[PreserveFirstFoundResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-preserve-first-found-resource-transformer/index.html
[PropertiesFileTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-properties-file-transformer/index.html
[PropertiesFileTransformer.MergeStrategy.First]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-properties-file-transformer/-merge-strategy/-first/index.html
[ResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-resource-transformer/index.html
[ApacheNoticeResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-apache-notice-resource-transformer/index.html
[ProGuardFilesResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-pro-guard-files-resource-transformer/index.html
[ServiceFileTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-service-file-transformer/index.html
[PatternFilterable]: https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.tasks.util/-pattern-filterable/index.html
[PatternFilterableResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-pattern-filterable-resource-transformer/index.html
[ShadowJar.append]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/append.html
[ShadowJar.exclude]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.AbstractCopyTask.html#org.gradle.api.tasks.AbstractCopyTask:exclude(java.lang.Iterable)
[ShadowJar.failOnDuplicateEntries]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/fail-on-duplicate-entries.html
[ShadowJar.from]: https://docs.gradle.org/current/dsl/org.gradle.jvm.tasks.Jar.html#org.gradle.jvm.tasks.Jar:from(java.lang.Object,%20org.gradle.api.Action)
[ShadowJar.mergeServiceFiles]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/merge-service-files.html
[ShadowJar.transform]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/transform.html
[ShadowJar]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
[XmlAppendingTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-xml-appending-transformer/index.html
[mergeGroovyExtensionModules]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/merge-groovy-extension-modules.html
[CopySpec]: https://docs.gradle.org/current/javadoc/org/gradle/api/file/CopySpec.html
[shadowjar-execution-flow]: ../README.md#shadowjar-execution-flow
[Diffuse]: https://github.com/JakeWharton/diffuse
[log4j-plugin-descriptor-transformer]: https://logging.apache.org/log4j/transform/log4j-transform-maven-shade-plugin-extensions.html#log4j-plugin-cache-transformer
