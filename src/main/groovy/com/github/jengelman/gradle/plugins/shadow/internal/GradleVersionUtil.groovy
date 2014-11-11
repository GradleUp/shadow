package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.internal.gradle111.Gradle111DefaultZipCompressor
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.GradleVersion

class GradleVersionUtil {

    private final GradleVersion version

    GradleVersionUtil(String version) {
        this.version = GradleVersion.version(version)
    }

    PatternSet getRootPatternSet(CopySpecInternal mainSpec) {
        // Gradle 1.12 class exposes patternSet on the spec
        if (isGradle1x()) {
            return mainSpec.getPatternSet()
            // Gradle 2.x moves it to the spec resolver.
        } else {
            return mainSpec.buildRootResolver().getPatternSet()
        }
    }

    ZipCompressor getInternalCompressor(ZipEntryCompression entryCompression, Jar jar) {
        if (isGradle1_11()) {
            return getGradle1_11InternalCompressor(entryCompression, jar)
        } else {
            switch (entryCompression) {
                case ZipEntryCompression.DEFLATED:
                    return new DefaultZipCompressor(jar.zip64, ZipOutputStream.DEFLATED);
                case ZipEntryCompression.STORED:
                    return new DefaultZipCompressor(jar.zip64, ZipOutputStream.STORED);
                default:
                    throw new IllegalArgumentException(String.format("Unknown Compression type %s", entryCompression));
            }
        }
    }

    private ZipCompressor getGradle1_11InternalCompressor(ZipEntryCompression entryCompression, Jar jar) {
        switch(entryCompression) {
            case ZipEntryCompression.DEFLATED:
                return Gradle111DefaultZipCompressor.INSTANCE;
            case ZipEntryCompression.STORED:
                return Gradle111DefaultZipCompressor.INSTANCE;
            default:
                throw new IllegalArgumentException(String.format("Unknown Compression type %s", entryCompression));
        }
    }

    private boolean isGradle1x() {
        version < GradleVersion.version('2.0')
    }

    private boolean isGradle1_11() {
        version <= GradleVersion.version('1.11')
    }
}
