package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy
import com.github.jengelman.gradle.plugins.shadow.util.testObjectFactory
import groovy.lang.Closure
import java.nio.charset.Charset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class PropertiesFileTransformerSpec : TransformerTestSupport<PropertiesFileTransformer>() {

  @BeforeEach
  fun setup() {
    transformer = PropertiesFileTransformer(testObjectFactory)
  }

  @ParameterizedTest(name = "Path {0} {2} transformed")
  @MethodSource("pathProvider")
  fun `test canTransformResource with paths`(path: String, expected: Boolean, transform: String) {
    assertThat(transformer.canTransformResource(getFileElement(path))).isEqualTo(expected)
  }

  @ParameterizedTest(name = "mergeStrategy={1}, mergeSeparator='{2}'")
  @MethodSource("transformConfigurationsProvider")
  fun `test exerciseAllTransformConfigurations`(
    path: String,
    mergeStrategy: String,
    mergeSeparator: String,
    input1: Map<String, String>,
    input2: Map<String, String>,
    expectedOutput: Map<String, String>,
  ) {
    val element = getFileElement(path)
    transformer.mergeStrategy.set(MergeStrategy.from(mergeStrategy))
    transformer.mergeSeparator.set(mergeSeparator)

    if (transformer.canTransformResource(element)) {
      transformer.transform(context(path, input1))
      transformer.transform(context(path, input2))
    }

    assertThat(transformer.propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
  }

  @ParameterizedTest(name = "Paths={1}")
  @MethodSource("transformConfigurationsWithPathsProvider")
  fun `test exerciseAllTransformConfigurationsWithPaths`(
    path: String,
    paths: List<String>,
    input1: Map<String, String>,
    input2: Map<String, String>,
    expectedOutput: Map<String, String>,
  ) {
    val element = getFileElement(path)
    transformer.paths.set(paths)
    transformer.mergeStrategy.set(MergeStrategy.from("first"))

    if (transformer.canTransformResource(element)) {
      transformer.transform(context(path, input1))
      transformer.transform(context(path, input2))
    }

    assertThat(transformer.propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
  }

  @ParameterizedTest(name = "Mappings={1}")
  @MethodSource("transformConfigurationsWithMappingsProvider")
  fun `test exerciseAllTransformConfigurationsWithMappings`(
    path: String,
    mappings: Map<String, Map<String, String>>,
    input1: Map<String, String>,
    input2: Map<String, String>,
    expectedOutput: Map<String, String>,
  ) {
    val element = getFileElement(path)
    transformer.mappings.set(mappings)
    transformer.mergeStrategy.set(MergeStrategy.from("latest"))

    if (transformer.canTransformResource(element)) {
      transformer.transform(context(path, input1))
      transformer.transform(context(path, input2))
    }

    assertThat(transformer.propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
  }

  @ParameterizedTest(name = "KeyTransformer: {1}")
  @MethodSource("appliesKeyTransformerProvider")
  fun `test appliesKeyTransformer`(
    path: String,
    keyTransformer: (String) -> String,
    input1: Map<String, String>,
    input2: Map<String, String>,
    expectedOutput: Map<String, String>,
  ) {
    val element = getFileElement(path)
    transformer.keyTransformer.set(object : Closure<String>(null) {
      override fun call(vararg arguments: Any?): String {
        return keyTransformer.invoke(arguments.first() as String)
      }
    })
    transformer.mergeStrategy.set(MergeStrategy.from("append"))

    if (transformer.canTransformResource(element)) {
      transformer.transform(context(path, input1))
      transformer.transform(context(path, input2))
    }

    assertThat(transformer.propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
  }

  @ParameterizedTest(name = "Charset: {1}")
  @MethodSource("appliesCharsetProvider")
  fun `test appliesCharset`(
    path: String,
    charset: String,
    input: Map<String, String>,
    expectedOutput: Map<String, String>,
  ) {
    val element = getFileElement(path)
    transformer.charsetName.set(charset)

    if (transformer.canTransformResource(element)) {
      transformer.transform(context(path, input, Charset.forName(charset)))
    }

    assertThat(transformer.propertiesEntries[path].orEmpty()).isEqualTo(expectedOutput)
  }

  private companion object {
    @JvmStatic
    fun pathProvider() = listOf(
      Arguments.of("foo.properties", true, "can be"),
      Arguments.of("foo/bar.properties", true, "can be"),
      Arguments.of("foo.props", false, "can not be"),
    )

    @JvmStatic
    fun appliesCharsetProvider() = listOf(
      Arguments.of(
        "utf8.properties",
        "utf-8",
        mapOf("foo" to "传傳磨宿说説"),
        mapOf("foo" to "传傳磨宿说説"),
      ),
    )

    @JvmStatic
    fun transformConfigurationsWithPathsProvider() = listOf(
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
    fun transformConfigurationsWithMappingsProvider() = listOf(
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
    fun transformConfigurationsProvider() = listOf(
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
    fun appliesKeyTransformerProvider() = listOf(
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
