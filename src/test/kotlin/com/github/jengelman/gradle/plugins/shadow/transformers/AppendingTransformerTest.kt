package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.invariantEolString
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import de.infix.testBalloon.framework.core.testSuite

val AppendingTransformerTests by testSuite {
  runTests(::AppendingTransformerTest)
}

/**
 * Modified from
 * [org.apache.maven.plugins.shade.resource.AppendingTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/AppendingTransformerTest.java).
 */
private class AppendingTransformerTest : BaseTransformerTest<AppendingTransformer>() {

  init {
    setupTurkishLocale()
  }

  fun canTransformResource() =
    with(transformer) {
      resource.set("abcdefghijklmnopqrstuvwxyz")

      assertThat(canTransformResource("abcdefghijklmnopqrstuvwxyz")).isTrue()
      assertThat(canTransformResource("ABCDEFGHIJKLMNOPQRSTUVWXYZ")).isTrue()
      assertThat(canTransformResource("META-INF/MANIFEST.MF")).isFalse()
    }

  fun appendResources() =
    with(transformer) {
      resource.set("test.properties")
      transform(textContext("test.properties", "foo=bar"))
      transform(textContext("test.properties", "baz=qux"))

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }
      val content = JarPath(tempJar).use { it.getContent("test.properties") }.invariantEolString
      assertThat(content).isEqualTo("foo=bar\nbaz=qux")
    }

  fun appendResourcesWithCustomSeparator() =
    with(transformer) {
      resource.set("application.yml")
      separator.set("\n---\n")
      transform(textContext("application.yml", "key1: val1"))
      transform(textContext("application.yml", "key2: val2"))

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }
      val content = JarPath(tempJar).use { it.getContent("application.yml") }.invariantEolString
      assertThat(content).isEqualTo("key1: val1\n---\nkey2: val2")
    }
}
