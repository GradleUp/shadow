package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import org.apache.commons.io.IOUtils
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.Zip64RequiredException
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.UncheckedIOException
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.internal.file.copy.ZipCompressor
import org.gradle.api.internal.tasks.SimpleWorkResult
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.IoActions

import java.util.jar.JarEntry
import java.util.jar.JarFile

public class ShadowCopyAction implements CopyAction {

    private final File zipFile
    private final ZipCompressor compressor
    private final DocumentationRegistry documentationRegistry
    private final List<Transformer> transformers

    public ShadowCopyAction(File zipFile, ZipCompressor compressor, DocumentationRegistry documentationRegistry, List<Transformer> transformers) {
        this.zipFile = zipFile
        this.compressor = compressor
        this.documentationRegistry = documentationRegistry
        this.transformers = transformers
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
                    stream.process(new StreamAction(outputStream, transformers))
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

    class StreamAction implements CopyActionProcessingStreamAction {

        private final ZipOutputStream zipOutStr
        private final List<Transformer> transformers

        private List<String> entries = []

        public StreamAction(ZipOutputStream zipOutStr, List<Transformer> transformers) {
            this.zipOutStr = zipOutStr
            this.transformers = transformers
        }

        public void processFile(FileCopyDetailsInternal details) {
            if (details.directory) {
                visitDir(details)
            } else {
                visitFile(details)
            }
            processTransformers()
        }

        private void processTransformers() {
            transformers.each { Transformer transformer ->
                if (transformer.hasTransformedResource()) {
                    transformer.modifyOutputStream(zipOutStr)
                }
            }
        }

        private void visitFile(FileCopyDetails fileDetails) {
            if (!fileDetails.relativePath.pathString.endsWith('.jar')) {
                try {
                    String path = fileDetails.relativePath.pathString
                    if (!entries.contains(path)) {
                        ZipEntry archiveEntry = new ZipEntry(path)
                        archiveEntry.setTime(fileDetails.lastModified)
                        archiveEntry.unixMode = (UnixStat.FILE_FLAG | fileDetails.mode)
                        zipOutStr.putNextEntry(archiveEntry)
                        fileDetails.copyTo(zipOutStr)
                        zipOutStr.closeEntry()
                        entries << path
                    }
                } catch (Exception e) {
                    throw new GradleException(String.format("Could not add %s to ZIP '%s'.", fileDetails, zipFile), e)
                }
            } else {
                processJarFile(fileDetails)
            }
        }

        private void processJarFile(FileCopyDetails fileDetails) {
            JarFile jar = new JarFile(fileDetails.file)
            jar.entries().each { JarEntry entry ->
                RelativeJarPath relativePath = new RelativeJarPath(new ZipEntry(entry))
                if (relativePath.isDirectory()) {
                    visitJarDirectory(relativePath)
                } else {
                    visitJarFile(relativePath, jar)
                }
            }
        }

        private void visitJarDirectory(RelativeJarPath jarDir) {
            if (!entries.contains(jarDir.entry.name)) {
                zipOutStr.putNextEntry(jarDir.entry)
                zipOutStr.closeEntry()
                entries << jarDir.entry.name
            }
        }

        private void visitJarFile(RelativeJarPath jarFile, JarFile jar) {
            if (jarFile.classFile || !isTransformable(jarFile)) {
                //TODO class relocation
                copyJarEntry(jarFile, jar)
            }
        }

        private void copyJarEntry(RelativeJarPath jarFile, JarFile jar) {
            if (!entries.contains(jarFile.entry.name)) {
                zipOutStr.putNextEntry(jarFile.entry)
                IOUtils.copyLarge(jar.getInputStream(jarFile.entry), zipOutStr)
                zipOutStr.closeEntry()
                entries << jarFile.entry.name
            }
        }

        private void visitDir(FileCopyDetails dirDetails) {
            try {
                // Trailing slash in name indicates that entry is a directory
                String path = dirDetails.relativePath.pathString + '/'
                if (!entries.contains(path)) {
                    ZipEntry archiveEntry = new ZipEntry(path)
                    archiveEntry.setTime(dirDetails.lastModified)
                    archiveEntry.unixMode = (UnixStat.DIR_FLAG | dirDetails.mode)
                    zipOutStr.putNextEntry(archiveEntry)
                    zipOutStr.closeEntry()
                    entries << path
                }
            } catch (Exception e) {
                throw new GradleException(String.format("Could not add %s to ZIP '%s'.", dirDetails, zipFile), e)
            }
        }

        private boolean isTransformable(RelativeJarPath file) {
            return transformers.find { it.canTransformResource(file.path) } as boolean
        }
    }

    class RelativeJarPath {

        ZipEntry entry

        RelativeJarPath(ZipEntry entry) {
            this.entry = entry
        }

        boolean isDirectory() {
            entry.isDirectory()
        }

        boolean isRoot() {
            entry.name == '/'
        }

        RelativeJarPath getParent() {
            if (root) {
                return null
            }
            String path = entry.name
            if (directory) {
                path = path[0..-2]
            }
            int index = path.lastIndexOf('/')
            if (index != -1) {
                return new RelativeJarPath(new JarEntry(path[0..index]))
            }
        }

        String getPath() {
            return entry.name
        }

        boolean isClassFile() {
            return path.endsWith('.class')
        }

        ZipEntry getEntry() {
            return entry
        }
    }
}
