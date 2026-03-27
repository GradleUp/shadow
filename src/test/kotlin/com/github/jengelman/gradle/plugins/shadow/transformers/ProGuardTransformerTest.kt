package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.outputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ProGuardTransformerTest : BaseTransformerTest<ProGuardTransformer>() {
  private lateinit var tempJar: Path

  @BeforeEach
  override fun beforeEach() {
    super.beforeEach()
    tempJar = createTempFile("shade.", ".jar")
  }

  @AfterEach
  fun afterEach() {
    tempJar.deleteExisting()
  }

  @ParameterizedTest
  @MethodSource("resourceProvider")
  fun canTransformResource(path: String, expected: Boolean) {
    assertThat(transformer.canTransformResource(path)).isEqualTo(expected)
  }

  @ParameterizedTest
  @MethodSource("proGuardFileProvider")
  fun transformProGuardFile(path: String, input1: String, input2: String, output: String) {
    if (transformer.canTransformResource(path)) {
      transformer.transform(textContext(path, input1))
      transformer.transform(textContext(path, input2))
    }

    assertThat(transformer.hasTransformedResource()).isTrue()
    val entry = transformer.proGuardEntries.getValue(path).joinToString("\n")
    assertThat(entry).isEqualTo(output)
  }

  @Test
  fun mergesMultipleFiles() {
    val path = "META-INF/proguard/app.pro"
    val content1 = "-keep class com.foo.Bar { *; }"
    val content2 = "-keep class com.foo.Baz { *; }"

    transformer.transform(textContext(path, content1))
    transformer.transform(textContext(path, content2))

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val transformedContent = JarPath(tempJar).use { it.getContent(path) }
    assertThat(transformedContent)
      .isEqualTo("-keep class com.foo.Bar { *; }\n-keep class com.foo.Baz { *; }")
  }

  @Test
  fun canTransformAlternatePath() {
    transformer.include("META-INF/custom/**")
    assertThat(transformer.canTransformResource("META-INF/proguard/rules.pro")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/custom/rules.pro")).isTrue()
  }

  @Test
  fun relocatedClasses() {
    val relocator = SimpleRelocator("org.foo", "borg.foo", excludes = listOf("org.foo.exclude.*"))
    val content = "-keep class org.foo.Service { *; }\n-keep class org.foo.exclude.OtherService"
    val path = "META-INF/proguard/app.pro"

    transformer.transform(textContext(path, content, relocator))

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val transformedContent = JarPath(tempJar).use { it.getContent(path) }
    assertThat(transformedContent)
      .isEqualTo("-keep class borg.foo.Service { *; }\n-keep class org.foo.exclude.OtherService")
  }

  @Test
  fun mergeRelocatedFiles() {
    val relocator = SimpleRelocator("org.foo", "borg.foo", excludes = listOf("org.foo.exclude.*"))
    val content1 = "-keep class org.foo.Service { *; }"
    val content2 = "-keep class org.foo.exclude.OtherService"
    val path = "META-INF/proguard/app.pro"

    transformer.transform(textContext(path, content1, relocator))
    transformer.transform(textContext(path, content2, relocator))

    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, false)
    }

    val transformedContent = JarPath(tempJar).use { it.getContent(path) }
    assertThat(transformedContent)
      .isEqualTo("-keep class borg.foo.Service { *; }\n-keep class org.foo.exclude.OtherService")
  }

  private companion object {
    @JvmStatic
    fun resourceProvider() =
      listOf(
        // path, expected
        Arguments.of("META-INF/proguard/app.pro", true),
        Arguments.of("META-INF/proguard/rules.pro", true),
        Arguments.of("META-INF/services/com.acme.Foo", false),
        Arguments.of("foo/bar.properties", false),
      )

    @JvmStatic
    fun proGuardFileProvider() =
      listOf(
        // path, input1, input2, output
        Arguments.of(
          "META-INF/proguard/app.pro",
          "-keep class com.foo.Bar",
          "-keep class com.foo.Baz",
          "-keep class com.foo.Bar\n-keep class com.foo.Baz",
        ),
        Arguments.of(
          "META-INF/proguard/rules.pro",
          "-keep class com.foo.**",
          "-dontwarn com.foo.**",
          "-keep class com.foo.**\n-dontwarn com.foo.**",
        ),
      )
  }
}
