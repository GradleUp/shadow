package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.noOpDelegate

/**
 * Prevents duplicate copies of the license.
 *
 * Modified from [org.apache.maven.plugins.shade.resource.ApacheLicenseResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/ApacheLicenseResourceTransformer.java).
 *
 * @author John Engelman
 */
@CacheableTransformer
public open class ApacheLicenseResourceTransformer : ResourceTransformer by noOpDelegate() {
  override fun canTransformResource(relativePath: String): Boolean {
    return LICENSE_PATH.equals(relativePath, ignoreCase = true) ||
      LICENSE_TXT_PATH.regionMatches(0, relativePath, 0, LICENSE_TXT_PATH.length, ignoreCase = true) ||
      LICENSE_MD_PATH.regionMatches(0, relativePath, 0, LICENSE_MD_PATH.length, ignoreCase = true)
  }

  private companion object {
    private const val LICENSE_PATH = "META-INF/LICENSE"
    private const val LICENSE_TXT_PATH = "META-INF/LICENSE.txt"
    private const val LICENSE_MD_PATH = "META-INF/LICENSE.md"
  }
}
