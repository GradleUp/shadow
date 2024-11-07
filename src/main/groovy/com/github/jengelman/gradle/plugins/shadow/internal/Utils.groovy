package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.api.tasks.util.PatternSet

import java.util.jar.JarFile

class Utils {

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

    static void configureRelocation(ShadowJar target, String prefix) {
        def packages = [] as Set<String>
        target.configurations.each { configuration ->
            configuration.files.each { jar ->
                JarFile jf = new JarFile(jar)
                jf.entries().each { entry ->
                    if (entry.name.endsWith(".class") && entry.name != "module-info.class") {
                        packages << entry.name[0..entry.name.lastIndexOf('/') - 1].replaceAll('/', '.')
                    }
                }
                jf.close()
            }
        }
        packages.each {
            target.relocate(it, "${prefix}.${it}")
        }
    }
}
