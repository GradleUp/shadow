package com.github.jengelman.gradle.plugins.shadow.unit.transformers

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import java.util.Locale
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Modified from [org.apache.maven.plugins.shade.resource.AppendingTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/AppendingTransformerTest.java).
 */
class AppendingTransformerTest : TransformerTestSupport<AppendingTransformer>() {

  init {
    /*
     * NOTE: The Turkish locale has an usual case transformation for the letters "I" and "i", making it a prime
     * choice to test for improper case-less string comparisions.
     */
    Locale.setDefault(Locale("tr"))
  }

  @BeforeEach
  fun setUp() {
    transformer = AppendingTransformer(objectFactory)
  }

  @Test
  fun testCanTransformResource() {
    transformer.resource.set("abcdefghijklmnopqrstuvwxyz")

    assertThat(transformer.canTransformResource(getFileElement("abcdefghijklmnopqrstuvwxyz"))).isTrue()
    assertThat(transformer.canTransformResource(getFileElement("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))).isTrue()
    assertThat(transformer.canTransformResource(getFileElement("META-INF/MANIFEST.MF"))).isFalse()
  }
}
