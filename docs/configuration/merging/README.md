# Controlling JAR Content Merging

Shadow allows for customizing the process by which the output JAR is generated through the
[`ResourceTransformer`][ResourceTransformer] interface. This is a concept that has been carried over from the original
Maven Shade implementation. A [`ResourceTransformer`][ResourceTransformer] is invoked for each entry in the JAR before
being written to the final output JAR. This allows a [`ResourceTransformer`][ResourceTransformer] to determine if it
should process a particular entry and apply any modifications before writing the stream to the output.

## Resource Processing Order

**Important**: [`ResourceTransformer`][ResourceTransformer] follows a guaranteed processing order:

1. **Project files first**: All files added via `shadowJar { from(...) }` are processed before any dependency files
2. **Dependency files second**: Files from configurations (runtime dependencies) are processed after project files

This ordering is crucial when merging configuration files where you want to preserve project-specific values while
merging in additional data from dependencies.

### Example: Merging Configuration Files

When merging files like `fabric.mod.json`, `plugin.yml`, or other configuration files, you can rely on the first
file being from your project:

=== "Kotlin"

    ```kotlin
    import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
    import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
    import org.apache.tools.zip.ZipOutputStream
    import org.gradle.api.file.FileTreeElement

    class ConfigMergingTransformer : ResourceTransformer {
      private var projectConfig: String? = null
      private val allConfigs = mutableListOf<String>()

      override fun canTransformResource(element: FileTreeElement): Boolean = 
        element.path == "config.json"

      override fun transform(context: TransformerContext) {
        val content = context.inputStream.reader().readText()
        
        if (projectConfig == null) {
          // First file is guaranteed to be from the project
          projectConfig = content
        }
        allConfigs.add(content)
      }

      override fun hasTransformedResource(): Boolean = projectConfig != null

      override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
        // Merge all configs while preserving project's base structure
        val merged = mergeConfigs(projectConfig!!, allConfigs)
        // ... write merged content to output stream ...
      }

      private fun mergeConfigs(project: String, all: List<String>): String {
        // Implementation depends on your specific config format
        return project // Simplified for example
      }
    }

    tasks.shadowJar {
      transform<ConfigMergingTransformer>()
    }
    ```

=== "Groovy"

    ```groovy
    import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
    import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
    import org.apache.tools.zip.ZipOutputStream
    import org.gradle.api.file.FileTreeElement

    class ConfigMergingTransformer implements ResourceTransformer {
      private String projectConfig = null
      private List<String> allConfigs = []

      @Override boolean canTransformResource(FileTreeElement element) { 
        return element.path == "config.json"
      }

      @Override void transform(TransformerContext context) {
        def content = context.inputStream.reader.text
        
        if (projectConfig == null) {
          // First file is guaranteed to be from the project
          projectConfig = content
        }
        allConfigs.add(content)
      }

      @Override boolean hasTransformedResource() { return projectConfig != null }

      @Override void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {
        // Merge all configs while preserving project's base structure
        def merged = mergeConfigs(projectConfig, allConfigs)
        // ... write merged content to output stream ...
      }

      private String mergeConfigs(String project, List<String> all) {
        // Implementation depends on your specific config format
        return project // Simplified for example
      }
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(ConfigMergingTransformer.class)
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
      transform(MyTransformer.class)
    }
    ```

Additionally, a [`ResourceTransformer`][ResourceTransformer] can accept a `Closure` to configure the provided
[`ResourceTransformer`][ResourceTransformer].

=== "Kotlin"

    ```kotlin
    import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
    import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
    import org.apache.tools.zip.ZipOutputStream
    import org.gradle.api.file.FileTreeElement

    class MyTransformer(var enabled: Boolean = false) : ResourceTransformer {
      override fun canTransformResource(element: FileTreeElement): Boolean = true
      override fun transform(context: TransformerContext) {}
      override fun hasTransformedResource(): Boolean = true
      override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {}
    }

    tasks.shadowJar {
      transform<MyTransformer>() {
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
      boolean enabled
      @Override boolean canTransformResource(FileTreeElement element) { return true }
      @Override void transform(TransformerContext context) {}
      @Override boolean hasTransformedResource() { return true }
      @Override void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {}
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(MyTransformer.class) {
        enabled = true
      }
    }
    ```

An instantiated instance of a [`ResourceTransformer`][ResourceTransformer] can also be provided.

=== "Kotlin"

    ```kotlin
    import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
    import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
    import org.apache.tools.zip.ZipOutputStream
    import org.gradle.api.file.FileTreeElement

    class MyTransformer(val enabled: Boolean) : ResourceTransformer {
      override fun canTransformResource(element: FileTreeElement): Boolean = true
      override fun transform(context: TransformerContext) {}
      override fun hasTransformedResource(): Boolean = true
      override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {}
    }

    tasks.shadowJar {
      transform(MyTransformer(true))
    }
    ```

