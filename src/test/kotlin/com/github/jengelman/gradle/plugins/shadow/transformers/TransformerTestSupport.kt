package com.github.jengelman.gradle.plugins.shadow.transformers

import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.DefaultFileTreeElement
import org.gradle.testfixtures.ProjectBuilder

abstract class TransformerTestSupport<T : Transformer> {
  protected lateinit var transformer: T

  protected val manifestTransformerContext: TransformerContext
    get() = TransformerContext(MANIFEST_NAME, requireResourceAsStream(MANIFEST_NAME))

  protected fun requireResourceAsStream(name: String): InputStream {
    return this::class.java.classLoader.getResourceAsStream(name) ?: error("Resource $name not found.")
  }

  protected companion object {
    const val MANIFEST_NAME: String = "META-INF/MANIFEST.MF"
    val objectFactory = ProjectBuilder.builder().build().objects

    fun getFileElement(path: String): FileTreeElement {
      return DefaultFileTreeElement(null, RelativePath.parse(true, path), null, null)
    }

    fun readFrom(jarFile: File, resourceName: String = MANIFEST_NAME): List<String> {
      return ZipFile(jarFile).use { zip ->
        val entry = zip.getEntry(resourceName) ?: return emptyList()
        zip.getInputStream(entry).bufferedReader().readLines()
      }
    }

    /**
     * NOTE: The Turkish locale has an usual case transformation for the letters "I" and "i", making it a prime
     * choice to test for improper case-less string comparisons.
     */
    fun setupTurkishLocale() {
      Locale.setDefault(Locale("tr"))
    }
  }
}
