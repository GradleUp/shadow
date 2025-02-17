package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.internal.RealStreamAction
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import java.io.File
import java.util.GregorianCalendar
import org.apache.tools.zip.Zip64RequiredException
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.internal.DocumentationRegistry
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
  private val stats: ShadowStats,
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
            stats,
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

  public companion object {
    private val logger = Logging.getLogger(ShadowCopyAction::class.java)
    public val CONSTANT_TIME_FOR_ZIP_ENTRIES: Long = GregorianCalendar(1980, 1, 1, 0, 0, 0).timeInMillis
  }
}
