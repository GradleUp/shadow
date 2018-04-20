package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.impl.RelocatorRemapper
import com.github.jengelman.gradle.plugins.shadow.internal.GradleVersionUtil
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import groovy.util.logging.Slf4j
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOUtils
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.Zip64RequiredException
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipFile
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.UncheckedIOException
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.UncheckedException
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper

import java.util.zip.ZipException

@Slf4j
class ShadowCopyAction implements CopyAction {

    private final File zipFile
    private final ZipCompressor compressor
    private final DocumentationRegistry documentationRegistry
    private final List<Transformer> transformers
    private final List<Relocator> relocators
    private final PatternSet patternSet
    private final ShadowStats stats
    private final String encoding
    private final GradleVersionUtil versionUtil

    ShadowCopyAction(File zipFile, ZipCompressor compressor, DocumentationRegistry documentationRegistry,
                            String encoding, List<Transformer> transformers, List<Relocator> relocators,
                            PatternSet patternSet, ShadowStats stats, GradleVersionUtil util) {

        this.zipFile = zipFile
        this.compressor = compressor
        this.documentationRegistry = documentationRegistry
        this.transformers = transformers
        this.relocators = relocators
        this.patternSet = patternSet
        this.stats = stats
        this.encoding = encoding
        this.versionUtil = util
    }

    @Override
    WorkResult execute(CopyActionProcessingStream stream) {
        final ZipOutputStream zipOutStr

        try {
            zipOutStr = compressor.createArchiveOutputStream(zipFile)
        } catch (Exception e) {
            throw new GradleException("Could not create ZIP '${zipFile.toString()}'", e)
        }

        try {
            withResource(zipOutStr, new Action<ZipOutputStream>() {
                void execute(ZipOutputStream outputStream) {
                    try {
                        stream.process(new StreamAction(outputStream, encoding, transformers, relocators, patternSet,
                                stats))
                        processTransformers(outputStream)
                    } catch (Exception e) {
                        log.error('ex', e)
                        //TODO this should not be rethrown
                        throw e
                    }
                }
            })
        } catch (UncheckedIOException e) {
            if (e.cause instanceof Zip64RequiredException) {
                throw new Zip64RequiredException(
                        String.format("%s\n\nTo build this archive, please enable the zip64 extension.\nSee: %s",
                                e.cause.message, documentationRegistry.getDslRefForProperty(Zip, "zip64"))
                )
            }
        }
        return versionUtil.getWorkResult(true)
    }

    private void processTransformers(ZipOutputStream stream) {
        transformers.each { Transformer transformer ->
            if (transformer.hasTransformedResource()) {
                transformer.modifyOutputStream(stream)
            }
        }
    }

    private static <T extends Closeable> void withResource(T resource, Action<? super T> action) {
        try {
            action.execute(resource)
        } catch(Throwable t) {
            try {
                resource.close()
            } catch (IOException e) {
                // Ignored
            }
            throw UncheckedException.throwAsUncheckedException(t)
        }

        try {
            resource.close()
        } catch (IOException e) {
            throw new UncheckedIOException(e)
        }
    }

    class StreamAction implements CopyActionProcessingStreamAction {

        private final ZipOutputStream zipOutStr
        private final List<Transformer> transformers
        private final List<Relocator> relocators
        private final RelocatorRemapper remapper
        private final PatternSet patternSet
        private final ShadowStats stats

        private Set<String> visitedFiles = new HashSet<String>()

        StreamAction(ZipOutputStream zipOutStr, String encoding, List<Transformer> transformers,
                            List<Relocator> relocators, PatternSet patternSet, ShadowStats stats) {
            this.zipOutStr = zipOutStr
            this.transformers = transformers
            this.relocators = relocators
            this.remapper = new RelocatorRemapper(relocators, stats)
            this.patternSet = patternSet
            this.stats = stats
            if(encoding != null) {
                this.zipOutStr.setEncoding(encoding)
            }
        }

        void processFile(FileCopyDetailsInternal details) {
            if (details.directory) {
                visitDir(details)
            } else {
                visitFile(details)
            }
        }

        private boolean isArchive(FileCopyDetails fileDetails) {
            return fileDetails.relativePath.pathString.endsWith('.jar')
        }

        private boolean recordVisit(RelativePath path) {
            return visitedFiles.add(path.pathString)
        }

