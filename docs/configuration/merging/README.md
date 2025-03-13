# Controlling JAR Content Merging

Shadow allows for customizing the process by which the output JAR is generated through the
[`ResourceTransformer`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-resource-transformer/index.html) interface.
This is a concept that has been carried over from the original Maven Shade implementation.
A [`ResourceTransformer`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-resource-transformer/index.html) is invoked for each 
entry in the JAR before being written to the final output JAR.
This allows a [`ResourceTransformer`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-resource-transformer/index.html) to 
determine if it should process a particular entry and apply any modifications before writing the stream to the output.

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

Additionally, a `ResourceTransformer` can accept a `Closure` to configure the provided `ResourceTransformer`.

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

An instantiated instance of a `ResourceTransformer` can also be provided.

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
The [`ServiceFileTransformer`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-service-file-transformer/index.html) 
class is used to perform this merging. By default, it will merge each copy of a file under `META-INF/services` into a 
single file in the output JAR.

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

The above code snippet is a convenience syntax for calling
[`transform(ServiceFileTransformer.class)`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/transform.html).

> Groovy Extension Module descriptor files (located at `META-INF/services/org.codehaus.groovy.runtime.ExtensionModule`)
are ignored by the [`ServiceFileTransformer`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-service-file-transformer/index.html).
This is due to these files having a different syntax than standard service descriptor files.
Use the [`mergeGroovyExtensionModules()`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/merge-groovy-extension-modules.html) method to merge
these files if your dependencies contain them.

### Configuring the Location of Service Descriptor Files

By default, the [`ServiceFileTransformer`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-service-file-transformer/index.html) 
is configured to merge files in `META-INF/services`.
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

#### Excluding/Including Specific Service Descriptor Files From Merging

The [`ServiceFileTransformer`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-service-file-transformer/index.html) 
class supports specifying specific files to include or exclude from merging.

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
The [`GroovyExtensionModuleTransformer`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-groovy-extension-module-transformer/index.html) 
will handle these files.
The [`ShadowJar`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html) task also provides a short syntax 
method to add this transformer.

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

`Log4j2PluginsCacheFileTransformer` is a `ResourceTransformer` that merges `META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat` plugin caches from all the jars
containing Log4j 2.x Core components. It's a Gradle equivalent of [Log4j Plugin Descriptor Transformer](https://logging.apache.org/log4j/transform/log4j-transform-maven-shade-plugin-extensions.html#log4j-plugin-cache-transformer).

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

Generic text files can be appended together using the
[`AppendingTransformer`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-appending-transformer/index.html).
Each file is appended using separators (defaults to `\n`) to separate content.
The [`ShadowJar`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html) task provides a short syntax 
method of
[`append(String)`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/append.html) to 
configure this transformer.

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

XML files require a special transformer for merging.
The [`XmlAppendingTransformer`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-xml-appending-transformer/index.html) 
reads each XML document and merges each root element into a single document.
There is no short syntax method for the [`XmlAppendingTransformer`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.transformers/-xml-appending-transformer/index.html).
It must be added using the [`transform`](https://gradleup.com/shadow/api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/transform.html)) methods.

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

`ShadowJar` is a subclass of 
[`org.gradle.api.tasks.AbstractCopyTask`](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.AbstractCopyTask.html), 
which means it honors the `duplicatesStrategy` property as its parent classes do. There are several strategies to handle:

- `EXCLUDE`: Do not allow duplicates by ignoring subsequent items to be created at the same path.
- `FAIL`: Throw a `DuplicateFileCopyingException` when subsequent items are to be created at the same path.
- `INCLUDE`: Do not attempt to prevent duplicates.
- `INHERIT`: Uses the same strategy as the parent copy specification.
- `WARN`: Do not attempt to prevent duplicates, but log a warning message when multiple items are to be created at the same path.

You can see more details about them in 
[`DuplicatesStrategy`](https://docs.gradle.org/current/javadoc/org/gradle/api/file/DuplicatesStrategy.html).

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
- `INHERIT`: **Fail** the build with an exception like `Entry .* is a duplicate but no duplicate handling strategy has been set`.
- `WARN`: The **last** `foo/bar` file will be included in the final JAR, and a warning message will be logged.
