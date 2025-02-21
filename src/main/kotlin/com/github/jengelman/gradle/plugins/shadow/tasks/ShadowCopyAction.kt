package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.internal.RelocatorRemapper
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.createDefaultFileTreeElement
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.GregorianCalendar
import java.util.zip.ZipException
import kotlin.sequences.forEach
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.Zip64RequiredException
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipFile
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FilePermissions
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.DefaultFilePermissions
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.logging.Logger
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
        stream.process(
          StreamAction(
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

  private class StreamAction(
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
      logger.info("Relocator count: ${relocators.size}.")
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
      private val FileCopyDetails.isClass: Boolean get() = relativePath.pathString.endsWith(CLASS_SUFFIX)
      private val FileCopyDetails.isJar: Boolean get() = relativePath.pathString.endsWith(".jar")
    }
  }

  public companion object {
    private const val CLASS_SUFFIX = ".class"
    private val logger = Logging.getLogger(ShadowCopyAction::class.java)
    public val CONSTANT_TIME_FOR_ZIP_ENTRIES: Long = GregorianCalendar(1980, 1, 1, 0, 0, 0).timeInMillis
  }
}