        private void visitFile(FileCopyDetails fileDetails) {
            if (!isArchive(fileDetails)) {
                try {
                    boolean isClass = (FilenameUtils.getExtension(fileDetails.path) == 'class')
                    if (!remapper.hasRelocators() || !isClass) {
                        if (!isTransformable(fileDetails)) {
                            String mappedPath = remapper.map(fileDetails.relativePath.pathString)
                            ZipEntry archiveEntry = new ZipEntry(mappedPath)
                            archiveEntry.setTime(fileDetails.lastModified)
                            archiveEntry.unixMode = (UnixStat.FILE_FLAG | fileDetails.mode)
                            zipOutStr.putNextEntry(archiveEntry)
                            fileDetails.copyTo(zipOutStr)
                            zipOutStr.closeEntry()
                        } else {
                            transform(fileDetails)
                        }
                    } else if (isClass) {
                        remapClass(fileDetails)
                    }
                    recordVisit(fileDetails.relativePath)
                } catch (Exception e) {
                    throw new GradleException(String.format("Could not add %s to ZIP '%s'.", fileDetails, zipFile), e)
                }
            } else {
                processArchive(fileDetails)
            }
        }

        private void processArchive(FileCopyDetails fileDetails) {
            stats.startJar()
            ZipFile archive = new ZipFile(fileDetails.file)
            List<ArchiveFileTreeElement> archiveElements = archive.entries.collect {
                new ArchiveFileTreeElement(new RelativeArchivePath(it, fileDetails))
            }
            Spec<FileTreeElement> patternSpec = patternSet.getAsSpec()
            List<ArchiveFileTreeElement> filteredArchiveElements = archiveElements.findAll { ArchiveFileTreeElement archiveElement ->
                patternSpec.isSatisfiedBy(archiveElement)
            }
            filteredArchiveElements.each { ArchiveFileTreeElement archiveElement ->
                if (archiveElement.relativePath.file) {
                    visitArchiveFile(archiveElement, archive)
                }
            }
            archive.close()
            stats.finishJar()
        }

        private void visitArchiveDirectory(RelativeArchivePath archiveDir) {
            if (recordVisit(archiveDir)) {
                zipOutStr.putNextEntry(archiveDir.entry)
                zipOutStr.closeEntry()
            }
        }

        private void visitArchiveFile(ArchiveFileTreeElement archiveFile, ZipFile archive) {
            def archiveFilePath = archiveFile.relativePath
            if (archiveFile.classFile || !isTransformable(archiveFile)) {
                if (recordVisit(archiveFilePath)) {
                    if (!remapper.hasRelocators() || !archiveFile.classFile) {
                        copyArchiveEntry(archiveFilePath, archive)
                    } else {
                        remapClass(archiveFilePath, archive)
                    }
                }
            } else {
                transform(archiveFile, archive)
            }
        }

        private void addParentDirectories(RelativeArchivePath file) {
            if (file) {
                addParentDirectories(file.parent)
                if (!file.file) {
                    visitArchiveDirectory(file)
                }
            }
        }

        private void remapClass(RelativeArchivePath file, ZipFile archive) {
            if (file.classFile) {
                addParentDirectories(new RelativeArchivePath(new ZipEntry(remapper.mapPath(file) + '.class'), null))
                InputStream is = archive.getInputStream(file.entry)
                try {
                    remapClass(is, file.pathString, file.entry.time)
                } finally {
                    is.close()
                }
            }
        }

        private void remapClass(FileCopyDetails fileCopyDetails) {
            if (FilenameUtils.getExtension(fileCopyDetails.name) == 'class') {
                remapClass(fileCopyDetails.file.newInputStream(), fileCopyDetails.path, fileCopyDetails.lastModified)
            }
        }

        private void remapClass(InputStream classInputStream, String path, long lastModified) {
            InputStream is = classInputStream
            ClassReader cr = new ClassReader(is)

            // We don't pass the ClassReader here. This forces the ClassWriter to rebuild the constant pool.
            // Copying the original constant pool should be avoided because it would keep references
            // to the original class names. This is not a problem at runtime (because these entries in the
            // constant pool are never used), but confuses some tools such as Felix' maven-bundle-plugin
            // that use the constant pool to determine the dependencies of a class.
            ClassWriter cw = new ClassWriter(0)

            ClassVisitor cv = new ClassRemapper(cw, remapper)

            try {
                cr.accept(cv, ClassReader.EXPAND_FRAMES)
            } catch (Throwable ise) {
                throw new GradleException("Error in ASM processing class " + path, ise)
            }

            byte[] renamedClass = cw.toByteArray()

            // Need to take the .class off for remapping evaluation
            String mappedName = remapper.mapPath(path)

            InputStream bis = new ByteArrayInputStream(renamedClass)
            try {
                // Now we put it back on so the class file is written out with the right extension.
                ZipEntry archiveEntry = new ZipEntry(mappedName + ".class")
                archiveEntry.setTime(lastModified)
                zipOutStr.putNextEntry(archiveEntry)
                IOUtils.copyLarge(bis, zipOutStr)
                zipOutStr.closeEntry()
            } catch (ZipException e) {
                log.warn("We have a duplicate " + mappedName + " in source project")
            } finally {
                bis.close()
            }
        }

