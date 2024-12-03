package com.github.jengelman.gradle.plugins.shadow.util

final class FileExtensions {
    static File resolve(File file, String relativePath) {
        try {
            return new File(file, relativePath)
        } catch (RuntimeException e) {
            throw new RuntimeException(String.format("Could not locate file '%s' relative to '%s'.", Arrays.toString(relativePath), file), e)
        }
    }

    static File createDir(File file) {
        if (file.mkdirs()) {
            return file
        }
        if (file.isDirectory()) {
            return file
        }
        throw new AssertionError("Problems creating dir: " + this
            + ". Diagnostics: exists=" + this.exists() + ", isFile=" + this.isFile() + ", isDirectory=" + this.isDirectory())
    }
}
