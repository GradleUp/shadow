package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ServiceFileTransformerTest : BaseTransformerTest<ServiceFileTransformer>() {
  @ParameterizedTest
  @MethodSource("resourceProvider")
  fun canTransformResource(path: String, exclude: Boolean, expected: Boolean) {
    if (exclude) {
      transformer.exclude(path)
    }
    assertThat(transformer.canTransformResource(path)).isEqualTo(expected)
  }

  @ParameterizedTest
  @MethodSource("serviceFileProvider")
  fun transformServiceFile(path: String, input1: String, input2: String, output: String) {
    if (transformer.canTransformResource(path)) {
      transformer.transform(context(path, input1))
      transformer.transform(context(path, input2))
    }

    assertThat(transformer.hasTransformedResource()).isTrue()
    val entry = transformer.serviceEntries.getValue(path).joinToString("\n")
    assertThat(entry).isEqualTo(output)
  }

  @Test
  fun excludesGroovyExtensionModuleDescriptorFilesByDefault() {
    val element = "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"
    assertThat(transformer.canTransformResource(element)).isFalse()
  }

  @Test
  fun canTransformAlternateResource() {
    transformer.path = "foo/bar"
    assertThat(transformer.canTransformResource("foo/bar/moo/goo/Zoo")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/services/Zoo")).isFalse()
  }

  private companion object {
    fun context(path: String, input: String): TransformerContext {
      return TransformerContext(path, input.byteInputStream(), stats = sharedStats)
    }

    @JvmStatic
    fun resourceProvider() = listOf(
      // path, exclude, expected
      Arguments.of("META-INF/services/java.sql.Driver", false, true),
      Arguments.of("META-INF/services/io.dropwizard.logging.AppenderFactory", false, true),
      Arguments.of("META-INF/services/org.apache.maven.Shade", true, false),
      Arguments.of("META-INF/services/foo/bar/moo.goo.Zoo", false, true),
      Arguments.of("foo/bar.properties", false, false),
      Arguments.of("foo.props", false, false),
    )

    @JvmStatic
    fun serviceFileProvider() = listOf(
      // path, input1, input2, output
      Arguments.of("META-INF/services/com.acme.Foo", "foo", "bar", "foo\nbar"),
      Arguments.of("META-INF/services/com.acme.Bar", "foo\nbar", "zoo", "foo\nbar\nzoo"),
    )
  }
}
