package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.inputStream
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import java.util.Properties
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

/**
 * Aggregate Apache Groovy extension modules descriptors.
 *
 * Resource transformer that merges Groovy extension module descriptor files into a single file.
 * Groovy extension module descriptor files have the name org.codehaus.groovy.runtime.ExtensionModule
 * and live in the META-INF/services (Groovy up to 2.4) or META-INF/groovy (Groovy 2.5+) directory.
 * See [GROOVY-8480](https://issues.apache.org/jira/browse/GROOVY-8480) for more details of the change.
 *
 * If there are several descriptor files spread across many JARs the individual
 * entries will be merged into a single descriptor file which will be
 * packaged into the resultant JAR produced by the shadowing process.
 * It will live in the legacy directory (META-INF/services) if all the processed descriptor
 * files came from the legacy location, otherwise it will be written into the now standard location (META-INF/groovy).
 * Note that certain JDK9+ tooling will break when using the legacy location.
 *
 * Modified from [eu.appsatori.gradle.fatjar.tasks.PrepareFiles.groovy](https://github.com/musketyr/gradle-fatjar-plugin/blob/master/src/main/groovy/eu/appsatori/gradle/fatjar/tasks/PrepareFiles.groovy).
 * Related to [org.apache.maven.plugins.shade.resource.GroovyResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/GroovyResourceTransformer.java).
 */
@CacheableTransformer
public open class GroovyExtensionModuleTransformer : ResourceTransformer {
  private val module = Properties()

  /**
   * default to Groovy 2.4 or earlier
   */
  private var legacy = true

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.path
    if (path == PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR) {
      // Groovy 2.5+
      legacy = false
      return true
    }
    return path == PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR
  }

  override fun transform(context: TransformerContext) {
    val props = Properties()
    props.load(context.inputStream)
    props.forEach { key, value ->
      when (key as String) {
        KEY_MODULE_NAME -> handle(key, value as String) {
          module.setProperty(key, MERGED_MODULE_NAME)
        }
        KEY_MODULE_VERSION -> handle(key, value as String) {
          module.setProperty(key, MERGED_MODULE_VERSION)
        }
        KEY_EXTENSION_CLASSES,
        KEY_STATIC_EXTENSION_CLASSES,
        -> handle(key, value as String) { existingValue ->
          module.setProperty(key, "$existingValue,$value")
        }
      }
    }
  }

  private fun handle(key: String, value: String, mergeValue: (String) -> Unit) {
    val existingValue = module.getProperty(key)
    if (existingValue != null) {
      mergeValue(existingValue)
    } else {
      module.setProperty(key, value)
    }
  }

  override fun hasTransformedResource(): Boolean = module.isNotEmpty()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val name = if (legacy) PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR else PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR
    os.putNextEntry(zipEntry(name, preserveFileTimestamps))
    module.inputStream().use {
      it.copyTo(os)
    }
    os.closeEntry()
  }

  public companion object {
    public const val PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR: String =
      "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"
    public const val PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR: String =
      "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule"

    public const val KEY_MODULE_NAME: String = "moduleName"
    public const val KEY_MODULE_VERSION: String = "moduleVersion"
    public const val KEY_EXTENSION_CLASSES: String = "extensionClasses"
    public const val KEY_STATIC_EXTENSION_CLASSES: String = "staticExtensionClasses"

    public const val MERGED_MODULE_NAME: String = "MergedByShadowJar"
    public const val MERGED_MODULE_VERSION: String = "1.0.0"
  }
}
