package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.internal.inputStream
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy
import java.nio.charset.Charset
import java.util.Properties
import java.util.jar.JarFile.MANIFEST_NAME
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class PropertiesFileTransformerTest : BaseTransformerTest<PropertiesFileTransformer>() {
  @Test
  fun hasTransformedResource() =
    with(transformer) {
      transform(manifestTransformerContext)

      assertThat(hasTransformedResource()).isTrue()
    }

  @Test
  fun hasNotTransformedResource() {
    assertThat(transformer.hasTransformedResource()).isFalse()
  }

  @Test
  fun transformation() =
    with(transformer) {
      transform(manifestTransformerContext)

      val targetLines = transformToJar().use { it.getContent(MANIFEST_NAME).lines() }

      assertThat(targetLines).isNotEmpty()
      assertThat(targetLines).contains("Manifest-Version=1.0")
    }

  @Test
  fun transformationPropertiesAreReproducible() =
    with(transformer) {
      transform(manifestTransformerContext)

      val firstRunTargetLines = transformToJar().use { it.getContent(MANIFEST_NAME).lines() }
      Thread.sleep(1000) // wait for 1sec to ensure timestamps in properties would change
      val secondRunTargetLines = transformToJar().use { it.getContent(MANIFEST_NAME).lines() }

      assertThat(firstRunTargetLines).isEqualTo(secondRunTargetLines)
    }

  @ParameterizedTest
  @MethodSource("pathProvider")
  fun canTransformResourceWithPaths(path: String, expected: Boolean) {
    assertThat(transformer.canTransformResource(path)).isEqualTo(expected)
  }

  @ParameterizedTest
  @MethodSource("transformConfigProvider")
  fun exerciseAllTransformConfigurations(
    path: String,
    mergeStrategy: String,
    mergeSeparator: String,
    input1: Map<String, String>,
    input2: Map<String, String>,
    expectedOutput: Map<String, String>,
    expectedConflicts: Map<String, Map<String, Int>>,
  ) =
    with(transformer) {
      this.mergeStrategy.set(MergeStrategy.from(mergeStrategy))
      this.mergeSeparator.set(mergeSeparator)

      if (canTransformResource(path)) {
        transform(context(path, input1))
        transform(context(path, input2))
      }

      assertThat(propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
      assertThat(conflicts).isEqualTo(expectedConflicts)
    }

  @ParameterizedTest
  @MethodSource("transformConfigWithPathsProvider")
  fun exerciseAllTransformConfigurationsWithPaths(
    path: String,
    paths: List<String>,
    input1: Map<String, String>,
    input2: Map<String, String>,
    expectedOutput: Map<String, String>,
  ) =
    with(transformer) {
      this.paths.set(paths)
      mergeStrategy.set(MergeStrategy.First)

      if (canTransformResource(path)) {
        transform(context(path, input1))
        transform(context(path, input2))
      }

      assertThat(propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
    }

  @ParameterizedTest
  @MethodSource("transformConfigWithMappingsProvider")
  fun exerciseAllTransformConfigurationsWithMappings(
    path: String,
    mappings: Map<String, Map<String, String>>,
    input1: Map<String, String>,
    input2: Map<String, String>,
    expectedOutput: Map<String, String>,
  ) =
    with(transformer) {
      this.mappings.set(mappings)
      mergeStrategy.set(MergeStrategy.Latest)

      if (canTransformResource(path)) {
        transform(context(path, input1))
        transform(context(path, input2))
      }

      assertThat(propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
    }

  @ParameterizedTest
  @MethodSource("keyTransformerProvider")
  fun appliesKeyTransformer(
    path: String,
    keyTransformer: (String) -> String,
    input1: Map<String, String>,
    input2: Map<String, String>,
    expectedOutput: Map<String, String>,
  ) =
    with(transformer) {
      mergeStrategy.set(MergeStrategy.Append)
      this.keyTransformer = keyTransformer

      if (canTransformResource(path)) {
        transform(context(path, input1))
        transform(context(path, input2))
      }

      assertThat(propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
    }

  @ParameterizedTest
  @MethodSource("charsetProvider")
  fun appliesCharset(
    path: String,
    charset: String,
    input: Map<String, String>,
    expectedOutput: Map<String, String>,
  ) =
    with(transformer) {
      charsetName.set(charset)

      if (canTransformResource(path)) {
        transform(context(path, input, Charset.forName(charset)))
      }

      assertThat(propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
    }

  private companion object {
    fun context(
      path: String,
      input: Map<String, String>,
      charset: Charset = Charsets.ISO_8859_1,
    ): TransformerContext {
      val properties = Properties().apply { putAll(input) }
      return TransformerContext(path, properties.inputStream(charset))
    }

    @JvmStatic
    fun pathProvider() =
      listOf(
        Arguments.of("foo.properties", true),
        Arguments.of("foo/bar.properties", true),
        Arguments.of("foo.props", false),
      )

    @JvmStatic
    fun charsetProvider() =
      listOf(
        Arguments.of("utf8.properties", "utf-8", mapOf("foo" to "传傳磨宿说説"), mapOf("foo" to "传傳磨宿说説"))
      )

    @JvmStatic
    fun transformConfigWithPathsProvider() =
      listOf(
        Arguments.of(
          "f.properties",
          listOf("f.properties"),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
        ),
        Arguments.of(
          "foo.properties",
          listOf(".*.properties"),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
        ),
        Arguments.of(
          "foo.properties",
          listOf(".*bar"),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          emptyMap<String, String>(),
        ),
        Arguments.of(
          "foo.properties",
          emptyList<String>(),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
        ),
      )

    @JvmStatic
    fun transformConfigWithMappingsProvider() =
      listOf(
        Arguments.of(
          "f.properties",
          mapOf("f.properties" to mapOf("mergeStrategy" to "first")),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
        ),
        Arguments.of(
          "f.properties",
          mapOf("f.properties" to mapOf("mergeStrategy" to "latest")),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "bar"),
        ),
        Arguments.of(
          "f.properties",
          mapOf("f.properties" to mapOf("mergeStrategy" to "append")),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo,bar"),
        ),
        Arguments.of(
          "f.properties",
          mapOf("f.properties" to mapOf("mergeStrategy" to "append", "mergeSeparator" to ";")),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo;bar"),
        ),
        Arguments.of(
          "foo.properties",
          mapOf(".*.properties" to mapOf("mergeStrategy" to "first")),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
        ),
        Arguments.of(
          "foo.properties",
          mapOf(".*bar" to mapOf("mergeStrategy" to "first")),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          emptyMap<String, String>(),
        ),
      )

    @JvmStatic
    fun transformConfigProvider() =
      listOf(
        Arguments.of(
          "f.properties",
          "first",
          "",
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
          mapOf<String, Map<String, Int>>(),
        ),
        Arguments.of(
          "f.properties",
          "latest",
          "",
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "bar"),
          mapOf<String, Map<String, Int>>(),
        ),
        Arguments.of(
          "f.properties",
          "append",
          ",",
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo,bar"),
          mapOf<String, Map<String, Int>>(),
        ),
        Arguments.of(
          "f.properties",
          "append",
          ";",
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo;bar"),
          mapOf<String, Map<String, Int>>(),
        ),
        Arguments.of(
          "f.properties",
          "fail",
          ";",
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
          mapOf("f.properties" to mapOf("foo" to 2)),
        ),
      )

    @JvmStatic
    fun keyTransformerProvider() =
      listOf(
        Arguments.of(
          "foo.properties",
          { key: String -> key },
          mapOf("foo" to "bar"),
          mapOf("FOO" to "baz"),
          mapOf("foo" to "bar", "FOO" to "baz"),
        ),
        Arguments.of(
          "foo.properties",
          { key: String -> key.uppercase() },
          mapOf("foo" to "bar"),
          mapOf("FOO" to "baz"),
          mapOf("FOO" to "bar,baz"),
        ),
        Arguments.of(
          "foo.properties",
          { key: String -> "bar.${key.lowercase()}" },
          mapOf("foo" to "bar"),
          mapOf("FOO" to "baz"),
          mapOf("bar.foo" to "bar,baz"),
        ),
        Arguments.of(
          "foo.properties",
          { key: String -> key.replaceFirst(Regex("^(foo)"), "bar.$1") },
          mapOf("foo" to "bar"),
          mapOf("FOO" to "baz"),
          mapOf("bar.foo" to "bar", "FOO" to "baz"),
        ),
      )
  }
}
