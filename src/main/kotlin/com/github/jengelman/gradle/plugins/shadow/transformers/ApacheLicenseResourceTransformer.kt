package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.noOpDelegate
import org.gradle.api.file.FileTreeElement

/**
 * Prevents duplicate copies of the license.
 *
 * Modified from [org.apache.maven.plugins.shade.resource.ApacheLicenseResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/ApacheLicenseResourceTransformer.java).
 *
 * @author John Engelman
 */
@CacheableTransformer
public open class ApacheLicenseResourceTransformer : Transformer by noOpDelegate() {
  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.relativePath.pathString
    return LICENSE_PATH.equals(path, ignoreCase = true) ||
      LICENSE_TXT_PATH.regionMatches(0, path, 0, LICENSE_TXT_PATH.length, ignoreCase = true) ||
      LICENSE_MD_PATH.regionMatches(0, path, 0, LICENSE_MD_PATH.length, ignoreCase = true)
  }

  private companion object {
    private const val LICENSE_PATH = "META-INF/LICENSE"
    private const val LICENSE_TXT_PATH = "META-INF/LICENSE.txt"
    private const val LICENSE_MD_PATH = "META-INF/LICENSE.md"
  }
}
