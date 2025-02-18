package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.internal.RealStreamAction
import com.github.jengelman.gradle.plugins.shadow.internal.RealStreamAction.Companion.CLASS_SUFFIX
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.createDefaultFileTreeElement
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.GregorianCalendar
import org.apache.tools.zip.Zip64RequiredException
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.file.FilePermissions
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.DefaultFilePermissions
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.util.PatternSet

/**
 * Modified from [org.gradle.api.internal.file.archive.ZipCopyAction.java](https://github.com/gradle/gradle/blob/b893c2b085046677cf858fb3d5ce00e68e556c3a/platforms/core-configuration/file-operations/src/main/java/org/gradle/api/internal/file/archive/ZipCopyAction.java).
 */
public open class ShadowCopyAction(
  private val zipFile: File,
  private val compressor: ZipCompressor,
  private val documentationRegistry: DocumentationRegistry,
  private val encoding: String?,
  private val transformers: Set<Transformer>,
  private val relocators: Set<Relocator>,
  private val patternSet: PatternSet,
  private val preserveFileTimestamps: Boolean,
  private val unusedClasses: Set<String>,
) : CopyAction {

  override fun execute(stream: CopyActionProcessingStream): WorkResult {
    val zipOutStream = try {
      compressor.createArchiveOutputStream(zipFile)
    } catch (e: Exception) {
      throw GradleException("Could not create ZIP '$zipFile'.", e)
    }

    try {
      zipOutStream.use { zos ->
        stream.process(
          RealStreamAction(
            zos,
            encoding,
            transformers,
            relocators,
            patternSet,
            unusedClasses,
            zipFile,
            preserveFileTimestamps,
            logger,
          ),
        )
        processTransformers(zos)
      }
    } catch (e: Exception) {
      if (e.cause is Zip64RequiredException) {
        throw Zip64RequiredException(
          "${e.cause?.message}\n\nTo build this archive, please enable the zip64 extension.\n" +
            "See: ${documentationRegistry.getDslRefForProperty(Zip::class.java, "zip64")}",
        )
      }
      zipFile.delete()
      // Rethrow the exception like `java.util.zip.ZipException: archive is not a ZIP archive`.
      throw e
    }
    return WorkResults.didWork(true)
  }

  private fun processTransformers(zos: ZipOutputStream) {
    transformers.forEach { transformer ->
      if (transformer.hasTransformedResource()) {
        transformer.modifyOutputStream(zos, preserveFileTimestamps)
      }
    }
  }

  public open class RelativeArchivePath(
    public open val entry: ZipEntry,
    private val preserveFileTimestamps: Boolean,
  ) : RelativePath(
    !entry.isDirectory,
    // `dir/` will be split into ["dir", ""], we have to trim empty segments here.
    *entry.name.split('/').filter(CharSequence::isNotEmpty).toTypedArray(),
  ) {
    public open val isClass: Boolean get() = lastName.endsWith(CLASS_SUFFIX)

    @Suppress("WRONG_NULLABILITY_FOR_JAVA_OVERRIDE") // It could return null in super.getParent().
    override fun getParent(): RelativeArchivePath? {
      return if (segments.size <= 1) {
        null
      } else {
        // Parent is always a directory so add / to the end of the path.
        val parentPath = segments.dropLast(1).joinToString("/") + "/"
        RelativeArchivePath(
          zipEntry(parentPath, preserveFileTimestamps),
          preserveFileTimestamps,
        )
      }
    }
  }

  public open class ArchiveFileTreeElement(
    private val archivePath: RelativeArchivePath,
  ) : FileTreeElement {
    public open val isClass: Boolean get() = archivePath.isClass

    override fun isDirectory(): Boolean = archivePath.entry.isDirectory

    override fun getLastModified(): Long = archivePath.entry.lastModifiedDate.time

    override fun getSize(): Long = archivePath.entry.size

    override fun getName(): String = archivePath.pathString

    override fun getPath(): String = archivePath.lastName

    override fun getRelativePath(): RelativeArchivePath = archivePath

    @Deprecated("Deprecated in Java")
    override fun getMode(): Int = archivePath.entry.unixMode

    override fun getPermissions(): FilePermissions = DefaultFilePermissions(mode)

    public open fun asFileTreeElement(): FileTreeElement {
      return createDefaultFileTreeElement(relativePath = RelativePath(!isDirectory, *archivePath.segments))
    }

    override fun getFile(): File = throw UnsupportedOperationException()
    override fun open(): InputStream = throw UnsupportedOperationException()
    override fun copyTo(outputStream: OutputStream): Unit = throw UnsupportedOperationException()
    override fun copyTo(file: File): Boolean = throw UnsupportedOperationException()
  }

  public companion object {
    private val logger = Logging.getLogger(ShadowCopyAction::class.java)
    public val CONSTANT_TIME_FOR_ZIP_ENTRIES: Long = GregorianCalendar(1980, 1, 1, 0, 0, 0).timeInMillis
  }
}
