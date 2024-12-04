package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.createDefaultFileTreeElement
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer.Companion.create
import com.github.jengelman.gradle.plugins.shadow.util.testObjectFactory
import java.io.InputStream
import java.lang.reflect.ParameterizedType
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.junit.jupiter.api.BeforeEach

abstract class TransformerTestSupport<T : Transformer> {
  protected lateinit var transformer: T
    private set

  protected val manifestTransformerContext: TransformerContext
    get() = TransformerContext(MANIFEST_NAME, requireResourceAsStream(MANIFEST_NAME))

  protected fun requireResourceAsStream(name: String): InputStream {
    return this::class.java.classLoader.getResourceAsStream(name) ?: error("Resource $name not found.")
  }

  @BeforeEach
  fun setup() {
    @Suppress("UNCHECKED_CAST")
    val clazz = (this::class.java.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<T>
    transformer = clazz.create(testObjectFactory)
  }

  protected companion object {
    const val MANIFEST_NAME: String = "META-INF/MANIFEST.MF"

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
  }
}
