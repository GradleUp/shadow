package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.transformers.ProGuardFilesResourceTransformer.Companion.relocateRuleLine
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

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

  @ParameterizedTest
  @MethodSource("ruleProvider")
  fun relocateRuleLines(input: String, expected: String) {
    val relocators = listOf(SimpleRelocator("com.foo", "shaded.com.foo"))
    assertThat(relocators.relocateRuleLine(input)).isEqualTo(expected)
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

  private companion object {
    @JvmStatic
    fun ruleProvider() =
      listOf(
        // Directives
        Arguments.of("-keep class com.foo.Bar", "-keep class shaded.com.foo.Bar"),
        Arguments.of(
          "-keepclassmembers class com.foo.Bar { *; }",
          "-keepclassmembers class shaded.com.foo.Bar { *; }",
        ),
        Arguments.of(
          "-keepclasseswithmembers class * { @com.foo.Inject <init>(com.foo.Dep); }",
          "-keepclasseswithmembers class * { @shaded.com.foo.Inject <init>(shaded.com.foo.Dep); }",
        ),
        Arguments.of(
          "-assumenosideeffects class com.foo.Logger { public static *** d(...); }",
          "-assumenosideeffects class shaded.com.foo.Logger { public static *** d(...); }",
        ),
        Arguments.of("-dontwarn com.foo.**", "-dontwarn shaded.com.foo.**"),
        Arguments.of("-dontnote com.foo.**", "-dontnote shaded.com.foo.**"),
        Arguments.of("-repackageclasses com.foo.pkg", "-repackageclasses shaded.com.foo.pkg"),
        Arguments.of("-adaptclassstrings com.foo.**", "-adaptclassstrings shaded.com.foo.**"),
        // Wildcards & Inner classes
        Arguments.of($$"-keep class com.foo.Bar$Inner", $$"-keep class shaded.com.foo.Bar$Inner"),
        Arguments.of(
          $$"-keep class com.foo.Bar$Inner$SubInner",
          $$"-keep class shaded.com.foo.Bar$Inner$SubInner",
        ),
        Arguments.of("-keep class com.foo.*", "-keep class shaded.com.foo.*"),
        Arguments.of("-keep class com.foo.**", "-keep class shaded.com.foo.**"),
        Arguments.of("-keep class com.foo.?Bar", "-keep class shaded.com.foo.?Bar"),
        Arguments.of("-keep class com.foo.Test?", "-keep class shaded.com.foo.Test?"),
        Arguments.of("-keep class com.foo.?*", "-keep class shaded.com.foo.?*"),
        // Multiple classes on one line
        Arguments.of(
          "-keep class com.foo.Bar extends com.foo.Base implements com.foo.I1, com.foo.I2",
          "-keep class shaded.com.foo.Bar extends shaded.com.foo.Base implements shaded.com.foo.I1, shaded.com.foo.I2",
        ),
        Arguments.of(
          "public com.foo.Response execute(com.foo.Request req, com.foo.Context ctx)",
          "public shaded.com.foo.Response execute(shaded.com.foo.Request req, shaded.com.foo.Context ctx)",
        ),
        Arguments.of(
          "@com.foo.MyAnnotation class com.foo.Bar",
          "@shaded.com.foo.MyAnnotation class shaded.com.foo.Bar",
        ),
        // Comments and non-matching lines
        Arguments.of("# -keep class com.foo.Bar", "# -keep class com.foo.Bar"),
        Arguments.of("  # indented comment com.foo.Bar", "  # indented comment com.foo.Bar"),
        Arguments.of("-keep class org.other.Bar", "-keep class org.other.Bar"),
        Arguments.of(
          "public static void main(String[] args)",
          "public static void main(String[] args)",
        ),
      )
  }
}
