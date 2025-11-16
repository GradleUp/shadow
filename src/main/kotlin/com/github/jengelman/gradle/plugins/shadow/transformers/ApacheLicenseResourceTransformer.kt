package com.github.jengelman.gradle.plugins.shadow.transformers

import org.gradle.api.tasks.util.PatternSet

/**
 * Prevents duplicate copies of the license.
 *
 * Modified from [org.apache.maven.plugins.shade.resource.ApacheLicenseResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/ApacheLicenseResourceTransformer.java).
 *
 * @author John Engelman
 */
@CacheableTransformer
public open class ApacheLicenseResourceTransformer @JvmOverloads constructor(
  patternSet: PatternSet = PatternSet()
    .apply { isCaseSensitive = false }
    .include(
      LICENSE_PATH,
      LICENSE_TXT_PATH,
      LICENSE_MD_PATH,
    ),
) : PatternFilterableResourceTransformer(patternSet = patternSet) {
  private companion object {
    private const val LICENSE_PATH = "META-INF/LICENSE"
    private const val LICENSE_TXT_PATH = "META-INF/LICENSE.txt"
    private const val LICENSE_MD_PATH = "META-INF/LICENSE.md"
  }
}
