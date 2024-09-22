
package com.github.jengelman.gradle.plugins.shadow.transformers

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Properties
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

@CacheableTransformer
class GroovyExtensionModuleTransformer : Transformer {

  private val module = Properties()

  /**
   * default to Groovy 2.4 or earlier
   */
  private var legacy = true

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.relativePath.pathString
    return when (path) {
      GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH -> {
        // Groovy 2.5+
        legacy = false
        true
      }
      GROOVY_LEGACY_EXTENSION_MODULE_DESCRIPTOR_PATH -> true
      else -> false
    }
  }

  override fun transform(context: TransformerContext) {
    val props = Properties()
    props.load(context.inputStream)
    props.forEach { key, value ->
      when (key as String) {
        MODULE_NAME_KEY -> handle(key, value as String) {
          module.setProperty(key, MERGED_MODULE_NAME)
        }
        MODULE_VERSION_KEY -> handle(key, value as String) {
          module.setProperty(key, MERGED_MODULE_VERSION)
        }
        EXTENSION_CLASSES_KEY, STATIC_EXTENSION_CLASSES_KEY -> handle(key, value as String) { existingValue ->
          val newValue = "$existingValue,$value"
          module.setProperty(key, newValue)
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
    val path = if (legacy) GROOVY_LEGACY_EXTENSION_MODULE_DESCRIPTOR_PATH else GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH
    val entry = ZipEntry(path).apply {
      time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, time)
    }
    os.putNextEntry(entry)
    module.toInputStream().copyTo(os)
    os.closeEntry()
  }

  private fun Properties.toInputStream(): InputStream {
    val baos = ByteArrayOutputStream()
    this.store(baos, null)
    return baos.toByteArray().inputStream()
  }

  companion object {
    private const val GROOVY_LEGACY_EXTENSION_MODULE_DESCRIPTOR_PATH =
      "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"
    private const val GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH =
      "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule"
    private const val MODULE_NAME_KEY = "moduleName"
    private const val MODULE_VERSION_KEY = "moduleVersion"
    private const val EXTENSION_CLASSES_KEY = "extensionClasses"
    private const val STATIC_EXTENSION_CLASSES_KEY = "staticExtensionClasses"
    private const val MERGED_MODULE_NAME = "MergedByShadowJar"
    private const val MERGED_MODULE_VERSION = "1.0.0"
  }
}
