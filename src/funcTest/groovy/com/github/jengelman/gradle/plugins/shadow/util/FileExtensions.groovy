package com.github.jengelman.gradle.plugins.shadow.util

/**
 * TODO: this is used as extensions for Groovy, could be replaced after migrated to Kotlin.
 *  Registered in resources/META-INF/services/org.codehaus.groovy.runtime.ExtensionModule.
 */
final class FileExtensions {
    static final File resolve(File file, String relativePath) {
        try {
            return new File(file, relativePath)
        } catch (RuntimeException e) {
            throw new RuntimeException(String.format("Could not locate file '%s' relative to '%s'.", Arrays.toString(relativePath), file), e)
        }
    }

    static final File createDir(File file) {
        if (file.mkdirs()) {
            return file
        }
        if (file.isDirectory()) {
            return file
        }
        throw new AssertionError("Problems creating dir: " + file
            + ". Diagnostics: exists=" + file.exists() + ", isFile=" + file.isFile() + ", isDirectory=" + file.isDirectory())
    }
}
