package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.impl.RelocatorRemapper
import com.github.jengelman.gradle.plugins.shadow.internal.UnusedTracker
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.GregorianCalendar
import java.util.zip.ZipException
import org.apache.commons.io.FilenameUtils
import org.apache.log4j.LogManager
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.Zip64RequiredException
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipFile
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.UncheckedIOException
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FilePermissions
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.DefaultFilePermissions
import org.gradle.api.internal.file.DefaultFileTreeElement
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

class ShadowCopyAction(
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
  private val unusedTracker: UnusedTracker,
) : CopyAction {
  private val logger = LogManager.getLogger(this::class.java)

  override fun execute(stream: CopyActionProcessingStream): WorkResult {
    val unusedClasses: Set<String> = if (minimizeJar) {
      stream.process(object : BaseStreamAction() {
        override fun visitFile(fileDetails: FileCopyDetails) {
          if (isArchive(fileDetails)) {
            unusedTracker.addDependency(fileDetails.file)
          }
        }
      })
      unusedTracker.findUnused()
    } else {
      emptySet()
    }

    val zipOutStr = try {
      compressor.createArchiveOutputStream(zipFile) as ZipOutputStream
    } catch (e: Exception) {
      throw GradleException("Could not create ZIP '$zipFile'", e)
    }

    try {
      zipOutStr.use { outputStream ->
        try {
          val action =
            StreamAction(outputStream, encoding, transformers, relocators, patternSet, unusedClasses, stats)
          stream.process(action)
          processTransformers(outputStream)
        } catch (e: Exception) {
          throw e
        }
      }
    } catch (e: UncheckedIOException) {
      if (e.cause is Zip64RequiredException) {
        val message = (e.cause as Zip64RequiredException).message
        throw Zip64RequiredException(
          "$message\n\nTo build this archive, please enable the zip64 extension." +
            "\nSee: ${documentationRegistry.getDslRefForProperty(Zip::class.java, "zip64")}",
        )
      }
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

  abstract inner class BaseStreamAction : CopyActionProcessingStreamAction {
    protected fun isArchive(fileDetails: FileCopyDetails): Boolean {
      return fileDetails.relativePath.pathString.endsWith(".jar")
    }

    protected fun isClass(fileDetails: FileCopyDetails): Boolean {
      return FilenameUtils.getExtension(fileDetails.path) == "class"
    }

    override fun processFile(details: FileCopyDetailsInternal) {
      if (details.isDirectory) {
        visitDir(details)
      } else {
        visitFile(details)
      }
    }

    protected open fun visitDir(dirDetails: FileCopyDetails) = Unit

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

    private fun recordVisit(path: RelativePath): Boolean {
      return visitedFiles.add(path.pathString)
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

    private fun processArchive(fileDetails: FileCopyDetails) {
      stats.startJar()
      ZipFile(fileDetails.file).use { archive ->
        val archiveElements = archive.entries.toList().map { ArchiveFileTreeElement(RelativeArchivePath(it)) }
        val patternSpec = patternSet.asSpec
        val filteredArchiveElements =
          archiveElements.filter { patternSpec.isSatisfiedBy(it.asFileTreeElement()) }
        filteredArchiveElements.forEach { archiveElement ->
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
      val className = FilenameUtils.removeExtension(classPath)
        .replace('/', '.')
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
      if (FilenameUtils.getExtension(fileCopyDetails.name) == "class") {
        fileCopyDetails.file.inputStream().use { inputStream ->
          remapClass(
            inputStream,
            fileCopyDetails.path,
            fileCopyDetails.lastModified,
          )
        }
      }
    }

    private fun remapClass(classInputStream: InputStream, path: String, lastModified: Long) {
      val cw = ClassWriter(0)
      val cv = ClassRemapper(cw, remapper)

      classInputStream.use {
        try {
          ClassReader(it).accept(cv, ClassReader.EXPAND_FRAMES)
        } catch (ise: Throwable) {
          throw GradleException("Error in ASM processing class $path", ise)
        }
      }

      val renamedClass = cw.toByteArray()
      val multiReleasePrefix = "^META-INF/versions/\\d+/".toRegex().find(path)?.value.orEmpty()
      val mappedName = multiReleasePrefix + remapper.mapPath(path.replace(multiReleasePrefix, ""))

      renamedClass.inputStream().use { bis ->
        try {
          val archiveEntry = ZipEntry("$mappedName.class")
          archiveEntry.time = getArchiveTimeFor(lastModified)
          zipOutStr.putNextEntry(archiveEntry)
          bis.copyTo(zipOutStr)
          zipOutStr.closeEntry()
        } catch (ignored: ZipException) {
        }
      }
    }

    private fun copyArchiveEntry(archiveFile: RelativeArchivePath, archive: ZipFile) {
      val mappedPath = remapper.map(archiveFile.entry.name)
      val entry = ZipEntry(mappedPath)
      entry.time = getArchiveTimeFor(archiveFile.entry.time)
      val mappedFile = RelativeArchivePath(entry)
      addParentDirectories(mappedFile)
      zipOutStr.putNextEntry(mappedFile.entry)
      archive.getInputStream(archiveFile.entry).copyTo(zipOutStr)
      zipOutStr.closeEntry()
    }

    override fun visitDir(dirDetails: FileCopyDetails) {
      try {
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
      inputStream.use {
        val mappedPath = remapper.map(element.relativePath.pathString)
        transformers.find { it.canTransformResource(element) }
          ?.transform(
            TransformerContext.builder()
              .path(mappedPath)
              .inputStream(it)
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

  inner class RelativeArchivePath(val entry: ZipEntry) :
    RelativePath(
      !entry.isDirectory,
      *entry.name.split("/").toTypedArray(),
    ) {

    val isClassFile: Boolean
      get() = lastName.endsWith(".class")

    override fun getParent(): RelativeArchivePath {
      return if (segments.isEmpty() || segments.size == 1) {
        // TODO: the return type must be non-nullable
        null!!
      } else {
        val path = segments.dropLast(1).joinToString("/") + "/"
        RelativeArchivePath(setArchiveTimes(ZipEntry(path)))
      }
    }
  }

  class ArchiveFileTreeElement(private val archivePath: RelativeArchivePath) : FileTreeElement {

    val isClassFile: Boolean
      get() = archivePath.isClassFile

    override fun getFile(): File = error("Not supported")

    override fun isDirectory(): Boolean = archivePath.entry.isDirectory

    override fun getLastModified(): Long = archivePath.entry.lastModifiedDate.time

    override fun getSize(): Long = archivePath.entry.size

    override fun open(): InputStream = error("Not supported")

    override fun copyTo(outputStream: OutputStream) {}

    override fun copyTo(file: File): Boolean = false

    override fun getName(): String = archivePath.pathString

    override fun getPath(): String = archivePath.lastName

    override fun getRelativePath(): RelativeArchivePath = archivePath

    override fun getMode(): Int = archivePath.entry.unixMode

    override fun getPermissions(): FilePermissions = DefaultFilePermissions(mode)

    fun asFileTreeElement(): FileTreeElement {
      // TODO: the param types must be non-nullable
      return DefaultFileTreeElement(
        null!!,
        RelativePath(!isDirectory, *archivePath.segments),
        null!!,
        null!!,
      )
    }
  }

  companion object {
    val CONSTANT_TIME_FOR_ZIP_ENTRIES = GregorianCalendar(1980, 1, 1, 0, 0, 0).timeInMillis
  }
}
