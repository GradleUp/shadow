package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import org.junit.jupiter.api.Test

class ProGuardFilesResourceTransformerTest :
  BaseTransformerTest<ProGuardFilesResourceTransformer>() {
  @Test
  fun matchesDefaultPath() {
    assertThat(transformer.canTransformResource("META-INF/proguard/rules.pro")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/proguard/sub/rules.pro")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/MANIFEST.MF")).isFalse()
    assertThat(transformer.canTransformResource("META-INF/services/com.foo.Bar")).isFalse()
  }

  @Test
  fun matchesCustomPath() =
    with(ProGuardFilesResourceTransformer()) {
      includes.clear()
      include("META-INF/r8/**")
      assertThat(canTransformResource("META-INF/r8/rules.pro")).isTrue()
      assertThat(canTransformResource("META-INF/proguard/rules.pro")).isFalse()
    }

  @Test
  fun mergesSameFileNameAndKeepsDistinctFileNames() =
    with(transformer) {
      val relocator = SimpleRelocator("com.foo", "shaded.com.foo")

      // File 1 from dependency A
      transform(
        textContext(
          "META-INF/proguard/rules.pro",
          "-keep class com.foo.Bar\n-keep class com.foo.Bar\$Inner\n",
          relocator,
        )
      )

      // File 2 from dependency A (different filename)
      transform(textContext("META-INF/proguard/client.pro", "-dontwarn com.foo.**\n", relocator))

      // File 1 from dependency B (same filename as File 1)
      transform(textContext("META-INF/proguard/rules.pro", "-keep class com.foo.Baz\n", relocator))

      assertThat(hasTransformedResource()).isTrue()

      tempJar.zipOutputStream().use { zos -> modifyOutputStream(zos, false) }

      JarPath(tempJar).use { jarPath ->
        assertThat(jarPath.getContent("META-INF/proguard/rules.pro"))
          .isEqualTo(
            """
            |-keep class shaded.com.foo.Bar
            |-keep class shaded.com.foo.Bar${'$'}Inner
            |-keep class shaded.com.foo.Baz
            """
              .trimMargin()
          )
        assertThat(jarPath.getContent("META-INF/proguard/client.pro"))
          .isEqualTo("-dontwarn shaded.com.foo.**")
      }
    }
}
