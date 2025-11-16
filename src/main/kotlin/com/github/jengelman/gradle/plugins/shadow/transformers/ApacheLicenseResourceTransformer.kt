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
      "META-INF/LICENSE",
      "META-INF/LICENSE.txt",
      "META-INF/LICENSE.md",
    ),
) : PatternFilterableResourceTransformer(patternSet = patternSet)
