# Controlling JAR Content Merging

Shadow allows for customizing the process by which the output JAR is generated through the
[`ResourceTransformer`][ResourceTransformer] interface. This is a concept that has been carried over from the original
Maven Shade implementation. A [`ResourceTransformer`][ResourceTransformer] is invoked for each entry in the JAR before
being written to the final output JAR. This allows a [`ResourceTransformer`][ResourceTransformer] to determine if it
should process a particular entry and apply any modifications before writing the stream to the output.

!!! important "Guaranteed Processing Order"

    [`ResourceTransformer`][ResourceTransformer] follows a guaranteed processing order:

    1. **Project files first**: All files in projects are processed before any dependency files.
    2. **Dependency files second**: Files from configurations (runtime dependencies) or added via [`ShadowJar.from`][ShadowJar.from] are processed after project files.

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
- `INHERIT`: **Fail** the build with an exception like
  `Entry .* is a duplicate but no duplicate handling strategy has been set`.
- `WARN`: **Warn** about duplicates in the build log, this behaves exactly as `INHERIT` otherwise.

!!! note "Precedence of DuplicatesStrategy"

    The `duplicatesStrategy` evaluation takes precedence over transforming and relocating.
    Because `ShadowJar` is a subclass of Gradle's `AbstractCopyTask`, duplicate filtering configured via
    `duplicatesStrategy` is performed at Gradle's `CopySpec` processing layer **before** entries are passed to Shadow's
    internal [`ResourceTransformer`][ResourceTransformer] engine.

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

    'META-INF/services/foo' is matched by com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer but its DuplicatesStrategy is EXCLUDE — duplicates may be silently dropped before the transformer processes them.
    Set it to INCLUDE or WARN to ensure all duplicates are processed by the transformer.

Want [`ResourceTransformer`][ResourceTransformer]s and `duplicatesStrategy` to work together? There are several common
steps to take:

1. Set the default strategy to `INCLUDE` or `WARN`.
2. Apply your [`ResourceTransformer`][ResourceTransformer]s.
3. Remove duplicate entries by
    - overriding the default strategy for specific files to `EXCLUDE` or `FAIL` using
    [`filesMatching`][Jar.filesMatching], [`filesNotMatching`][Jar.filesNotMatching], or [`eachFile`][Jar.eachFile] functions
    - or applying [`PreserveFirstFoundResourceTransformer`][PreserveFirstFoundResourceTransformer] for specific files
    - or write your own [`ResourceTransformer`][ResourceTransformer] to handle duplicates
    - or mechanism similar.

Alternatively, you can follow these steps:

1. Set the default strategy to `EXCLUDE` or `FAIL`.
2. Apply your [`ResourceTransformer`][ResourceTransformer]s.
3. Bypass the duplicate entries which should be handled by the [`ResourceTransformer`][ResourceTransformer]s using
    [`filesMatching`][Jar.filesMatching], [`filesNotMatching`][Jar.filesNotMatching], or [`eachFile`][Jar.eachFile] functions
    to set their `duplicatesStrategy` to `INCLUDE` or `WARN`.

!!! warning "Build Cache Impact"

    Functions inherited from [`CopySpec`][CopySpec], such as [`filesMatching`][Jar.filesMatching], [`filesNotMatching`][Jar.filesNotMatching], [`eachFile`][Jar.eachFile], or others, disable the output caching.

Optional steps:

- Enable [`ShadowJar.failOnDuplicateEntries`][ShadowJar.failOnDuplicateEntries] to check duplicate entries in the final JAR.
  This can also ensure the regressions are caught in the future.
