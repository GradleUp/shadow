package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.internal.createDefaultFileTreeElement
import com.github.jengelman.gradle.plugins.shadow.internal.inputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.Locale
import java.util.Properties
import java.util.zip.ZipFile
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath

abstract class TransformerTestSupport<T : Transformer> {
  protected lateinit var transformer: T

  protected val manifestTransformerContext: TransformerContext
    get() = TransformerContext(MANIFEST_NAME, requireResourceAsStream(MANIFEST_NAME))

  protected fun requireResourceAsStream(name: String): InputStream {
    return this::class.java.classLoader.getResourceAsStream(name) ?: throw FileNotFoundException("Resource $name not found.")
  }

  protected companion object {
    const val MANIFEST_NAME: String = "META-INF/MANIFEST.MF"
    val sharedStats = ShadowStats()

    fun getFileElement(path: String): FileTreeElement {
      return createDefaultFileTreeElement(relativePath = RelativePath.parse(true, path))
    }

    fun readFrom(jarPath: Path, resourceName: String = MANIFEST_NAME): List<String> {
      return ZipFile(jarPath.toFile()).use { zip ->
        val entry = zip.getEntry(resourceName) ?: return emptyList()
        zip.getInputStream(entry).bufferedReader().readLines()
      }
    }

    fun doTransformAndGetTransformedPath(
      transformer: Transformer,
      preserveFileTimestamps: Boolean,
    ): Path {
      val testableZipPath = createTempFile("testable-zip-file-", ".jar")
      ZipOutputStream(testableZipPath.outputStream().buffered()).use { zipOutputStream ->
        transformer.modifyOutputStream(zipOutputStream, preserveFileTimestamps)
      }
      return testableZipPath
    }

    /**
     * NOTE: The Turkish locale has an usual case transformation for the letters "I" and "i", making it a prime
     * choice to test for improper case-less string comparisons.
     */
    fun setupTurkishLocale() {
      Locale.setDefault(Locale("tr"))
    }

    fun Map<String, String>.toProperties(): Properties {
      return Properties().also { it.putAll(this) }
    }

    fun Properties.toMap(): Map<String, String> {
      return entries.associate { it.key.toString() to it.value.toString() }
    }

    fun context(path: String, input: Map<String, String>, charset: Charset = Charsets.ISO_8859_1): TransformerContext {
      return TransformerContext(path, input.toProperties().inputStream(charset), stats = sharedStats)
    }

    fun context(path: String, input: String): TransformerContext {
      return TransformerContext(path, input.byteInputStream(), stats = sharedStats)
    }
  }
}
