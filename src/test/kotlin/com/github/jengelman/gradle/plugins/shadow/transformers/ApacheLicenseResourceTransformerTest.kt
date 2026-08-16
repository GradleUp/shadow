package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import de.infix.testBalloon.framework.core.testSuite

val ApacheLicenseResourceTransformerTests by testSuite {
  runTests(::ApacheLicenseResourceTransformerTest)
}

/**
 * Modified from
 * [org.apache.maven.plugins.shade.resource.ApacheLicenseResourceTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ApacheLicenseResourceTransformerTest.java).
 */
private class ApacheLicenseResourceTransformerTest :
  BaseTransformerTest<ApacheLicenseResourceTransformer>() {

  init {
    setupTurkishLocale()
  }

  fun canTransformResource() =
    with(transformer) {
      assertThat(canTransformResource("META-INF/LICENSE")).isTrue()
      assertThat(canTransformResource("META-INF/LICENSE.TXT")).isTrue()
      assertThat(canTransformResource("META-INF/License.txt")).isTrue()
      assertThat(canTransformResource("META-INF/LICENSE.md")).isTrue()
      assertThat(canTransformResource("META-INF/License.md")).isTrue()
      assertThat(canTransformResource("META-INF/MANIFEST.MF")).isFalse()
    }

  fun canTransformByPattern() =
    with(transformer) {
      exclude("META-INF/LICENSE.txt")
      include("META-INF/LICENSE.*")
      assertThat(canTransformResource("META-INF/LICENSE.txt")).isFalse()
      assertThat(canTransformResource("META-INF/LICENSE.log")).isTrue()
    }
}