- Use [Diffuse](https://github.com/JakeWharton/diffuse) to diff the JARs.

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
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.PreserveFirstFoundResourceTransformer>() {
        resources.add("META-INF/foo/**") // Or something else where the first occurrence should be preserved.
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
        resources.add('META-INF/foo/**') // Or something else where the first occurrence should be preserved.
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
      transform<MyTransformer>() {
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

Java libraries often contain service descriptors files in the `META-INF/services` directory of the JAR.
A service descriptor typically contains a line delimited list of classes that are supported for a particular _service_.
At runtime, this file is read and used to configure library or application behavior.

Multiple dependencies may use the same service descriptor file name.
In this case, it is generally desired to merge the content of each instance of the file into a single output file.
The [`ServiceFileTransformer`][ServiceFileTransformer] class is used to perform this merging.
By default, it will merge each copy of a file under `META-INF/services` into a single file in the output JAR.
You can use either the short syntax method [`mergeServiceFiles()`][ShadowJar.mergeServiceFiles] or the full syntax
method [`transform`][ShadowJar.transform] to add the [`ServiceFileTransformer`][ServiceFileTransformer]:

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

    Groovy Extension Module descriptor files (located at `META-INF/services/org.codehaus.groovy.runtime.ExtensionModule`)
    are ignored by the [`ServiceFileTransformer`][ServiceFileTransformer].
    This is due to these files having a different syntax than standard service descriptor files.
    Use the [`mergeGroovyExtensionModules()`][mergeGroovyExtensionModules] method to merge
    these files if your dependencies contain them.

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
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer>() {
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
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer>() {
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

Shadow provides a specific transformer for dealing with Groovy extension module files.
This is due to their special syntax and how they need to be merged together.
The [`GroovyExtensionModuleTransformer`][GroovyExtensionModuleTransformer] will handle these files.
The [`ShadowJar`][ShadowJar] task also provides a short syntax method to add this transformer.

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
`META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat` plugin caches from all the jars
containing Log4j 2.x Core components. It's a Gradle equivalent of
[Log4j Plugin Descriptor Transformer](https://logging.apache.org/log4j/transform/log4j-transform-maven-shade-plugin-extensions.html#log4j-plugin-cache-transformer).

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

Generic text files can be appended together using the [`AppendingTransformer`][AppendingTransformer].
Each file is appended using separators (defaults to `\n`) to separate content.
The [`ShadowJar`][ShadowJar] task provides a short syntax method of [`append(String)`][ShadowJar.append] to configure
this transformer.

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
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer>() {
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

XML files require a special transformer for merging. The [`XmlAppendingTransformer`][XmlAppendingTransformer]
reads each XML document and merges each root element into a single document.
There is no short syntax method for the [`XmlAppendingTransformer`][XmlAppendingTransformer].
It must be added using the [`transform`][ShadowJar.transform] methods.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer>() {
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

Dependencies may publish ProGuard or R8 rules under `META-INF/proguard`.
When multiple dependencies have files with the same name under `META-INF/proguard`,
the [`ProGuardFilesResourceTransformer`][ProGuardFilesResourceTransformer] merges them into a single file in the
output JAR, while retaining distinct file names for non-conflicting rules. It also relocates matched class names
and package patterns within the rules according to configured relocators.

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

## Merging License Files

When creating an uber JAR, individual dependencies may include their own license files.
The [`MergeLicenseResourceTransformer`][MergeLicenseResourceTransformer] generates an aggregated license file combining
your project's license with the license files from merged dependencies.

By default, it looks for license files matching `META-INF/LICENSE`, `META-INF/LICENSE.txt`, `META-INF/LICENSE.md`,
`LICENSE`, `LICENSE.txt`, and `LICENSE.md` in dependencies, and writes the merged license file to `META-INF/LICENSE`.

You can configure the project's license file via [`artifactLicense`][MergeLicenseResourceTransformer.artifactLicense] (required),
specify an SPDX license identifier header using [`artifactLicenseSpdxId`][MergeLicenseResourceTransformer.artifactLicenseSpdxId]
(defaults to `Apache-2.0`), and customize separators:

=== "Kotlin"

    ```kotlin
    file("LICENSE").writeText("Sample Project License")

    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.MergeLicenseResourceTransformer> {
        artifactLicense.set(layout.projectDirectory.file("LICENSE"))
        artifactLicenseSpdxId.set("Apache-2.0")
      }
    }
    ```

=== "Groovy"

    ```groovy
    file('LICENSE').text = 'Sample Project License'

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.MergeLicenseResourceTransformer) {
        artifactLicense = layout.projectDirectory.file('LICENSE')
        artifactLicenseSpdxId = 'Apache-2.0'
      }
    }
    ```

If you instead only want to prevent duplicate license files from being included, you can use
[`ApacheLicenseResourceTransformer`][ApacheLicenseResourceTransformer].

## Merging Apache NOTICE Files

The [`ApacheNoticeResourceTransformer`][ApacheNoticeResourceTransformer] aggregates `META-INF/NOTICE`,
`META-INF/NOTICE.txt`, and `META-INF/NOTICE.md` files from dependencies into a single `META-INF/NOTICE` file,
standardizing headers, copyright notices, and organization attributions.

You can configure project metadata such as `projectName`, `organizationName`, `organizationURL`, `inceptionYear`,
and `copyright`:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.ApacheNoticeResourceTransformer> {
        projectName.set("My Project")
        organizationName.set("My Organization")
        organizationURL.set("https://example.com/")
        inceptionYear.set("2020")
        copyright.set("Copyright 2020-2026 My Organization")
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.ApacheNoticeResourceTransformer) {
        projectName = 'My Project'
        organizationName = 'My Organization'
        organizationURL = 'https://example.com/'
        inceptionYear = '2020'
        copyright = 'Copyright 2020-2026 My Organization'
      }
    }
    ```

## Merging Properties Files

The [`PropertiesFileTransformer`][PropertiesFileTransformer] merges Java `.properties` files across JARs.
By default, it transforms all `.properties` files using [`MergeStrategy.First`][PropertiesFileTransformer.MergeStrategy],
preserving the first value encountered for duplicate keys.

Available merge strategies in [`MergeStrategy`][PropertiesFileTransformer.MergeStrategy]:

- `First`: Discards duplicate values coming from subsequent resources (default).
- `Latest`: Overwrites earlier values with the latest value found.
- `Append`: Appends values together using [`mergeSeparator`][PropertiesFileTransformer.mergeSeparator] (defaults to `,`).
- `Fail`: Fails the build if conflicting values exist for any property.

You can also restrict transformation to specific file paths using `paths`, configure per-path merge behaviors using `mappings`,
or rewrite property keys (for example, when relocating class names) using `keyTransformer`:

=== "Kotlin"

    ```kotlin
    import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy

    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer> {
        paths.add("META-INF/config.properties")
        mergeStrategy.set(MergeStrategy.Append)
        mergeSeparator.set(";")
      }
    }
    ```

=== "Groovy"

    ```groovy
    import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer) {
        paths.add('META-INF/config.properties')
        mergeStrategy = MergeStrategy.Append
        mergeSeparator = ';'
      }
    }
    ```

## Modifying and Appending to Manifest Files

Shadow provides two transformers for modifying `META-INF/MANIFEST.MF`:

### Setting Attributes with `ManifestResourceTransformer`

[`ManifestResourceTransformer`][ManifestResourceTransformer] sets attributes (such as `mainClass` and arbitrary
manifest entries) in the first `MANIFEST.MF` encountered, or creates a new manifest if none exists:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.ManifestResourceTransformer> {
        mainClass.set("com.example.Main")
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.ManifestResourceTransformer) {
        mainClass = 'com.example.Main'
      }
    }
    ```

### Appending Attributes with `ManifestAppenderTransformer`

[`ManifestAppenderTransformer`][ManifestAppenderTransformer] appends arbitrary attributes to the first `MANIFEST.MF`
found. Attributes are appended in the specified order, and duplicate attribute names are allowed:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.ManifestAppenderTransformer> {
        append("Custom-Header", "Value1")
        append("Custom-Header", "Value2")
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.ManifestAppenderTransformer) {
        append('Custom-Header', 'Value1')
        append('Custom-Header', 'Value2')
      }
    }
    ```

## Merging Plexus Components XML Files

Maven plugins and Plexus-based libraries use `META-INF/plexus/components.xml` component descriptors.
The [`ComponentsXmlResourceTransformer`][ComponentsXmlResourceTransformer] merges these files into a single
descriptor and automatically relocates class names for roles, implementations, and requirements when relocation
rules are configured:

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

## Relocating Kotlin Module Metadata

Kotlin libraries contain `.kotlin_module` files describing package parts and top-level declarations.
The [`KotlinModuleMetadataTransformer`][KotlinModuleMetadataTransformer] rewrites package parts within
these metadata files according to configured relocation rules:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.KotlinModuleMetadataTransformer>()
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.KotlinModuleMetadataTransformer)
    }
    ```

See also [Kotlin Plugins](../../kotlin-plugins/README.md#kotlin-module-metadata-transformer) for more details.

## Deduplicating Resources with Identical Content

[`DeduplicatingResourceTransformer`][DeduplicatingResourceTransformer] ensures that identical files (determined by SHA-256 hash)
are included only once in the output JAR. If multiple files share the same path but have different contents,
it fails the build with a descriptive error detailing the conflicting files and their hashes.

It supports pattern filtering (`exclude` / `include`), allowing you to exclude specific paths (such as `META-INF/maven/**` metadata)
from causing duplicate content failures:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.DeduplicatingResourceTransformer> {
        exclude("META-INF/maven/**")
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.DeduplicatingResourceTransformer) {
        exclude 'META-INF/maven/**'
      }
    }
    ```

!!! warning "Do Not Combine with PreserveFirstFoundResourceTransformer"

    Do not combine [`PreserveFirstFoundResourceTransformer`][PreserveFirstFoundResourceTransformer] with
    [`DeduplicatingResourceTransformer`][DeduplicatingResourceTransformer], as they handle duplicate entries differently.

## Configuring Resource Transformer Filtering by Pattern

There are lots of built-in [`ResourceTransformer`][ResourceTransformer]s provided by Shadow. Some of them extend
[`PatternFilterableResourceTransformer`][PatternFilterableResourceTransformer], which extends
[`PatternFilterable`][PatternFilterable] to provide `include`/`exclude` pattern filtering capabilities. e.g.

- [`ApacheLicenseResourceTransformer`][ApacheLicenseResourceTransformer]
- [`ApacheNoticeResourceTransformer`][ApacheNoticeResourceTransformer]
- [`DeduplicatingResourceTransformer`][DeduplicatingResourceTransformer]
- [`KotlinModuleMetadataTransformer`][KotlinModuleMetadataTransformer]
- [`MergeLicenseResourceTransformer`][MergeLicenseResourceTransformer]
- [`ProGuardFilesResourceTransformer`][ProGuardFilesResourceTransformer]
- [`ServiceFileTransformer`][ServiceFileTransformer]
- ...

You can use `include`/`exclude` and more methods to configure the patterns for those
[`ResourceTransformer`][ResourceTransformer]s that support it. For example:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      transform<com.github.jengelman.gradle.plugins.shadow.transformers.ApacheLicenseResourceTransformer>() {
        include("META-INF/LICENSE.*")
        exclude("META-INF/LICENSE.log")
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.ApacheLicenseResourceTransformer) {
        include 'META-INF/LICENSE.*'
        exclude 'META-INF/LICENSE.log'
      }
    }
    ```

## Finding Resources in the Classpath

When dealing with resource merge conflicts, it can be helpful to find which dependencies contain the conflicting resources.
Shadow provides a [`FindResourceInClasspath`][FindResourceInClasspath] helper task for this purpose.

To scan for resources, register a [`FindResourceInClasspath`][FindResourceInClasspath] task in your build script and configure its `classpath` and the resource patterns to look for:

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
[ManifestAppenderTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-manifest-appender-transformer/index.html
[ManifestResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-manifest-resource-transformer/index.html
[MergeLicenseResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-merge-license-resource-transformer/index.html
[MergeLicenseResourceTransformer.artifactLicense]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-merge-license-resource-transformer/artifact-license.html
[MergeLicenseResourceTransformer.artifactLicenseSpdxId]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-merge-license-resource-transformer/artifact-license-spdx-id.html
[PropertiesFileTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-properties-file-transformer/index.html
[PropertiesFileTransformer.MergeStrategy]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-properties-file-transformer/-merge-strategy/index.html
[PropertiesFileTransformer.mergeSeparator]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-properties-file-transformer/merge-separator.html
[ResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-resource-transformer/index.html
[ApacheLicenseResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-apache-license-resource-transformer/index.html
[ApacheNoticeResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-apache-notice-resource-transformer/index.html
[ProGuardFilesResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-pro-guard-files-resource-transformer/index.html
[ServiceFileTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-service-file-transformer/index.html
[PreserveFirstFoundResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-preserve-first-found-resource-transformer/index.html
[PatternFilterable]: https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.tasks.util/-pattern-filterable/index.html
[PatternFilterableResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-pattern-filterable-resource-transformer/index.html
[ShadowJar.append]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/append.html
[ShadowJar.failOnDuplicateEntries]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/fail-on-duplicate-entries.html
[ShadowJar.from]: https://docs.gradle.org/current/dsl/org.gradle.jvm.tasks.Jar.html#org.gradle.jvm.tasks.Jar:from(java.lang.Object,%20org.gradle.api.Action)
[ShadowJar.mergeServiceFiles]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/merge-service-files.html
[ShadowJar.transform]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/transform.html
[ShadowJar]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
[XmlAppendingTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-xml-appending-transformer/index.html
[mergeGroovyExtensionModules]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/merge-groovy-extension-modules.html
[CopySpec]: https://docs.gradle.org/current/javadoc/org/gradle/api/file/CopySpec.html

