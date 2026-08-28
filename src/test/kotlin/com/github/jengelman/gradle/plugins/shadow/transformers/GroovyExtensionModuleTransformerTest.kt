package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.KEY_EXTENSION_CLASSES
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.KEY_MODULE_NAME
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.KEY_MODULE_VERSION
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.KEY_STATIC_EXTENSION_CLASSES
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.MERGED_MODULE_NAME
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.MERGED_MODULE_VERSION
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import java.io.StringReader
import java.util.Properties
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class GroovyExtensionModuleTransformerTest :
  BaseTransformerTest<GroovyExtensionModuleTransformer>() {

  @Test
  fun canTransformResource() =
    with(transformer) {
      assertThat(canTransformResource(PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR)).isTrue()
      assertThat(canTransformResource(PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR)).isTrue()
      assertThat(canTransformResource("META-INF/MANIFEST.MF")).isFalse()
    }

  @ParameterizedTest
  @MethodSource("resourcePathProvider")
  fun mergeDescriptors(fooEntry: String, barEntry: String) =
    with(GroovyExtensionModuleTransformer()) {
      transform(textContext(fooEntry, FOO_DESCRIPTOR))
      transform(textContext(barEntry, BAR_DESCRIPTOR))

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }
      val properties =
        JarPath(tempJar)
          .use { it.getContent(PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR) }
          .toProperties()
      assertThat(properties.getProperty(KEY_MODULE_NAME)).isEqualTo(MERGED_MODULE_NAME)
      assertThat(properties.getProperty(KEY_MODULE_VERSION)).isEqualTo(MERGED_MODULE_VERSION)
      assertThat(properties.getProperty(KEY_EXTENSION_CLASSES))
        .isEqualTo("$EXTENSION_CLASSES_FOO,$EXTENSION_CLASSES_BAR")
      assertThat(properties.getProperty(KEY_STATIC_EXTENSION_CLASSES))
        .isEqualTo("$STATIC_EXTENSION_CLASSES_FOO,$STATIC_EXTENSION_CLASSES_BAR")
    }

  @Test
  fun groovyExtensionModuleTransformerWithRelocation() =
    with(GroovyExtensionModuleTransformer()) {
      val relocator = SimpleRelocator("com.acme", "com.example.shaded.acme")
      transform(textContext(PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR, FOO_DESCRIPTOR, relocator))
      transform(textContext(PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR, BAR_DESCRIPTOR, relocator))

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }
      val properties =
        JarPath(tempJar)
          .use { it.getContent(PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR) }
          .toProperties()
      assertThat(properties.getProperty(KEY_MODULE_NAME)).isEqualTo(MERGED_MODULE_NAME)
      assertThat(properties.getProperty(KEY_MODULE_VERSION)).isEqualTo(MERGED_MODULE_VERSION)
      assertThat(properties.getProperty(KEY_EXTENSION_CLASSES))
        .isEqualTo(
          "com.example.shaded.acme.foo.FooExtension,com.example.shaded.acme.foo.BarExtension," +
            "com.example.shaded.acme.bar.SomeExtension,com.example.shaded.acme.bar.AnotherExtension"
        )
      assertThat(properties.getProperty(KEY_STATIC_EXTENSION_CLASSES))
        .isEqualTo(
          "com.example.shaded.acme.foo.FooStaticExtension,com.example.shaded.acme.bar.SomeStaticExtension"
        )
    }

  private companion object {
    const val EXTENSION_CLASSES_FOO = "com.acme.foo.FooExtension,com.acme.foo.BarExtension"
    const val EXTENSION_CLASSES_BAR = "com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension"
    const val STATIC_EXTENSION_CLASSES_FOO = "com.acme.foo.FooStaticExtension"
    const val STATIC_EXTENSION_CLASSES_BAR = "com.acme.bar.SomeStaticExtension"

    val FOO_DESCRIPTOR =
      """
      |$KEY_MODULE_NAME=foo
      |$KEY_MODULE_VERSION=1.0.5
      |$KEY_EXTENSION_CLASSES=$EXTENSION_CLASSES_FOO
      |$KEY_STATIC_EXTENSION_CLASSES=$STATIC_EXTENSION_CLASSES_FOO
      """
        .trimMargin()

    val BAR_DESCRIPTOR =
      """
      |$KEY_MODULE_NAME=bar
      |$KEY_MODULE_VERSION=2.3.5
      |$KEY_EXTENSION_CLASSES=$EXTENSION_CLASSES_BAR
      |$KEY_STATIC_EXTENSION_CLASSES=$STATIC_EXTENSION_CLASSES_BAR
      """
        .trimMargin()

    fun String.toProperties() = Properties().apply { load(StringReader(this@toProperties)) }

    @JvmStatic
    fun resourcePathProvider() =
      listOf(
        Arguments.of(
          PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR,
          PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR,
        ),
        Arguments.of(
          PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR,
          PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR,
        ),
        Arguments.of(
          PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR,
          PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR,
        ),
        Arguments.of(
          PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR,
          PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR,
        ),
      )
  }
}
