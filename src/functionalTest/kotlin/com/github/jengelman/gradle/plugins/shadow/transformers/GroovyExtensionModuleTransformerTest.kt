package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.KEY_EXTENSION_CLASSES
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.KEY_MODULE_NAME
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.KEY_MODULE_VERSION
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.KEY_STATIC_EXTENSION_CLASSES
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.MERGED_MODULE_NAME
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.MERGED_MODULE_VERSION
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import java.nio.file.Path
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GroovyExtensionModuleTransformerTest : BaseTransformerTest() {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun groovyExtensionModuleTransformer(shortSyntax: Boolean) {
    val config = if (shortSyntax) {
      """
        dependencies {
          ${implementationFiles(buildJarFoo(), buildJarBar())}
        }
        $shadowJarTask {
          mergeGroovyExtensionModules()
        }
      """.trimIndent()
    } else {
      transform<GroovyExtensionModuleTransformer>(
        dependenciesBlock = implementationFiles(buildJarFoo(), buildJarBar()),
      )
    }
    projectScript.appendText(config)

    run(shadowJarPath)

    commonAssertions(PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR)
  }

  @Test
  fun groovyExtensionModuleTransformerWorksForLegacyGroovy() {
    projectScript.appendText(
      transform<GroovyExtensionModuleTransformer>(
        dependenciesBlock = implementationFiles(
          buildJarFoo(PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR),
          buildJarBar(PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR),
        ),
      ),
    )

    run(shadowJarPath)

    commonAssertions(PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR)
  }

  private fun buildJarFoo(
    entry: String = PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR,
  ): Path = buildJar("foo.jar") {
    insert(
      entry,
      """
        $KEY_MODULE_NAME=foo
        $KEY_MODULE_VERSION=1.0.5
        $KEY_EXTENSION_CLASSES=$EXTENSION_CLASSES_FOO
        $KEY_STATIC_EXTENSION_CLASSES=$STATIC_EXTENSION_CLASSES_FOO
      """.trimIndent(),
    )
  }

  private fun buildJarBar(
    entry: String = PATH_GROOVY_EXTENSION_MODULE_DESCRIPTOR,
  ): Path = buildJar("bar.jar") {
    insert(
      entry,
      """
        $KEY_MODULE_NAME=bar
        $KEY_MODULE_VERSION=2.3.5
        $KEY_EXTENSION_CLASSES=$EXTENSION_CLASSES_BAR
        $KEY_STATIC_EXTENSION_CLASSES=$STATIC_EXTENSION_CLASSES_BAR
      """.trimIndent(),
    )
  }

  private fun commonAssertions(entry: String) {
    val properties = outputShadowedJar.use { it.getContent(entry) }.toProperties()

    assertThat(properties.getProperty(KEY_MODULE_NAME)).isEqualTo(MERGED_MODULE_NAME)
    assertThat(properties.getProperty(KEY_MODULE_VERSION)).isEqualTo(MERGED_MODULE_VERSION)
    assertThat(properties.getProperty(KEY_EXTENSION_CLASSES))
      .isEqualTo("$EXTENSION_CLASSES_FOO,$EXTENSION_CLASSES_BAR")
    assertThat(properties.getProperty(KEY_STATIC_EXTENSION_CLASSES))
      .isEqualTo("$STATIC_EXTENSION_CLASSES_FOO,$STATIC_EXTENSION_CLASSES_BAR")
  }

  private companion object {
    const val EXTENSION_CLASSES_FOO = "com.acme.foo.FooExtension,com.acme.foo.BarExtension"
    const val EXTENSION_CLASSES_BAR = "com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension"
    const val STATIC_EXTENSION_CLASSES_FOO = "com.acme.foo.FooStaticExtension"
    const val STATIC_EXTENSION_CLASSES_BAR = "com.acme.bar.SomeStaticExtension"
  }
}
