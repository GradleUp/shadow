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

      transform(
        textContext(
          "META-INF/proguard/rules.pro",
          $$"""
          |# File 1 from dependency A
          |-keep class com.foo.Bar
          |-keep class com.foo.Bar$Inner
          |-keep class com.foo.?Bar extends com.foo.Base
          |"""
            .trimMargin(),
          relocator,
        )
      )
      transform(
        textContext(
          "META-INF/proguard/client.pro",
          """
          |# File 2 from dependency A (different filename)
          |-dontwarn com.foo.**
          |-keep class com.foo.Test? { @com.foo.MyAnnotation <fields>; }
          """
            .trimMargin(),
          relocator,
        )
      )
      transform(
        textContext(
          "META-INF/proguard/rules.pro",
          """
          |# File 1 from dependency B (same filename as File 1)
          |-keep class com.foo.Baz
          |-dontwarn com.foo.internal.*
          """
            .trimMargin(),
          relocator,
        )
      )

      assertThat(hasTransformedResource()).isTrue()

      tempJar.zipOutputStream().use { zos -> modifyOutputStream(zos, false) }

      JarPath(tempJar).use { jarPath ->
        assertThat(jarPath.getContent("META-INF/proguard/rules.pro"))
          .isEqualTo(
            $$"""
            |# File 1 from dependency A
            |-keep class shaded.com.foo.Bar
            |-keep class shaded.com.foo.Bar$Inner
            |-keep class shaded.com.foo.?Bar extends shaded.com.foo.Base
            |# File 1 from dependency B (same filename as File 1)
            |-keep class shaded.com.foo.Baz
            |-dontwarn shaded.com.foo.internal.*
            """
              .trimMargin()
          )
        assertThat(jarPath.getContent("META-INF/proguard/client.pro"))
          .isEqualTo(
            """
            |# File 2 from dependency A (different filename)
            |-dontwarn shaded.com.foo.**
            |-keep class shaded.com.foo.Test? { @shaded.com.foo.MyAnnotation <fields>; }
            """
              .trimMargin()
          )
      }
    }
}
