package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction.ArchiveFileTreeElement
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction.RelativeArchivePath
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipException
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.ZipFile
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
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
  private val zipFile: File,
  private val preserveFileTimestamps: Boolean,
  private val logger: Logger,
) : CopyActionProcessingStreamAction {
  private val remapper = RelocatorRemapper(relocators)
  private val visitedFiles = mutableSetOf<String>()

  init {
    if (encoding != null) {
      this.zipOutStr.setEncoding(encoding)
    }
  }

  override fun processFile(details: FileCopyDetailsInternal) {
    if (details.isDirectory) visitDir(details) else visitFile(details)
  }

  private fun visitFile(fileDetails: FileCopyDetails) {
    if (fileDetails.isJar) {
      processArchive(fileDetails)
    } else {
      try {
        val isClass = fileDetails.isClass
        if (!remapper.hasRelocators() || !isClass) {
          if (isTransformable(fileDetails)) {
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
          remapClass(fileDetails)
        }
        recordVisit(fileDetails.relativePath)
      } catch (e: Exception) {
        throw GradleException("Could not add $fileDetails to ZIP '$zipFile'.", e)
      }
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
      recordVisit(dirDetails.relativePath)
    } catch (e: Exception) {
      throw GradleException("Could not add $dirDetails to ZIP '$zipFile'.", e)
    }
  }

  private fun recordVisit(path: RelativePath): Boolean {
    return visitedFiles.add(path.pathString)
  }

  private fun processArchive(fileDetails: FileCopyDetails) {
    ZipFile(fileDetails.file).use { archive ->
      archive.entries.asSequence()
        .map {
          ArchiveFileTreeElement(RelativeArchivePath(it, preserveFileTimestamps))
        }
        .filter {
          patternSet.asSpec.isSatisfiedBy(it.asFileTreeElement())
        }.forEach { archiveElement ->
          if (archiveElement.relativePath.isFile) {
            visitArchiveFile(archiveElement, archive)
          }
        }
    }
  }

  private fun visitArchiveDirectory(archiveDir: RelativeArchivePath) {
    if (recordVisit(archiveDir)) {
      zipOutStr.putNextEntry(archiveDir.entry)
      zipOutStr.closeEntry()
    }
  }

  private fun visitArchiveFile(archiveFile: ArchiveFileTreeElement, archive: ZipFile) {
    val archiveFilePath = archiveFile.relativePath
    if (archiveFile.isClass || !isTransformable(archiveFile)) {
      if (recordVisit(archiveFilePath) && !isUnused(archiveFilePath.entry.name)) {
        if (!remapper.hasRelocators() || !archiveFile.isClass) {
          copyArchiveEntry(archiveFilePath, archive)
        } else {
          remapClass(archiveFilePath, archive)
        }
      }
    } else {
      transform(archiveFile, archive)
    }
  }

  private fun addParentDirectories(file: RelativeArchivePath?) {
    file?.let {
      addParentDirectories(it.parent)
      if (!it.isFile) {
        visitArchiveDirectory(it)
      }
    }
  }

  private fun isUnused(classPath: String): Boolean {
    val classPathWithoutExtension = classPath.substringBeforeLast(".")
    val className = classPathWithoutExtension.replace('/', '.')
    val result = unusedClasses.contains(className)
    if (result) {
      logger.debug("Dropping unused class: $className")
    }
    return result
  }

  private fun remapClass(file: RelativeArchivePath, archive: ZipFile) {
    if (file.isClass) {
      val entry = zipEntry(remapper.mapPath(file) + CLASS_SUFFIX, preserveFileTimestamps)
      addParentDirectories(RelativeArchivePath(entry, preserveFileTimestamps))
      remapClass(archive.getInputStream(file.entry), file.pathString, file.entry.time)
    }
  }

  private fun remapClass(fileCopyDetails: FileCopyDetails) {
    if (fileCopyDetails.isClass) {
      fileCopyDetails.file.inputStream().use {
        remapClass(it, fileCopyDetails.path, fileCopyDetails.lastModified)
      }
    }
  }

  /**
   * Applies remapping to the given class with the specified relocation path. The remapped class is then written
   * to the zip file. [classInputStream] is closed automatically to prevent future file leaks.
   * See #364 and #408.
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
      renamedClass.inputStream().use {
        it.copyTo(zipOutStr)
      }
      zipOutStr.closeEntry()
    } catch (_: ZipException) {
      logger.warn("We have a duplicate $mappedName in source project")
    }
  }

  private fun copyArchiveEntry(archiveFile: RelativeArchivePath, archive: ZipFile) {
    val mappedPath = remapper.map(archiveFile.entry.name)
    val entry = zipEntry(mappedPath, preserveFileTimestamps, archiveFile.entry.time)
    val mappedFile = RelativeArchivePath(entry, preserveFileTimestamps)
    addParentDirectories(mappedFile)
    zipOutStr.putNextEntry(mappedFile.entry)
    archive.getInputStream(archiveFile.entry).use {
      it.copyTo(zipOutStr)
    }
    zipOutStr.closeEntry()
  }

  private fun transform(element: ArchiveFileTreeElement, archive: ZipFile) {
    transformAndClose(element, archive.getInputStream(element.relativePath.entry))
  }

  private fun transform(details: FileCopyDetails) {
    transformAndClose(details, details.file.inputStream())
  }

  private fun transformAndClose(element: FileTreeElement, inputStream: InputStream) {
    inputStream.use { steam ->
      val mappedPath = remapper.map(element.relativePath.pathString)
      transformers.find {
        it.canTransformResource(element)
      }?.transform(
        TransformerContext(
          path = mappedPath,
          inputStream = steam,
          relocators = relocators,
        ),
      )
    }
  }

  private fun isTransformable(element: FileTreeElement): Boolean {
    return transformers.any { it.canTransformResource(element) }
  }

  companion object {
    const val CLASS_SUFFIX = ".class"

    private val FileCopyDetails.isClass: Boolean get() = relativePath.pathString.endsWith(CLASS_SUFFIX)
    private val FileCopyDetails.isJar: Boolean get() = relativePath.pathString.endsWith(".jar")
  }
}
