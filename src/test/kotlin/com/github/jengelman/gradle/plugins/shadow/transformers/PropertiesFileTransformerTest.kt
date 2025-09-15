package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.internal.inputStream
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy
import java.nio.charset.Charset
import java.util.Properties
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class PropertiesFileTransformerTest : BaseTransformerTest<PropertiesFileTransformer>() {
  @Test
  fun hasTransformedResource() {
    transformer.transform(manifestTransformerContext)

    assertThat(transformer.hasTransformedResource()).isTrue()
  }

  @Test
  fun hasNotTransformedResource() {
    assertThat(transformer.hasTransformedResource()).isFalse()
  }

  @Test
  fun transformation() {
    transformer.transform(manifestTransformerContext)

    val targetLines = doTransformAndGetTransformedPath(transformer, false).readContentLines()

    assertThat(targetLines).isNotEmpty()
    assertThat(targetLines).contains("Manifest-Version=1.0")
  }

  @Test
  fun transformationPropertiesAreReproducible() {
    transformer.transform(manifestTransformerContext)

    val firstRunTargetLines = doTransformAndGetTransformedPath(transformer, true).readContentLines()
    Thread.sleep(1000) // wait for 1sec to ensure timestamps in properties would change
    val secondRunTargetLines = doTransformAndGetTransformedPath(transformer, true).readContentLines()

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
  ) {
    transformer.mergeStrategy.set(MergeStrategy.from(mergeStrategy))
    transformer.mergeSeparator.set(mergeSeparator)

    if (transformer.canTransformResource(path)) {
      transformer.transform(context(path, input1))
      transformer.transform(context(path, input2))
    }

    assertThat(transformer.propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
  }

  @ParameterizedTest
  @MethodSource("transformConfigWithPathsProvider")
  fun exerciseAllTransformConfigurationsWithPaths(
    path: String,
    paths: List<String>,
    input1: Map<String, String>,
    input2: Map<String, String>,
    expectedOutput: Map<String, String>,
  ) {
    transformer.paths.set(paths)
    transformer.mergeStrategy.set(MergeStrategy.First)

    if (transformer.canTransformResource(path)) {
      transformer.transform(context(path, input1))
      transformer.transform(context(path, input2))
    }

    assertThat(transformer.propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
  }

  @ParameterizedTest
  @MethodSource("transformConfigWithMappingsProvider")
  fun exerciseAllTransformConfigurationsWithMappings(
    path: String,
    mappings: Map<String, Map<String, String>>,
    input1: Map<String, String>,
    input2: Map<String, String>,
    expectedOutput: Map<String, String>,
  ) {
    transformer.mappings.set(mappings)
    transformer.mergeStrategy.set(MergeStrategy.Latest)

    if (transformer.canTransformResource(path)) {
      transformer.transform(context(path, input1))
      transformer.transform(context(path, input2))
    }

    assertThat(transformer.propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
  }

  @ParameterizedTest
  @MethodSource("keyTransformerProvider")
  fun appliesKeyTransformer(
    path: String,
    keyTransformer: (String) -> String,
    input1: Map<String, String>,
    input2: Map<String, String>,
    expectedOutput: Map<String, String>,
  ) {
    transformer.mergeStrategy.set(MergeStrategy.Append)
    transformer.keyTransformer = keyTransformer

    if (transformer.canTransformResource(path)) {
      transformer.transform(context(path, input1))
      transformer.transform(context(path, input2))
    }

    assertThat(transformer.propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
  }

  @ParameterizedTest
  @MethodSource("charsetProvider")
  fun appliesCharset(
    path: String,
    charset: String,
    input: Map<String, String>,
    expectedOutput: Map<String, String>,
  ) {
    transformer.charsetName.set(charset)

    if (transformer.canTransformResource(path)) {
      transformer.transform(context(path, input, Charset.forName(charset)))
    }

    assertThat(transformer.propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
  }

  private companion object {
    fun context(path: String, input: Map<String, String>, charset: Charset = Charsets.ISO_8859_1): TransformerContext {
      val properties = Properties().apply { putAll(input) }
      return TransformerContext(path, properties.inputStream(charset))
    }

    @JvmStatic
    fun pathProvider() = listOf(
      Arguments.of("foo.properties", true),
      Arguments.of("foo/bar.properties", true),
      Arguments.of("foo.props", false),
    )

    @JvmStatic
    fun charsetProvider() = listOf(
      Arguments.of(
        "utf8.properties",
        "utf-8",
        mapOf("foo" to "传傳磨宿说説"),
        mapOf("foo" to "传傳磨宿说説"),
      ),
    )

    @JvmStatic
    fun transformConfigWithPathsProvider() = listOf(
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
    fun transformConfigWithMappingsProvider() = listOf(
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
    fun transformConfigProvider() = listOf(
      Arguments.of(
        "f.properties",
        "first",
        "",
        mapOf("foo" to "foo"),
        mapOf("foo" to "bar"),
        mapOf("foo" to "foo"),
      ),
      Arguments.of(
        "f.properties",
        "latest",
        "",
        mapOf("foo" to "foo"),
        mapOf("foo" to "bar"),
        mapOf("foo" to "bar"),
      ),
      Arguments.of(
        "f.properties",
        "append",
        ",",
        mapOf("foo" to "foo"),
        mapOf("foo" to "bar"),
        mapOf("foo" to "foo,bar"),
      ),
      Arguments.of(
        "f.properties",
        "append",
        ";",
        mapOf("foo" to "foo"),
        mapOf("foo" to "bar"),
        mapOf("foo" to "foo;bar"),
      ),
    )

    @JvmStatic
    fun keyTransformerProvider() = listOf(
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
