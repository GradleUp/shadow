package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.internal.RelocatorRemapper
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import java.io.File
import java.io.InputStream
import java.util.GregorianCalendar
import java.util.zip.ZipException
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.Zip64RequiredException
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.util.PatternSet
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper

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
        stream.process(StreamAction(zos))
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

  private inner class StreamAction(
    private val zipOutStr: ZipOutputStream,
  ) : CopyActionProcessingStreamAction {
    private val remapper = RelocatorRemapper(relocators)

    init {
      if (encoding != null) {
        zipOutStr.setEncoding(encoding)
      }
    }

    override fun processFile(details: FileCopyDetailsInternal) {
      if (!patternSet.asSpec.isSatisfiedBy(details)) return
      if (details.isDirectory) visitDir(details) else visitFile(details)
    }

    private fun visitFile(fileDetails: FileCopyDetails) {
      try {
        val relativePath = fileDetails.relativePath.pathString
        if (relativePath.endsWith(".class")) {
          if (isUnused(fileDetails.path)) return
          if (relocators.isEmpty()) {
            fileDetails.writeToZip()
            return
          }
          fileDetails.file.inputStream().use { stream ->
            remapClass(stream, fileDetails.path, fileDetails.lastModified)
          }
        } else {
          if (!transform(fileDetails)) {
            fileDetails.writeToZip(remapper.map(relativePath))
          }
        }
      } catch (e: Exception) {
        throw GradleException("Could not add $fileDetails to ZIP '$zipFile'.", e)
      }
    }

    private fun visitDir(dirDetails: FileCopyDetails) {
      try {
        val mappedPath = if (relocators.isEmpty()) {
          dirDetails.relativePath.pathString
        } else {
          remapper.map(dirDetails.relativePath.pathString)
        }
        dirDetails.writeToZip("$mappedPath/")
      } catch (e: Exception) {
        throw GradleException("Could not add $dirDetails to ZIP '$zipFile'.", e)
      }
    }

    private fun isUnused(classPath: String): Boolean {
      val classPathWithoutExtension = classPath.substringBeforeLast(".")
      val className = classPathWithoutExtension.replace('/', '.')
      return unusedClasses.contains(className).also {
        if (it) {
          logger.debug("Dropping unused class: $className")
        }
      }
    }

    /**
     * Applies remapping to the given class with the specified relocation path. The remapped class is then written
     * to the zip file. [classInputStream] is closed automatically to prevent future file leaks.
     *
     * See [issue 364](https://github.com/GradleUp/shadow/issues/364) and [issue 408](https://github.com/GradleUp/shadow/issues/408).
     */
    private fun remapClass(classInputStream: InputStream, path: String, lastModified: Long) {
      // We don't pass the ClassReader here. This forces the ClassWriter to rebuild the constant pool.
      // Copying the original constant pool should be avoided because it would keep references
      // to the original class names. This is not a problem at runtime (because these entries in the
      // constant pool are never used), but confuses some tools such as Felix's maven-bundle-plugin
      // that use the constant pool to determine the dependencies of a class.
      val cw = ClassWriter(0)
      val cr = ClassReader(classInputStream)
      val cv = ClassRemapper(cw, remapper)

      try {
        cr.accept(cv, ClassReader.EXPAND_FRAMES)
      } catch (t: Throwable) {
        throw GradleException("Error in ASM processing class $path", t)
      }

      // Temporarily remove the multi-release prefix.
      val multiReleasePrefix = "^META-INF/versions/\\d+/".toRegex().find(path)?.value.orEmpty()
      val newPath = path.replace(multiReleasePrefix, "")
      val mappedName = multiReleasePrefix + remapper.mapPath(newPath)
      try {
        // Now we put it back on so the class file is written out with the right extension.
        zipOutStr.putNextEntry(zipEntry("$mappedName.class", preserveFileTimestamps, lastModified))
        zipOutStr.write(cw.toByteArray())
        zipOutStr.closeEntry()
      } catch (_: ZipException) {
        logger.warn("We have a duplicate $mappedName in source project")
      }
    }

    private fun transform(fileDetails: FileCopyDetails): Boolean {
      val transformer = transformers.find { it.canTransformResource(fileDetails) } ?: return false
      fileDetails.file.inputStream().use { steam ->
        transformer.transform(
          TransformerContext(
            path = remapper.map(fileDetails.relativePath.pathString),
            inputStream = steam,
            relocators = relocators,
          ),
        )
      }
      return true
    }

    private fun FileCopyDetails.writeToZip(
      // Trailing slash in name indicates that entry is a directory.
      entryName: String = if (isDirectory) relativePath.pathString + "/" else relativePath.pathString,
    ) {
      val entry = zipEntry(entryName, preserveFileTimestamps, lastModified) {
        val flag = if (isDirectory) UnixStat.DIR_FLAG else UnixStat.FILE_FLAG
        unixMode = flag or permissions.toUnixNumeric()
      }
      zipOutStr.putNextEntry(entry)
      if (!isDirectory) {
        copyTo(zipOutStr)
      }
      zipOutStr.closeEntry()
    }
  }

  public companion object {
    private val logger = Logging.getLogger(ShadowCopyAction::class.java)
    public val CONSTANT_TIME_FOR_ZIP_ENTRIES: Long = GregorianCalendar(1980, 1, 1, 0, 0, 0).timeInMillis
  }
}
