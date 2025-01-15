package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

/**
 * Modified from [org.apache.maven.plugins.shade.resource.AppendingTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/AppendingTransformerTest.java).
 */
class AppendingTransformerTest : BaseTransformerTest<AppendingTransformer>() {

  init {
    setupTurkishLocale()
  }

  @Test
  fun canTransformResource() {
    transformer.resource.set("abcdefghijklmnopqrstuvwxyz")

    assertThat(transformer.canTransformResource("abcdefghijklmnopqrstuvwxyz")).isTrue()
    assertThat(transformer.canTransformResource("ABCDEFGHIJKLMNOPQRSTUVWXYZ")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/MANIFEST.MF")).isFalse()
  }
}
