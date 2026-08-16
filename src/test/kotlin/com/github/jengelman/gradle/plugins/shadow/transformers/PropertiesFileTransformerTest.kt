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
import com.github.jengelman.gradle.plugins.shadow.testkit.invariantEolString
import com.github.jengelman.gradle.plugins.shadow.testkit.runTest
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import de.infix.testBalloon.framework.core.testSuite
import java.nio.charset.Charset
import java.util.Properties
import org.gradle.api.GradleException

val PropertiesFileTransformerTests by testSuite {
  runTests(::PropertiesFileTransformerTest)

  for ((path, expected) in PropertiesFileTransformerTest.pathProvider) {
    runTest(
      "canTransformResourceWithPaths_${path}_$expected",
      ::PropertiesFileTransformerTest,
    ) {
      canTransformResourceWithPaths(path, expected)
    }
  }

  for ((path, mergeStrategy, mergeSeparator, input1, input2, expectedOutput, expectedConflicts) in
    PropertiesFileTransformerTest.transformConfigProvider) {
    runTest(
      "exerciseAllTransformConfigurations_${path}_$mergeStrategy",
      ::PropertiesFileTransformerTest,
    ) {
      exerciseAllTransformConfigurations(
        path,
        mergeStrategy,
        mergeSeparator,
        input1,
        input2,
        expectedOutput,
        expectedConflicts,
      )
    }
  }

  for ((path, paths, input1, input2, expectedOutput) in
    PropertiesFileTransformerTest.transformConfigWithPathsProvider) {
    runTest(
      "exerciseAllTransformConfigurationsWithPaths_${path}",
      ::PropertiesFileTransformerTest,
    ) {
      exerciseAllTransformConfigurationsWithPaths(path, paths, input1, input2, expectedOutput)
    }
  }

  for ((path, mappings, input1, input2, expectedOutput) in
    PropertiesFileTransformerTest.transformConfigWithMappingsProvider) {
    runTest(
      "exerciseAllTransformConfigurationsWithMappings_${path}",
      ::PropertiesFileTransformerTest,
    ) {
      exerciseAllTransformConfigurationsWithMappings(path, mappings, input1, input2, expectedOutput)
    }
  }

  for ((path, keyTransformer, input1, input2, expectedOutput) in
    PropertiesFileTransformerTest.keyTransformerProvider) {
    runTest("appliesKeyTransformer_${path}", ::PropertiesFileTransformerTest) {
      appliesKeyTransformer(path, keyTransformer, input1, input2, expectedOutput)
    }
  }

  for ((path, charset, input1, input2) in PropertiesFileTransformerTest.charsetProvider) {
    runTest("appliesCharset_${path}", ::PropertiesFileTransformerTest) {
      appliesCharset(path, charset, input1, input2)
    }
  }
}

private class PropertiesFileTransformerTest : BaseTransformerTest<PropertiesFileTransformer>() {
  fun hasTransformedResource() =
    with(transformer) {
      assertThat(hasTransformedResource()).isFalse()

      transform(context("f.properties", mapOf("foo" to "foo")))

      assertThat(hasTransformedResource()).isTrue()
    }

  fun canTransformResourceWithPaths(path: String, expected: Boolean) {
    assertThat(transformer.canTransformResource(path)).isEqualTo(expected)
  }

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

