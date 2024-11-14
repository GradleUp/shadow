package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext.Companion.getEntryTimestamp
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Properties
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

/**
 * Modified from `eu.appsatori.gradle.fatjar.tasks.PrepareFiles.groovy`
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
 */
@CacheableTransformer
class GroovyExtensionModuleTransformer : Transformer {
    private val module = Properties()

    /**
     * default to Groovy 2.4 or earlier
     */
    private var legacy = true

    override fun canTransformResource(element: FileTreeElement): Boolean {
        val path = element.relativePath.pathString
        if (path == GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH) {
            // Groovy 2.5+
            legacy = false
            return true
        }
        return path == GROOVY_LEGACY_EXTENSION_MODULE_DESCRIPTOR_PATH
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
        val name = if (legacy) GROOVY_LEGACY_EXTENSION_MODULE_DESCRIPTOR_PATH else GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH
        val entry = ZipEntry(name)
        entry.time = getEntryTimestamp(preserveFileTimestamps, entry.time)
        os.putNextEntry(entry)
        module.inputStream().use {
            it.copyTo(os)
        }
        os.closeEntry()
    }

    private companion object {
        private fun Properties.inputStream(): InputStream {
            val baos = ByteArrayOutputStream()
            store(baos, null)
            return ByteArrayInputStream(baos.toByteArray())
        }

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
