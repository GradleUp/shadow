package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsStream
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer.Companion.create
import com.github.jengelman.gradle.plugins.shadow.util.noOpDelegate
import com.github.jengelman.gradle.plugins.shadow.util.testObjectFactory
import java.io.File
import java.lang.reflect.ParameterizedType
import java.util.Locale
import java.util.jar.JarFile.MANIFEST_NAME
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.junit.jupiter.api.BeforeEach

abstract class BaseTransformerTest<T : ResourceTransformer> {
  lateinit var transformer: T
    private set

  val manifestTransformerContext: TransformerContext
    get() = TransformerContext(MANIFEST_NAME, requireResourceAsStream(MANIFEST_NAME))

  @BeforeEach
  open fun beforeEach() {
    @Suppress("UNCHECKED_CAST")
    val clazz =
      (this::class.java.genericSuperclass as ParameterizedType).actualTypeArguments.single()
        as Class<T>
    transformer = clazz.create(testObjectFactory)
  }

  companion object {
    fun ResourceTransformer.canTransformResource(path: String, file: File? = null): Boolean {
      val element =
        object : FileTreeElement by noOpDelegate() {
          private val _relativePath = RelativePath.parse(true, path)

          override fun getPath(): String = _relativePath.pathString

          override fun getRelativePath(): RelativePath = _relativePath

          override fun getFile(): File = requireNotNull(file) { "File must be provided." }
        }
      return canTransformResource(element)
    }

    fun resourceContext(path: String, vararg relocators: Relocator): TransformerContext {
      return TransformerContext(
        path = path,
        inputStream = requireResourceAsStream(path),
        relocators = relocators.toSet(),
      )
    }

    fun textContext(
      path: String,
      text: String = "",
      vararg relocators: Relocator,
    ): TransformerContext {
      return TransformerContext(
        path = path,
        inputStream = text.byteInputStream(),
        relocators = relocators.toSet(),
      )
    }

    fun ResourceTransformer.transformToJar(preserveFileTimestamps: Boolean = true): JarPath {
      val testableZipPath = createTempFile("testable-zip-file-", ".jar")
      ZipOutputStream(testableZipPath.outputStream()).use { zipOutputStream ->
        modifyOutputStream(zipOutputStream, preserveFileTimestamps)
      }
      return JarPath(testableZipPath)
    }

    /**
     * NOTE: The Turkish locale has a usual case transformation for the letters "I" and "i", making
     * it a prime choice to test for improper case-less string comparisons.
     */
    fun setupTurkishLocale() {
      @Suppress("DEPRECATION") Locale.setDefault(Locale("tr"))
    }
  }
}
