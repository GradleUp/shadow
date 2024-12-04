package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ServiceFileTransformerTest : TransformerTestSupport<ServiceFileTransformer>() {
  @BeforeEach
  fun setup() {
    transformer = ServiceFileTransformer()
  }

  @ParameterizedTest(name = "{index} => path={0}, exclude={1}, expected={2}")
  @MethodSource("canTransformResourceData")
  fun `test canTransformResource`(path: String, exclude: Boolean, expected: Boolean) {
    if (exclude) {
      transformer.exclude(path)
    }
    assertThat(transformer.canTransformResource(getFileElement(path))).isEqualTo(expected)
  }

  @ParameterizedTest(name = "{index} => path={0}")
  @MethodSource("transformsServiceFileData")
  fun `test transforms service file`(path: String, input1: String, input2: String, output: String) {
    val element = getFileElement(path)
    val transformer = ServiceFileTransformer()

    if (transformer.canTransformResource(element)) {
      transformer.transform(context(path, input1))
      transformer.transform(context(path, input2))
    }

    assertThat(transformer.hasTransformedResource()).isEqualTo(true)
    val transformedText = transformer.serviceEntries.getValue(path).toInputStream().bufferedReader().readText()
    assertThat(transformedText).isEqualTo(output)
  }

  @Test
  fun `test excludes Groovy extension module descriptor files by default`() {
    val transformer = ServiceFileTransformer()
    val element = getFileElement("META-INF/services/org.codehaus.groovy.runtime.ExtensionModule")

    assertThat(transformer.canTransformResource(element)).isEqualTo(false)
  }

  private companion object {
    @JvmStatic
    fun canTransformResourceData() = listOf(
      // path, exclude, expected
      Arguments.of("META-INF/services/java.sql.Driver", false, true),
      Arguments.of("META-INF/services/io.dropwizard.logging.AppenderFactory", false, true),
      Arguments.of("META-INF/services/org.apache.maven.Shade", true, false),
      Arguments.of("META-INF/services/foo/bar/moo.goo.Zoo", false, true),
      Arguments.of("foo/bar.properties", false, false),
      Arguments.of("foo.props", false, false),
    )

    @JvmStatic
    fun transformsServiceFileData() = listOf(
      // path, input1, input2, output
      Arguments.of("META-INF/services/com.acme.Foo", "foo", "bar", "foo\nbar"),
      Arguments.of("META-INF/services/com.acme.Bar", "foo\nbar", "zoo", "foo\nbar\nzoo"),
    )
  }
}