=== "Groovy"

    ```groovy
    import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
    import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
    import org.apache.tools.zip.ZipOutputStream
    import org.gradle.api.file.FileTreeElement

    class MyTransformer implements ResourceTransformer {
      final boolean enabled
      MyTransformer(boolean enabled) { this.enabled = enabled }
      @Override boolean canTransformResource(FileTreeElement element) { return true }
      @Override void transform(TransformerContext context) {}
      @Override boolean hasTransformedResource() { return true }
      @Override void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {}
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      transform(new MyTransformer(true))
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

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      mergeServiceFiles()
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      mergeServiceFiles()
    }
    ```

The above code snippet is a convenience syntax for calling `transform(ServiceFileTransformer.class)`.

> Groovy Extension Module descriptor files (located at `META-INF/services/org.codehaus.groovy.runtime.ExtensionModule`)
> are ignored by the [`ServiceFileTransformer`][ServiceFileTransformer].
> This is due to these files having a different syntax than standard service descriptor files.
> Use the [`mergeGroovyExtensionModules()`][mergeGroovyExtensionModules] method to merge
> these files if your dependencies contain them.

### Configuring the Location of Service Descriptor Files

By default, the [`ServiceFileTransformer`][ServiceFileTransformer] is configured to merge files in `META-INF/services`.
This directory can be overridden to merge descriptor files in a different location.

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      mergeServiceFiles {
        path = "META-INF/custom"
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      mergeServiceFiles {
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
      mergeServiceFiles {
        exclude("META-INF/services/com.acme.*")
      }
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      mergeServiceFiles {
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
      mergeGroovyExtensionModules()
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      mergeGroovyExtensionModules()
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
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer.class)
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
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer.class) {
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
      transform(com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer.class) {
        resource = 'properties.xml'
      }
    }
    ```

## Handling Duplicates Strategy

`ShadowJar` is a subclass of [`org.gradle.api.tasks.AbstractCopyTask`][AbstractCopyTask], which means it honors the
`duplicatesStrategy` property as its parent classes do. There are several strategies to handle:

- `EXCLUDE`: Do not allow duplicates by ignoring subsequent items to be created at the same path.
- `FAIL`: Throw a `DuplicateFileCopyingException` when subsequent items are to be created at the same path.
- `INCLUDE`: Do not attempt to prevent duplicates.
- `INHERIT`: Uses the same strategy as the parent copy specification.
- `WARN`: Do not attempt to prevent duplicates, but log a warning message when multiple items are to be created at the
  same path.

see more details about them in [`DuplicatesStrategy`][DuplicatesStrategy].

`ShadowJar` recognizes `DuplicatesStrategy.INCLUDE` as the default, if you want to change the strategy, you can
override it like:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Or something else.
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Or something else.
    }
    ```

Different strategies will lead to different results for `foo/bar` files in the JARs to be merged:

- `EXCLUDE`: The **first** `foo/bar` file will be included in the final JAR.
- `FAIL`: **Fail** the build with a `DuplicateFileCopyingException` if there are duplicated `foo/bar` files.
- `INCLUDE`: The **last** `foo/bar` file will be included in the final JAR (the default behavior).
- `INHERIT`: **Fail** the build with an exception like
  `Entry .* is a duplicate but no duplicate handling strategy has been set`.
- `WARN`: The **last** `foo/bar` file will be included in the final JAR, and a warning message will be logged.

**NOTE:** The `duplicatesStrategy` takes precedence over transforming and relocating. If you mix the usages of
`duplicatesStrategy` and [`ResourceTransformer`][ResourceTransformer] like below:

=== "Kotlin"

    ```kotlin
    tasks.shadowJar {
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      mergeServiceFiles()
    }
    ```

=== "Groovy"

    ```groovy
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      mergeServiceFiles()
    }
    ```

The [`ServiceFileTransformer`][ServiceFileTransformer] will not work as expected because the `duplicatesStrategy` will
exclude the duplicated service files beforehand. However, this behavior might be what you expected for duplicated
`foo/bar` files, preventing them from being included.
Want `ResourceTransformer`s and `duplicatesStrategy` to work together? There is a way to achieve this, leave the
`duplicatesStrategy` as `INCLUDE` and declare a custom [`ResourceTransformer`][ResourceTransformer] to handle the
duplicated files.



[AbstractCopyTask]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.AbstractCopyTask.html
[AppendingTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-appending-transformer/index.html
[DuplicatesStrategy]: https://docs.gradle.org/current/javadoc/org/gradle/api/file/DuplicatesStrategy.html
[GroovyExtensionModuleTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-groovy-extension-module-transformer/index.html
[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
[Log4j2PluginsCacheFileTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-log4j2-plugins-cache-file-transformer/index.html
[ResourceTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-resource-transformer/index.html
[ServiceFileTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-service-file-transformer/index.html
[ShadowJar.append]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/append.html
[ShadowJar.transform]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/transform.html
[ShadowJar]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
[XmlAppendingTransformer]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-xml-appending-transformer/index.html
[mergeGroovyExtensionModules]: ../../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/merge-groovy-extension-modules.html