  // #856
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
      val content = JarPath(tempJar).use { it.getContent(path) }.invariantEolString
      assertThat(content).isEqualTo("foo=one,two\n")
    }

  companion object {
    fun context(
      path: String,
      input: Map<String, String>,
      charset: Charset = Charsets.ISO_8859_1,
    ): TransformerContext {
      val properties = Properties().apply { putAll(input) }
      return TransformerContext(path, properties.inputStream(charset))
    }

    val pathProvider =
      listOf(
        tupleOf("foo.properties", true),
        tupleOf("foo/bar.properties", true),
        tupleOf("a/b/c/ButtonLabel_en.properties", true),
        tupleOf("a/b/c/ButtonLabel_en_US.properties", true),
        tupleOf("a/b/c/ButtonLabel_fr_CA_UNIX.properties", true),
        tupleOf("foo.props", false),
      )

    val charsetProvider =
      listOf(
        tupleOf("utf8.properties", "utf-8", mapOf("foo" to "传傳磨宿说説"), mapOf("foo" to "传傳磨宿说説"))
      )

    val transformConfigWithPathsProvider =
      listOf(
        tupleOf(
          "f.properties",
          listOf("f.properties"),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
        ),
        tupleOf(
          "foo.properties",
          listOf(".*.properties"),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
        ),
        tupleOf(
          "foo.properties",
          listOf(".*bar"),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          emptyMap<String, String>(),
        ),
        tupleOf(
          "foo.properties",
          emptyList<String>(),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
        ),
      )

    val transformConfigWithMappingsProvider =
      listOf(
        tupleOf(
          "f.properties",
          mapOf("f.properties" to mapOf("mergeStrategy" to "first")),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
        ),
        tupleOf(
          "f.properties",
          mapOf("f.properties" to mapOf("mergeStrategy" to "latest")),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "bar"),
        ),
        tupleOf(
          "f.properties",
          mapOf("f.properties" to mapOf("mergeStrategy" to "append")),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo,bar"),
        ),
        tupleOf(
          "f.properties",
          mapOf("f.properties" to mapOf("mergeStrategy" to "append", "mergeSeparator" to ";")),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo;bar"),
        ),
        tupleOf(
          "foo.properties",
          mapOf(".*.properties" to mapOf("mergeStrategy" to "first")),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
        ),
        tupleOf(
          "foo.properties",
          mapOf(".*bar" to mapOf("mergeStrategy" to "first")),
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          emptyMap<String, String>(),
        ),
      )

    val transformConfigProvider =
      listOf(
        tupleOf(
          "f.properties",
          "first",
          "",
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
          mapOf<String, Map<String, Int>>(),
        ),
        tupleOf(
          "f.properties",
          "latest",
          "",
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "bar"),
          mapOf<String, Map<String, Int>>(),
        ),
        tupleOf(
          "f.properties",
          "append",
          ",",
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo,bar"),
          mapOf<String, Map<String, Int>>(),
        ),
        tupleOf(
          "f.properties",
          "append",
          ";",
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo;bar"),
          mapOf<String, Map<String, Int>>(),
        ),
        tupleOf(
          "f.properties",
          "fail",
          ";",
          mapOf("foo" to "foo"),
          mapOf("foo" to "bar"),
          mapOf("foo" to "foo"),
          mapOf("f.properties" to mapOf("foo" to 2)),
        ),
      )

    val keyTransformerProvider =
      listOf(
        tupleOf(
          "foo.properties",
          { key: String -> key },
          mapOf("foo" to "bar"),
          mapOf("FOO" to "baz"),
          mapOf("foo" to "bar", "FOO" to "baz"),
        ),
        tupleOf(
          "foo.properties",
          { key: String -> key.uppercase() },
          mapOf("foo" to "bar"),
          mapOf("FOO" to "baz"),
          mapOf("FOO" to "bar,baz"),
        ),
        tupleOf(
          "foo.properties",
          { key: String -> "bar.${key.lowercase()}" },
          mapOf("foo" to "bar"),
          mapOf("FOO" to "baz"),
          mapOf("bar.foo" to "bar,baz"),
        ),
        tupleOf(
          "foo.properties",
          { key: String -> key.replaceFirst(Regex("^(foo)"), "bar.$1") },
          mapOf("foo" to "bar"),
          mapOf("FOO" to "baz"),
          mapOf("bar.foo" to "bar", "FOO" to "baz"),
        ),
      )
  }
}

private fun <A, B> tupleOf(a: A, b: B) = Pair(a, b)

private fun <A, B, C, D> tupleOf(a: A, b: B, c: C, d: D) = Tuple4(a, b, c, d)

private fun <A, B, C, D, E> tupleOf(a: A, b: B, c: C, d: D, e: E) = Tuple5(a, b, c, d, e)

private fun <A, B, C, D, E, F, G> tupleOf(a: A, b: B, c: C, d: D, e: E, f: F, g: G) =
  Tuple7(a, b, c, d, e, f, g)

data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

data class Tuple7<A, B, C, D, E, F, G>(
  val a: A,
  val b: B,
  val c: C,
  val d: D,
  val e: E,
  val f: F,
  val g: G,
)
