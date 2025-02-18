package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipException
import kotlin.io.inputStream
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.util.PatternSet
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper

/**
 * Modified from [org.gradle.api.internal.file.archive.ZipCopyAction.StreamAction](https://github.com/gradle/gradle/blob/b893c2b085046677cf858fb3d5ce00e68e556c3a/platforms/core-configuration/file-operations/src/main/java/org/gradle/api/internal/file/archive/ZipCopyAction.java#L89).
 */
internal class RealStreamAction(
  private val zipOutStr: ZipOutputStream,
  encoding: String?,
  private val transformers: Set<Transformer>,
  private val relocators: Set<Relocator>,
  private val patternSet: PatternSet,
  private val unusedClasses: Set<String>,
  private val stats: ShadowStats,
  private val zipFile: File,
  private val preserveFileTimestamps: Boolean,
  private val logger: Logger,
) : CopyActionProcessingStreamAction {
  private val remapper = RelocatorRemapper(relocators, stats)

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
      val isClass = fileDetails.relativePath.pathString.endsWith(".class")
      if (!remapper.hasRelocators() || !isClass) {
        if (transformers.any { it.canTransformResource(fileDetails) }) {
          transform(fileDetails)
        } else {
          val mappedPath = remapper.map(fileDetails.relativePath.pathString)
          val entry = zipEntry(mappedPath, preserveFileTimestamps, fileDetails.lastModified) {
            unixMode = UnixStat.FILE_FLAG or fileDetails.permissions.toUnixNumeric()
          }
          zipOutStr.putNextEntry(entry)
          fileDetails.copyTo(zipOutStr)
          zipOutStr.closeEntry()
        }
      } else if (isClass && !isUnused(fileDetails.path)) {
        fileDetails.file.inputStream().use { stream ->
          remapClass(stream, fileDetails.path, fileDetails.lastModified)
        }
      }
    } catch (e: Exception) {
      throw GradleException("Could not add $fileDetails to ZIP '$zipFile'.", e)
    }
  }

  private fun visitDir(dirDetails: FileCopyDetails) {
    try {
      // Trailing slash in name indicates that entry is a directory.
      val path = dirDetails.relativePath.pathString + "/"
      val entry = zipEntry(path, preserveFileTimestamps, dirDetails.lastModified) {
        unixMode = UnixStat.DIR_FLAG or dirDetails.permissions.toUnixNumeric()
      }
      zipOutStr.putNextEntry(entry)
      zipOutStr.closeEntry()
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

    val renamedClass = cw.toByteArray()
    // Temporarily remove the multi-release prefix.
    val multiReleasePrefix = "^META-INF/versions/\\d+/".toRegex().find(path)?.value.orEmpty()
    val newPath = path.replace(multiReleasePrefix, "")
    val mappedName = multiReleasePrefix + remapper.mapPath(newPath)
    try {
      // Now we put it back on so the class file is written out with the right extension.
      zipOutStr.putNextEntry(zipEntry("$mappedName.class", preserveFileTimestamps, lastModified))
      zipOutStr.write(renamedClass)
      zipOutStr.closeEntry()
    } catch (_: ZipException) {
      logger.warn("We have a duplicate $mappedName in source project")
    }
  }

  private fun transform(fileDetails: FileCopyDetails) {
    fileDetails.file.inputStream().use { steam ->
      val mappedPath = remapper.map(fileDetails.relativePath.pathString)
      transformers.find {
        it.canTransformResource(fileDetails)
      }?.transform(
        TransformerContext(
          path = mappedPath,
          inputStream = steam,
          relocators = relocators,
          stats = stats,
        ),
      )
    }
  }
}
