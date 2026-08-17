package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.internal.inputStream
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getBytes
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import java.nio.charset.Charset
import java.util.Properties
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class PropertiesFileTransformerTest : BaseTransformerTest<PropertiesFileTransformer>() {
  @Test
  fun hasTransformedResource() =
    with(transformer) {
      assertThat(hasTransformedResource()).isFalse()

      transform(context("f.properties", mapOf("foo" to "foo")))

      assertThat(hasTransformedResource()).isTrue()
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

  @Test
  fun failStrategyReportsConflicts() =
    with(transformer) {
      val path = "f.properties"
      mergeStrategy.set(MergeStrategy.Fail)
      transform(context(path, mapOf("foo" to "foo")))
      transform(context(path, mapOf("foo" to "bar")))

      assertFailure { transformToJar() }
        .isInstanceOf<GradleException>()
        .hasMessage(
          """
          |The following properties files have conflicting property values and cannot be merged:
          | * f.properties
          |   * Property foo is duplicated 2 times with different values
          """
            .trimMargin()
        )
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
      val content = transformToJar().use { it.getBytes(path).toString(Charset.forName(charset)) }
      expectedOutput.forEach { (key, value) ->
        assertThat(content).contains("$key=$value")
      }
    }

  @Test // #856
  fun mergedPropertiesWithoutComments() =
    with(transformer) {
      val path = "META-INF/test.properties"
      paths.set(listOf(path))
      mergeStrategy.set(MergeStrategy.Append)

      val text1 = "# A comment from jar one.\nfoo=one"
      val text2 = "# A comment from jar two.\nfoo=two"

      transform(textContext(path, text1))
      transform(textContext(path, text2))

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }
      val content = JarPath(tempJar).use { it.getContent(path) }
      assertThat(content).isEqualTo("foo=one,two\n")
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
        Arguments.of("a/b/c/ButtonLabel_en.properties", true),
        Arguments.of("a/b/c/ButtonLabel_en_US.properties", true),
        Arguments.of("a/b/c/ButtonLabel_fr_CA_UNIX.properties", true),
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
