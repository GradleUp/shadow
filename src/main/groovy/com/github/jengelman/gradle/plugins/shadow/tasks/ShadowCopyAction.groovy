package com.github.jengelman.gradle.plugins.shadow.tasks

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

    public ShadowCopyAction(File zipFile, ZipCompressor compressor, DocumentationRegistry documentationRegistry) {
        this.zipFile = zipFile
        this.compressor = compressor
        this.documentationRegistry = documentationRegistry
    }

    @Override
    WorkResult execute(CopyActionProcessingStream stream) {
        final def zipOutStr

        try {
            zipOutStr = compressor.createArchiveOutputStream(zipFile)
        } catch (Exception e) {
            throw new GradleException("Could not create ZIP '${zipFile.toString()}'", e)
        }

        try {
            IoActions.withResource(zipOutStr, new Action<ZipOutputStream>() {
                public void execute(ZipOutputStream outputStream) {
                    stream.process(new StreamAction(outputStream))
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

        public StreamAction(ZipOutputStream zipOutStr) {
            this.zipOutStr = zipOutStr
        }

        public void processFile(FileCopyDetailsInternal details) {
            if (details.directory) {
                visitDir(details)
            } else {
                visitFile(details)
            }
        }

        private void visitFile(FileCopyDetails fileDetails) {
            if (!fileDetails.relativePath.pathString.endsWith('.jar')) {
                try {
                    ZipEntry archiveEntry = new ZipEntry(fileDetails.relativePath.pathString)
                    archiveEntry.setTime(fileDetails.lastModified)
                    archiveEntry.unixMode = (UnixStat.FILE_FLAG | fileDetails.mode)
                    zipOutStr.putNextEntry(archiveEntry)
                    fileDetails.copyTo(zipOutStr)
                    zipOutStr.closeEntry()
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
            zipOutStr.putNextEntry(jarDir.entry)
            zipOutStr.closeEntry()
        }

        private void visitJarFile(RelativeJarPath jarFile, JarFile jar) {
            zipOutStr.putNextEntry(jarFile.entry)
            IOUtils.copyLarge(jar.getInputStream(jarFile.entry), zipOutStr)
            zipOutStr.closeEntry()
        }

        private void visitDir(FileCopyDetails dirDetails) {
            try {
                // Trailing slash in name indicates that entry is a directory
                ZipEntry archiveEntry = new ZipEntry(dirDetails.relativePath.pathString + '/')
                archiveEntry.setTime(dirDetails.lastModified)
                archiveEntry.unixMode = (UnixStat.DIR_FLAG | dirDetails.mode)
                zipOutStr.putNextEntry(archiveEntry)
                zipOutStr.closeEntry()
            } catch (Exception e) {
                throw new GradleException(String.format("Could not add %s to ZIP '%s'.", dirDetails, zipFile), e)
            }
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

        ZipEntry getEntry() {
            return entry
        }
    }
}