        private void copyArchiveEntry(RelativeArchivePath archiveFile, ZipFile archive) {
            String mappedPath = remapper.map(archiveFile.entry.name)
            ZipEntry entry = new ZipEntry(mappedPath)
            entry.setTime(archiveFile.entry.time)
            RelativeArchivePath mappedFile = new RelativeArchivePath(entry, archiveFile.details)
            addParentDirectories(mappedFile)
            zipOutStr.putNextEntry(mappedFile.entry)
            InputStream is = archive.getInputStream(archiveFile.entry)
            try {
                IOUtils.copyLarge(is, zipOutStr)
            } finally {
                is.close()
            }
            zipOutStr.closeEntry()
        }

        private void visitDir(FileCopyDetails dirDetails) {
            try {
                // Trailing slash in name indicates that entry is a directory
                String path = dirDetails.relativePath.pathString + '/'
                ZipEntry archiveEntry = new ZipEntry(path)
                archiveEntry.setTime(dirDetails.lastModified)
                archiveEntry.unixMode = (UnixStat.DIR_FLAG | dirDetails.mode)
                zipOutStr.putNextEntry(archiveEntry)
                zipOutStr.closeEntry()
                recordVisit(dirDetails.relativePath)
            } catch (Exception e) {
                throw new GradleException(String.format("Could not add %s to ZIP '%s'.", dirDetails, zipFile), e)
            }
        }

        private void transform(ArchiveFileTreeElement element, ZipFile archive) {
            InputStream is = archive.getInputStream(element.relativePath.entry)
            try {
                transform(element, is)
            } finally {
                is.close()
            }
        }

        private void transform(FileCopyDetails details) {
            transform(details, details.file.newInputStream())
        }

        private void transform(FileTreeElement element, InputStream is) {
            String mappedPath = remapper.map(element.relativePath.pathString)
            transformers.find { it.canTransformResource(element) }.transform(
                    TransformerContext.builder()
                            .path(mappedPath)
                            .is(is)
                            .relocators(relocators)
                            .stats(stats)
                            .build()
            )
        }

        private boolean isTransformable(FileTreeElement element) {
            return transformers.any { it.canTransformResource(element) }
        }

    }

    class RelativeArchivePath extends RelativePath {

        ZipEntry entry
        FileCopyDetails details

        RelativeArchivePath(ZipEntry entry, FileCopyDetails fileDetails) {
            super(!entry.directory, entry.name.split('/'))
            this.entry = entry
            this.details = fileDetails
        }

        boolean isClassFile() {
            return lastName.endsWith('.class')
        }

        RelativeArchivePath getParent() {
            if (!segments || segments.length == 1) {
                return null
            } else {
                //Parent is always a directory so add / to the end of the path
                String path = segments[0..-2].join('/') + '/'
                return new RelativeArchivePath(new ZipEntry(path), null)
            }
        }
    }

    class ArchiveFileTreeElement implements FileTreeElement {

        private final RelativeArchivePath archivePath

        ArchiveFileTreeElement(RelativeArchivePath archivePath) {
            this.archivePath = archivePath
        }

        boolean isClassFile() {
            return archivePath.classFile
        }

        @Override
        File getFile() {
            return null
        }

        @Override
        boolean isDirectory() {
            return archivePath.entry.directory
        }

        @Override
        long getLastModified() {
            return archivePath.entry.lastModifiedDate.time
        }

        @Override
        long getSize() {
            return archivePath.entry.size
        }

        @Override
        InputStream open() {
            return null
        }

        @Override
        void copyTo(OutputStream outputStream) {

        }

        @Override
        boolean copyTo(File file) {
            return false
        }

        @Override
        String getName() {
            return archivePath.pathString
        }

        @Override
        String getPath() {
            return archivePath.lastName
        }

        @Override
        RelativeArchivePath getRelativePath() {
            return archivePath
        }

        @Override
        int getMode() {
            return archivePath.entry.unixMode
        }
    }
}
