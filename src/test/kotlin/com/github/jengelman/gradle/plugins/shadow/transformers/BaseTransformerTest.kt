package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getStream
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsStream
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer.Companion.create
import com.github.jengelman.gradle.plugins.shadow.util.noOpDelegate
import com.github.jengelman.gradle.plugins.shadow.util.testObjectFactory
import java.io.File
import java.lang.reflect.ParameterizedType
import java.util.Locale
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir

abstract class BaseTransformerTest<T : ResourceTransformer> {
  protected lateinit var transformer: T
    private set

  protected val manifestTransformerContext: TransformerContext
    get() = TransformerContext(MANIFEST_NAME, requireResourceAsStream(MANIFEST_NAME))

  @BeforeEach
  open fun beforeEach() {
    @Suppress("UNCHECKED_CAST")
    val clazz = (this::class.java.genericSuperclass as ParameterizedType).actualTypeArguments.first() as Class<T>
    transformer = clazz.create(testObjectFactory)
  }

  companion object {
    const val MANIFEST_NAME: String = "META-INF/MANIFEST.MF"

    fun ResourceTransformer.canTransformResource(path: String, isFile: Boolean = true): Boolean {
      val element = object : FileTreeElement by noOpDelegate() {
        private val _relativePath = RelativePath.parse(isFile, path)
        override fun getPath(): String = _relativePath.pathString
        override fun getRelativePath(): RelativePath = _relativePath
      }
      return canTransformResource(element)
    }

    fun ResourceTransformer.canTransformResource(path: String, file: File): Boolean {
      val element = object : FileTreeElement by noOpDelegate() {
        private val _relativePath = RelativePath.parse(true, path)
        override fun getPath(): String = _relativePath.pathString
        override fun getRelativePath(): RelativePath = _relativePath
        override fun getFile(): File = file
      }
      return canTransformResource(element)
    }

    fun JarPath.readContentLines(resourceName: String = MANIFEST_NAME): List<String> {
      return use { it.getStream(resourceName).bufferedReader().readLines() }
    }

    fun doTransformAndGetTransformedPath(
      transformer: ResourceTransformer,
      preserveFileTimestamps: Boolean,
    ): JarPath {
      val testableZipPath = createTempFile("testable-zip-file-", ".jar")
      ZipOutputStream(testableZipPath.outputStream()).use { zipOutputStream ->
        transformer.modifyOutputStream(zipOutputStream, preserveFileTimestamps)
      }
      return JarPath(testableZipPath)
    }

    /**
     * NOTE: The Turkish locale has a usual case transformation for the letters "I" and "i", making it a prime
     * choice to test for improper case-less string comparisons.
     */
    fun setupTurkishLocale() {
      @Suppress("DEPRECATION")
      Locale.setDefault(Locale("tr"))
    }
  }
}
