package com.github.jengelman.gradle.plugins.shadow.transformers

import org.gradle.api.file.FileTreeElement

/**
 * Prevents duplicate copies of the license
 *
 * Modified from `org.apache.maven.plugins.shade.resouce.ApacheLicenseResourceTransformer.java`
 *
 * @author John Engelman
 */
open class ApacheLicenseResourceTransformer : Transformer by NoOpTransformer {
    override fun canTransformResource(element: FileTreeElement): Boolean {
        val path = element.relativePath.pathString
        return LICENSE_PATH.equals(path, ignoreCase = true) ||
            LICENSE_TXT_PATH.regionMatches(0, path, 0, LICENSE_TXT_PATH.length, ignoreCase = true)
    }

    private companion object {
        private const val LICENSE_PATH = "META-INF/LICENSE"
        private const val LICENSE_TXT_PATH = "META-INF/LICENSE.txt"
    }
}
