package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.impl.RelocatorRemapper
import com.github.jengelman.gradle.plugins.shadow.internal.UnusedTracker
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.createDefaultFileTreeElement
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.GregorianCalendar
import java.util.zip.ZipException
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
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.util.PatternSet
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.slf4j.LoggerFactory

public open class ShadowCopyAction internal constructor(
  private val zipFile: File,
  private val compressor: ZipCompressor,
  private val documentationRegistry: DocumentationRegistry,
  private val encoding: String?,
  private val transformers: List<Transformer>,
  private val relocators: List<Relocator>,
  private val patternSet: PatternSet,
  private val stats: ShadowStats,
  private val preserveFileTimestamps: Boolean,
  private val minimizeJar: Boolean,
  private val unusedTracker: UnusedTracker?,
) : CopyAction {

  public constructor(
    zipFile: File,
    compressor: ZipCompressor,
    documentationRegistry: DocumentationRegistry,
    encoding: String?,
    transformers: List<Transformer>,
    relocators: List<Relocator>,
    patternSet: PatternSet,
    stats: ShadowStats,
    preserveFileTimestamps: Boolean,
    minimizeJar: Boolean,
  ) : this(
    zipFile,
    compressor,
    documentationRegistry,
    encoding,
    transformers,
    relocators,
    patternSet,
    stats,
    preserveFileTimestamps,
    minimizeJar,
    null,
  )

  override fun execute(stream: CopyActionProcessingStream): WorkResult {
    val unusedClasses = if (minimizeJar && unusedTracker != null) {
      stream.process(
        object : BaseStreamAction() {
          override fun visitFile(fileDetails: FileCopyDetails) {
            // All project sources are already present, we just need
            // to deal with JAR dependencies.
            if (isArchive(fileDetails)) {
              unusedTracker.addDependency(fileDetails.file)
            }
          }
        },
      )
      unusedTracker.findUnused()
    } else {
      emptySet()
    }

    val zipOutStream = try {
      compressor.createArchiveOutputStream(zipFile) as ZipOutputStream
    } catch (e: Exception) {
      throw GradleException("Could not create ZIP '$zipFile'", e)
    }

    try {
      zipOutStream.use { outputStream ->
        stream.process(
          StreamAction(
            outputStream,
            encoding,
            transformers,
            relocators,
            patternSet,
            unusedClasses,
            stats,
          ),
        )
        processTransformers(outputStream)
      }
    } catch (e: IOException) {
      throw Zip64RequiredException(
        "${e.cause?.message}\n\nTo build this archive, please enable the zip64 extension.\n" +
          "See: ${documentationRegistry.getDslRefForProperty(Zip::class.java, "zip64")}",
      )
    }
    return WorkResults.didWork(true)
  }

  private fun processTransformers(stream: ZipOutputStream) {
    transformers.forEach { transformer ->
      if (transformer.hasTransformedResource()) {
        transformer.modifyOutputStream(stream, preserveFileTimestamps)
      }
    }
  }

  private fun getArchiveTimeFor(timestamp: Long): Long {
    return if (preserveFileTimestamps) timestamp else CONSTANT_TIME_FOR_ZIP_ENTRIES
  }

  private fun setArchiveTimes(zipEntry: ZipEntry): ZipEntry {
    if (!preserveFileTimestamps) {
      zipEntry.time = CONSTANT_TIME_FOR_ZIP_ENTRIES
    }
    return zipEntry
  }

  public abstract class BaseStreamAction : CopyActionProcessingStreamAction {
    protected fun isArchive(fileDetails: FileCopyDetails): Boolean {
      return fileDetails.relativePath.pathString.endsWith(".jar")
    }

    protected fun isClass(fileDetails: FileCopyDetails): Boolean {
      return fileDetails.path.endsWith(".class")
    }

    override fun processFile(details: FileCopyDetailsInternal) {
      if (details.isDirectory) visitDir(details) else visitFile(details)
    }

    protected open fun visitDir(dirDetails: FileCopyDetails) {}

    protected abstract fun visitFile(fileDetails: FileCopyDetails)
  }

  private inner class StreamAction(
    private val zipOutStr: ZipOutputStream,
    encoding: String?,
    private val transformers: List<Transformer>,
    private val relocators: List<Relocator>,
    private val patternSet: PatternSet,
    private val unused: Set<String>,
    private val stats: ShadowStats,
  ) : BaseStreamAction() {
    private val remapper = RelocatorRemapper(relocators, stats)
    private val visitedFiles = mutableSetOf<String>()

    init {
      if (encoding != null) {
        this.zipOutStr.setEncoding(encoding)
      }
    }

    override fun visitFile(fileDetails: FileCopyDetails) {
      if (!isArchive(fileDetails)) {
        try {
          val isClass = isClass(fileDetails)
          if (!remapper.hasRelocators() || !isClass) {
            if (!isTransformable(fileDetails)) {
              val mappedPath = remapper.map(fileDetails.relativePath.pathString)
              val archiveEntry = ZipEntry(mappedPath)
              archiveEntry.time = getArchiveTimeFor(fileDetails.lastModified)
              archiveEntry.unixMode = UnixStat.FILE_FLAG or fileDetails.permissions.toUnixNumeric()
              zipOutStr.putNextEntry(archiveEntry)
              fileDetails.copyTo(zipOutStr)
              zipOutStr.closeEntry()
            } else {
              transform(fileDetails)
            }
          } else if (isClass && !isUnused(fileDetails.path)) {
            remapClass(fileDetails)
          }
          recordVisit(fileDetails.relativePath)
        } catch (e: Exception) {
          throw GradleException("Could not add $fileDetails to ZIP '$zipFile'.", e)
        }
      } else {
        processArchive(fileDetails)
      }
    }

    private fun recordVisit(path: RelativePath): Boolean {
      return visitedFiles.add(path.pathString)
    }

    private fun processArchive(fileDetails: FileCopyDetails) {
      stats.startJar()
      ZipFile(fileDetails.file).use { archive ->
        archive.entries.asSequence()
          .map {
            ArchiveFileTreeElement(RelativeArchivePath(it))
          }
          .filter {
            patternSet.asSpec.isSatisfiedBy(it.asFileTreeElement())
          }.forEach { archiveElement ->
            if (archiveElement.relativePath.isFile) {
              visitArchiveFile(archiveElement, archive)
            }
          }
      }
      stats.finishJar()
    }

    private fun visitArchiveDirectory(archiveDir: RelativeArchivePath) {
      if (recordVisit(archiveDir)) {
        zipOutStr.putNextEntry(archiveDir.entry)
        zipOutStr.closeEntry()
      }
    }

    private fun visitArchiveFile(archiveFile: ArchiveFileTreeElement, archive: ZipFile) {
      val archiveFilePath = archiveFile.relativePath
      if (archiveFile.isClassFile || !isTransformable(archiveFile)) {
        if (recordVisit(archiveFilePath) && !isUnused(archiveFilePath.entry.name)) {
          if (!remapper.hasRelocators() || !archiveFile.isClassFile) {
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
      val result = unused.contains(className)
      if (result) {
        logger.debug("Dropping unused class: $className")
      }
      return result
    }

    private fun remapClass(file: RelativeArchivePath, archive: ZipFile) {
      if (file.isClassFile) {
        val zipEntry = setArchiveTimes(ZipEntry(remapper.mapPath(file) + ".class"))
        addParentDirectories(RelativeArchivePath(zipEntry))
        remapClass(archive.getInputStream(file.entry), file.pathString, file.entry.time)
      }
    }

    private fun remapClass(fileCopyDetails: FileCopyDetails) {
      if (fileCopyDetails.name.endsWith(".class")) {
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
      // constant pool are never used), but confuses some tools such as Felix' maven-bundle-plugin
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
        val archiveEntry = ZipEntry("$mappedName.class")
        archiveEntry.time = getArchiveTimeFor(lastModified)
        zipOutStr.putNextEntry(archiveEntry)
        renamedClass.inputStream().use {
          it.copyTo(zipOutStr)
        }
        zipOutStr.closeEntry()
      } catch (ignored: ZipException) {
        logger.warn("We have a duplicate $mappedName in source project")
      }
    }

    private fun copyArchiveEntry(archiveFile: RelativeArchivePath, archive: ZipFile) {
      val mappedPath = remapper.map(archiveFile.entry.name)
      val entry = ZipEntry(mappedPath)
      entry.time = getArchiveTimeFor(archiveFile.entry.time)
      val mappedFile = RelativeArchivePath(entry)
      addParentDirectories(mappedFile)
      zipOutStr.putNextEntry(mappedFile.entry)
      archive.getInputStream(archiveFile.entry).use {
        it.copyTo(zipOutStr)
      }
      zipOutStr.closeEntry()
    }

    override fun visitDir(dirDetails: FileCopyDetails) {
      try {
        // Trailing slash in name indicates that entry is a directory
        val path = dirDetails.relativePath.pathString + "/"
        val archiveEntry = ZipEntry(path)
        archiveEntry.time = getArchiveTimeFor(dirDetails.lastModified)
        archiveEntry.unixMode = UnixStat.DIR_FLAG or dirDetails.permissions.toUnixNumeric()
        zipOutStr.putNextEntry(archiveEntry)
        zipOutStr.closeEntry()
        recordVisit(dirDetails.relativePath)
      } catch (e: Exception) {
        throw GradleException("Could not add $dirDetails to ZIP '$zipFile'.", e)
      }
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
          TransformerContext.builder()
            .path(mappedPath)
            .inputStream(steam)
            .relocators(relocators)
            .stats(stats)
            .build(),
        )
      }
    }

    private fun isTransformable(element: FileTreeElement): Boolean {
      return transformers.any { it.canTransformResource(element) }
    }
  }

  public open inner class RelativeArchivePath(
    public open val entry: ZipEntry,
  ) : RelativePath(
    !entry.isDirectory,
    // `dir/` will be split into ["dir", ""], we have to trim empty segments here.
    *entry.name.split('/').dropLastWhile(CharSequence::isEmpty).toTypedArray(),
  ) {
    public open val isClassFile: Boolean get() = lastName.endsWith(".class")

    override fun getParent(): RelativeArchivePath? {
      return if (segments.size <= 1) {
        null
      } else {
        // Parent is always a directory so add / to the end of the path
        val parentPath = segments.dropLast(1).joinToString("/") + "/"
        val entry = setArchiveTimes(ZipEntry(parentPath))
        RelativeArchivePath(entry)
      }
    }
  }

  public open class ArchiveFileTreeElement(
    private val archivePath: RelativeArchivePath,
  ) : FileTreeElement {
    public open val isClassFile: Boolean get() = archivePath.isClassFile

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
    private val logger = LoggerFactory.getLogger(ShadowCopyAction::class.java)
    public val CONSTANT_TIME_FOR_ZIP_ENTRIES: Long = GregorianCalendar(1980, 1, 1, 0, 0, 0).timeInMillis
  }
}
