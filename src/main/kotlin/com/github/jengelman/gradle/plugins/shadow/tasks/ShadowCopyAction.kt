@file:Suppress(
  "InternalGradleApiUsage"
) // We have to use internal Gradle APIs to implement a CopyAction.

package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.internal.UnixMode
import com.github.jengelman.gradle.plugins.shadow.internal.cast
import com.github.jengelman.gradle.plugins.shadow.internal.parentDirectoryEntries
import com.github.jengelman.gradle.plugins.shadow.internal.remapClass
import com.github.jengelman.gradle.plugins.shadow.internal.writeEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import java.io.File
import org.apache.tools.zip.Zip64RequiredException
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults

/**
 * Modified from
 * [org.gradle.api.internal.file.archive.ZipCopyAction.java](https://github.com/gradle/gradle/blob/b893c2b085046677cf858fb3d5ce00e68e556c3a/platforms/core-configuration/file-operations/src/main/java/org/gradle/api/internal/file/archive/ZipCopyAction.java).
 */
@Deprecated("This should not be used as a public API. Will be made internal in Shadow 10.")
public open class ShadowCopyAction
internal constructor(
  private val zipFile: File,
  private val zipOutStream: ZipOutputStream,
  private val transformers: Set<ResourceTransformer>,
  private val relocators: Set<Relocator>,
  private val unusedClasses: Set<String>,
  private val isPreserveFileTimestamps: Boolean,
  private val failOnDuplicateEntries: Boolean,
) : CopyAction {
  @Suppress("unused") // For binary compatibility.
  public constructor(
    zipFile: File,
    zosProvider: (File) -> ZipOutputStream,
    transformers: Set<ResourceTransformer>,
    relocators: Set<Relocator>,
    unusedClasses: Set<String>,
    enableKotlinModuleRemapping: Boolean,
    preserveFileTimestamps: Boolean,
    failOnDuplicateEntries: Boolean,
    encoding: String?,
  ) : this(
    zipFile = zipFile,
    zipOutStream =
      try {
        zosProvider(zipFile)
      } catch (e: Exception) {
        throw GradleException("Could not create ZIP '$zipFile'.", e)
      },
    transformers = transformers,
    relocators = relocators,
    unusedClasses = unusedClasses,
    isPreserveFileTimestamps = preserveFileTimestamps,
    failOnDuplicateEntries = failOnDuplicateEntries,
  )

  private val visitedDirs = mutableMapOf<String, FileCopyDetails>()

  override fun execute(stream: CopyActionProcessingStream): WorkResult {
    try {
      zipOutStream.use { zos ->
        stream.process(StreamAction(zos))
        processTransformers(zos)
        addDirs(zos)
        checkDuplicateEntries(zos)
      }
    } catch (e: Exception) {
      if (e is Zip64RequiredException || e.cause is Zip64RequiredException) {
        val message = if (e is Zip64RequiredException) e.message else e.cause?.message
        throw Zip64RequiredException(
          """
            $message

            To build this archive, please enable the zip64 extension. e.g.
            ```kts
            tasks.shadowJar {
              isZip64 = true
            }
            ```
            See: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Zip.html#org.gradle.api.tasks.bundling.Zip:zip64 for more details.
          """
            .trimIndent()
        )
      }
      zipFile.delete()
      throw e
    }
    return WorkResults.didWork(true)
  }

  private fun processTransformers(zos: ZipOutputStream) {
    transformers.forEach { transformer ->
      if (transformer.hasTransformedResource()) {
        transformer.modifyOutputStream(zos, isPreserveFileTimestamps)
      }
    }
  }

  private fun addDirs(zos: ZipOutputStream) {
    @Suppress("UNCHECKED_CAST") val entries = zos.entries.map { it.name }
    val added = entries.toMutableSet()
    val currentTimeMillis = System.currentTimeMillis()

    entries.forEach { name ->
      name.parentDirectoryEntries().asReversed().forEach { entryName ->
        if (!added.add(entryName)) return@forEach
        val details = visitedDirs[entryName.removeSuffix("/")]
        val (lastModified, unixMode) =
          if (details == null) {
            currentTimeMillis to UnixMode.directory()
          } else {
            details.lastModified to UnixMode.directory(details.permissions.toUnixNumeric())
          }
        zos.writeEntry(
          name = entryName,
          preserveLastModified = isPreserveFileTimestamps,
          lastModified = lastModified,
          unixMode = unixMode,
        )
      }
    }
  }

  private fun checkDuplicateEntries(zos: ZipOutputStream) {
    val entries = zos.entries.map { it.name }
    val duplicates = entries.groupingBy { it }.eachCount().filter { it.value > 1 }
    if (duplicates.isNotEmpty()) {
      val dupEntries =
        duplicates.entries.joinToString(separator = "\n") { "${it.key} (${it.value} times)" }
      val message = "Duplicate entries found in the shadowed JAR: \n$dupEntries"
      if (failOnDuplicateEntries) {
        throw GradleException(message)
      } else {
        logger.warn(message)
      }
    }
  }

  private inner class StreamAction(private val zipOutStr: ZipOutputStream) :
    CopyActionProcessingStreamAction {
    init {
      logger.info("Relocator count: {}.", relocators.size)
    }

    override fun processFile(details: FileCopyDetailsInternal) {
      try {
        if (details.isDirectory) {
          visitedDirs[details.path] = details
        } else {
          visitFile(details)
        }
      } catch (e: Exception) {
        throw GradleException("Could not add $details to ZIP '$zipFile'.", e)
      }
    }

    private fun visitFile(fileDetails: FileCopyDetails) {
      val path = fileDetails.path
      when {
        path.endsWith(".class") -> {
          if (isUnused(path)) return
          if (relocators.isEmpty()) {
            fileDetails.writeToZip(path)
          } else {
            with(fileDetails) {
              // Temporarily remove the multi-release prefix.
              val multiReleasePrefix = multiReleaseRegex.find(path)?.value.orEmpty()
              val pathSuffix = path.removePrefix(multiReleasePrefix)
              val relocatedPath = multiReleasePrefix + relocators.relocatePath(pathSuffix)
              writeToZip(entryName = relocatedPath, bytes = remapClass(relocators = relocators))
            }
          }
        }
        else -> {
          val relocated = relocators.relocatePath(path)
          if (transform(fileDetails, relocated)) return
          fileDetails.writeToZip(relocated)
        }
      }
    }

    private fun isUnused(classPath: String): Boolean {
      val className = classPath.substringBeforeLast(".").replace('/', '.')
      return unusedClasses.contains(className).also {
        if (it) {
          logger.info("Dropping unused class: {}", className)
        }
      }
    }

    private fun transform(fileDetails: FileCopyDetails, path: String): Boolean {
      val transformer = transformers.find { it.canTransformResource(fileDetails) } ?: return false
      fileDetails.file.inputStream().use { inputStream ->
        transformer.transform(
          TransformerContext(path = path, inputStream = inputStream, relocators = relocators)
        )
      }
      return true
    }

    private fun FileCopyDetails.writeToZip(entryName: String, bytes: ByteArray? = null) {
      zipOutStr.writeEntry(
        name = entryName,
        preserveLastModified = isPreserveFileTimestamps,
        lastModified = lastModified,
        unixMode = UnixMode.file(permissions.toUnixNumeric()),
      ) {
        if (bytes == null) {
          copyTo(this)
        } else {
          write(bytes)
        }
      }
    }
  }

  public companion object {
    private val logger = Logging.getLogger(@Suppress("DEPRECATION") ShadowCopyAction::class.java)
    private val multiReleaseRegex = "^META-INF/versions/\\d+/".toRegex()

    private val ZipOutputStream.entries: List<ZipEntry>
      get() =
        this::class.java.getDeclaredField("entries").apply { isAccessible = true }.get(this).cast()

    @Deprecated(
      message =
        "Use `ShadowJar.CONSTANT_TIME_FOR_ZIP_ENTRIES` constant instead. This will be removed in Shadow 10.",
      replaceWith = ReplaceWith("ShadowJar.CONSTANT_TIME_FOR_ZIP_ENTRIES"),
    )
    public val CONSTANT_TIME_FOR_ZIP_ENTRIES: Long = ShadowJar.CONSTANT_TIME_FOR_ZIP_ENTRIES
  }
}
