package com.github.jengelman.gradle.plugins.shadow.internal

import org.apache.tools.zip.ZipEntry
import org.gradle.api.GradleException

class ZipUtils {

    static ZipEntry zipEntry(String name) {
        if (name.split("[/\\\\]").any { it == ".." }) {
            throw new GradleException("Malicious ZIP entry containing path traversal sequence: $name")
        }
        return new ZipEntry(name)
    }
}
