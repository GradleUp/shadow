package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.impl.RelocatorRemapper
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.Zip64RequiredException
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipFile
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.UncheckedIOException
import org.gradle.api.file.DuplicateFileCopyingException
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.internal.tasks.SimpleWorkResult
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.IoActions
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.RemappingClassAdapter

import java.util.zip.ZipException

@Slf4j
public class ShadowCopyAction implements CopyAction {

    private final File zipFile
    private final ZipCompressor compressor
    private final DocumentationRegistry documentationRegistry
    private final List<Transformer> transformers
    private final List<Relocator> relocators

    public ShadowCopyAction(File zipFile, ZipCompressor compressor, DocumentationRegistry documentationRegistry,
                            List<Transformer> transformers, List<Relocator> relocators) {
        this.zipFile = zipFile
        this.compressor = compressor
        this.documentationRegistry = documentationRegistry
        this.transformers = transformers
        this.relocators = relocators
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
            IoActions.withResource(zipOutStr, new Action<ZipOutputStream>() {
                public void execute(ZipOutputStream outputStream) {
                    try {
                        stream.process(new StreamAction(outputStream, transformers, relocators))
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
        return new SimpleWorkResult(true)
    }

    private void processTransformers(ZipOutputStream stream) {
        transformers.each { Transformer transformer ->
            if (transformer.hasTransformedResource()) {
                transformer.modifyOutputStream(stream)
            }
        }
    }

    class StreamAction implements CopyActionProcessingStreamAction {

        private final ZipOutputStream zipOutStr
        private final List<Transformer> transformers
        private final List<Relocator> relocators
        private final RelocatorRemapper remapper

        private Set<RelativePath> visitedFiles = new HashSet<RelativePath>()

        public StreamAction(ZipOutputStream zipOutStr, List<Transformer> transformers, List<Relocator> relocators) {
            this.zipOutStr = zipOutStr
            this.transformers = transformers
            this.relocators = relocators
            this.remapper = new RelocatorRemapper(relocators)
        }

        public void processFile(FileCopyDetailsInternal details) {
            if (details.directory) {
                visitDir(details)
            } else {
                visitFile(details)
            }
        }

        private boolean isArchive(FileCopyDetails fileDetails) {
            return fileDetails.relativePath.pathString.endsWith('.jar') ||
                    fileDetails.relativePath.pathString.endsWith('.zip')
        }

        private void visitFile(FileCopyDetails fileDetails) {
            if (!isArchive(fileDetails)) {
                try {
                    String path = fileDetails.relativePath.pathString
                    ZipEntry archiveEntry = new ZipEntry(path)
                    archiveEntry.setTime(fileDetails.lastModified)
                    archiveEntry.unixMode = (UnixStat.FILE_FLAG | fileDetails.mode)
                    zipOutStr.putNextEntry(archiveEntry)
                    fileDetails.copyTo(zipOutStr)
                    zipOutStr.closeEntry()
                    visitedFiles.add(fileDetails.relativePath)
                } catch (Exception e) {
                    throw new GradleException(String.format("Could not add %s to ZIP '%s'.", fileDetails, zipFile), e)
                }
            } else {
                processArchive(fileDetails)
            }
        }

        private void processArchive(FileCopyDetails fileDetails) {
            ZipFile archive = new ZipFile(fileDetails.file)
            archive.entries.each { ZipEntry entry ->
                RelativeArchivePath relativePath = new RelativeArchivePath(entry, fileDetails)
                if (!relativePath.file) {
                    visitArchiveDirectory(relativePath)
                } else {
                    visitArchiveFile(relativePath, archive)
                }
            }
            archive.close()
        }

        private void visitArchiveDirectory(RelativeArchivePath archiveDir) {
            if (visitedFiles.add(archiveDir)) {
                zipOutStr.putNextEntry(archiveDir.entry)
                zipOutStr.closeEntry()
            }
        }

        private void visitArchiveFile(RelativeArchivePath archiveFile, ZipFile archive) {
            if (archiveFile.classFile || !isTransformable(archiveFile)) {
                if (!visitedFiles.add(archiveFile)) {
                    DuplicatesStrategy strategy = archiveFile.details.duplicatesStrategy
                    if (strategy == DuplicatesStrategy.EXCLUDE) {
                        return
                    } else if (strategy == DuplicatesStrategy.FAIL) {
                        throw new DuplicateFileCopyingException(String.format("Encountered duplicate path \"%s\" during copy operation configured with DuplicatesStrategy.FAIL", archiveFile))
                    } else if (strategy == DuplicatesStrategy.WARN) {
                        log.warn("Encountered duplicate path \"{}\" during copy operation configured with DuplicatesStrategy.WARN", archiveFile)
                    }
                }
                if (!remapper.hasRelocators()) {
                    copyArchiveEntry(archiveFile, archive)
                } else {
                    remapClass(archiveFile, archive)
                }
            } else {
                transform(archiveFile, archive)
            }
        }

        private void remapClass(RelativeArchivePath file, ZipFile archive) {
            InputStream is = archive.getInputStream(file.entry)
            ClassReader cr = new ClassReader(is)

            // We don't pass the ClassReader here. This forces the ClassWriter to rebuild the constant pool.
            // Copying the original constant pool should be avoided because it would keep references
            // to the original class names. This is not a problem at runtime (because these entries in the
            // constant pool are never used), but confuses some tools such as Felix' maven-bundle-plugin
            // that use the constant pool to determine the dependencies of a class.
            ClassWriter cw = new ClassWriter(0)

            ClassVisitor cv = new RemappingClassAdapter(cw, remapper)

            try {
                cr.accept(cv, ClassReader.EXPAND_FRAMES)
            } catch (Throwable ise) {
                throw new GradleException("Error in ASM processing class " + file.pathString, ise)
            }

            byte[] renamedClass = cw.toByteArray()

            // Need to take the .class off for remapping evaluation
            String mappedName = remapper.map(file.pathString.substring(0, file.pathString.indexOf('.')))

            try {
                // Now we put it back on so the class file is written out with the right extension.
                zipOutStr.putNextEntry(new ZipEntry(mappedName + ".class"))
                IOUtils.copyLarge(new ByteArrayInputStream(renamedClass), zipOutStr)
                zipOutStr.closeEntry()
            } catch (ZipException e) {
                log.warn("We have a duplicate " + mappedName + " in " + archive)
            }
        }

        private void copyArchiveEntry(RelativeArchivePath archiveFile, ZipFile archive) {
            zipOutStr.putNextEntry(archiveFile.entry)
            IOUtils.copyLarge(archive.getInputStream(archiveFile.entry), zipOutStr)
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
                visitedFiles.add(dirDetails.relativePath)
            } catch (Exception e) {
                throw new GradleException(String.format("Could not add %s to ZIP '%s'.", dirDetails, zipFile), e)
            }
        }

        private void transform(RelativeArchivePath file, ZipFile archive) {
            String mappedPath = remapper.map(file.pathString)
            InputStream is = archive.getInputStream(file.entry)
            transformers.find { it.canTransformResource(file.pathString) }.transform(mappedPath, is, relocators)
        }

        private boolean isTransformable(RelativeArchivePath file) {
            return transformers.find { it.canTransformResource(file.pathString) } as boolean
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
    }
}
