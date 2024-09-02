package com.github.jengelman.gradle.plugins.shadow.internal

import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.api.tasks.util.PatternSet

class GradleVersionUtil {

    static PatternSet getRootPatternSet(CopySpecInternal mainSpec) {
        return mainSpec.buildRootResolver().getPatternSet()
    }

    static ZipCompressor getInternalCompressor(ZipEntryCompression entryCompression, Jar jar) {
        switch (entryCompression) {
            case ZipEntryCompression.DEFLATED:
                return new DefaultZipCompressor(jar.zip64, ZipOutputStream.DEFLATED)
            case ZipEntryCompression.STORED:
                return new DefaultZipCompressor(jar.zip64, ZipOutputStream.STORED)
            default:
                throw new IllegalArgumentException(String.format("Unknown Compression type %s", entryCompression))
        }
    }
}
