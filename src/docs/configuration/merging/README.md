# Controlling JAR Content Merging

Shadow allows for customizing the process by which the output JAR is generated through the
[`Transformer`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/transformers/Transformer.html) interface.
This is a concept that has been carried over from the original Maven Shade implementation.
A [`Transformer`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/transformers/Transformer.html) is invoked for each 
entry in the JAR before being written to the final output JAR.
This allows a [`Transformer`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/transformers/Transformer.html) to 
determine if it should process a particular entry and apply any modifications before writing the stream to the output.

```groovy
// Adding a Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import javax.annotation.Nonnull
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

class MyTransformer implements Transformer {
  @Override
  boolean canTransformResource(@Nonnull FileTreeElement element) { return true }

  @Override
  void transform(@Nonnull TransformerContext context) {}

  @Override
  boolean hasTransformedResource() { return true }

  @Override
  void modifyOutputStream(@Nonnull ZipOutputStream os, boolean preserveFileTimestamps) {}
}

tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
  transform(MyTransformer.class)
}
```

Additionally, a `Transformer` can accept a `Closure` to configure the provided `Transformer`.

```groovy
// Configuring a Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import javax.annotation.Nonnull
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

class MyTransformer implements Transformer {
  boolean enabled

  @Override
  boolean canTransformResource(@Nonnull FileTreeElement element) { return true }

  @Override
  void transform(@Nonnull TransformerContext context) {}

  @Override
  boolean hasTransformedResource() { return true }

  @Override
  void modifyOutputStream(@Nonnull ZipOutputStream os, boolean preserveFileTimestamps) {}
}

tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
  transform(MyTransformer.class) {
    enabled = true
  }
}
```

An instantiated instance of a `Transformer` can also be provided.

```groovy
// Adding a Transformer Instance
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import javax.annotation.Nonnull
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

class MyTransformer implements Transformer {
  final boolean enabled

  MyTransformer(boolean enabled) {
    this.enabled = enabled
  }

  @Override
  boolean canTransformResource(@Nonnull FileTreeElement element) { return true }

  @Override
  void transform(@Nonnull TransformerContext context) {}

  @Override
  boolean hasTransformedResource() { return true }

  @Override
  void modifyOutputStream(@Nonnull ZipOutputStream os, boolean preserveFileTimestamps) {}
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
The [`ServiceFileTransformer`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/transformers/ServiceFileTransformer.html) 
class is used to perform this merging. By default, it will merge each copy of a file under `META-INF/services` into a 
single file in the output JAR.

```groovy
// Merging Service Files
tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
  mergeServiceFiles()
}
```

The above code snippet is a convenience syntax for calling
[`transform(ServiceFileTransformer.class)`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html#transform(Class<?%20extends%20Transformer>)).

> Groovy Extension Module descriptor files (located at `META-INF/services/org.codehaus.groovy.runtime.ExtensionModule`)
are ignored by the [`ServiceFileTransformer`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/transformers/ServiceFileTransformer.html).
This is due to these files having a different syntax than standard service descriptor files.
Use the [`mergeGroovyExtensionModules()`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html#mergeGroovyExtensionModules()) method to merge
these files if your dependencies contain them.

### Configuring the Location of Service Descriptor Files

By default the [`ServiceFileTransformer`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/transformers/ServiceFileTransformer.html) 
is configured to merge files in `META-INF/services`.
This directory can be overridden to merge descriptor files in a different location.

```groovy
// Merging Service Files in a Specific Directory
tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
  mergeServiceFiles {
    path = 'META-INF/custom'
  }
}
```

#### Excluding/Including Specific Service Descriptor Files From Merging

The [`ServiceFileTransformer`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/transformers/ServiceFileTransformer.html) 
class supports specifying specific files to include or exclude from merging.

```groovy
// Excluding a Service Descriptor From Merging
tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
  mergeServiceFiles {
    exclude 'META-INF/services/com.acme.*'
  }
}
```

## Merging Groovy Extension Modules

Shadow provides a specific transformer for dealing with Groovy extension module files.
This is due to their special syntax and how they need to be merged together.
The [`GroovyExtensionModuleTransformer`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/transformers/GroovyExtensionModuleTransformer.html) 
will handle these files.
The [`ShadowJar`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html) task also provides a short syntax 
method to add this transformer.

```groovy
// Merging Groovy Extension Modules
tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
  mergeGroovyExtensionModules()
}
```

## Appending Text Files

Generic text files can be appended together using the
[`AppendingTransformer`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/transformers/AppendingTransformer.html).
Each file is appended using separators (defaults to `\n`) to separate content.
The [`ShadowJar`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html) task provides a short syntax 
method of
[`append(String)`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.html#append(java.lang.String)) to 
configure this transformer.

```groovy
// Appending a Property File
tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
  append 'test.properties'
}
```

```groovy
// Appending application.yml files
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
The [`XmlAppendingTransformer`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/transformers/XmlAppendingTransformer.html) 
reads each XML document and merges each root element into a single document.
There is no short syntax method for the [`XmlAppendingTransformer`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/transformers/XmlAppendingTransformer.html).
It must be added using the [`transform`](https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow//tasks/ShadowJar.html#transform(Class<?%20Fextends%20Transformer>)) methods.

```groovy
// Appending a XML File
tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
  transform(com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer.class) {
    resource = 'properties.xml'
  }
}
```
