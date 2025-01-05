package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.EXTENSION_CLASSES_KEY
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.GROOVY_LEGACY_EXTENSION_MODULE_DESCRIPTOR_PATH
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.MERGED_MODULE_NAME
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.MERGED_MODULE_VERSION
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.MODULE_NAME_KEY
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.MODULE_VERSION_KEY
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.STATIC_EXTENSION_CLASSES_KEY
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class GroovyExtensionModuleTransformerTest : BaseTransformerTest() {
  @Test
  fun groovyExtensionModuleTransformer() {
    projectScriptPath.appendText(
      transform<GroovyExtensionModuleTransformer>(
        shadowJarBlock = fromJar(buildJarFoo(), buildJarBar()),
      ),
    )

    run(shadowJarTask)

    val props = outputShadowJar.getContent(GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH).toProperties()
    commonAssertions(props)
  }

  @Test
  fun groovyExtensionModuleTransformerWorksForLegacyGroovy() {
    projectScriptPath.appendText(
      transform<GroovyExtensionModuleTransformer>(
        shadowJarBlock = fromJar(
          buildJarFoo(GROOVY_LEGACY_EXTENSION_MODULE_DESCRIPTOR_PATH),
          buildJarBar(GROOVY_LEGACY_EXTENSION_MODULE_DESCRIPTOR_PATH),
        ),
      ),
    )

    run(shadowJarTask)

    val props = outputShadowJar.getContent(GROOVY_LEGACY_EXTENSION_MODULE_DESCRIPTOR_PATH).toProperties()
    commonAssertions(props)
  }

  @Test
  fun groovyExtensionModuleTransformerShortSyntax() {
    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(buildJarFoo(), buildJarBar())}
          mergeGroovyExtensionModules()
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val props = outputShadowJar.getContent(GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH).toProperties()
    commonAssertions(props)
  }

  private fun buildJarFoo(
    path: String = GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH,
  ): Path = buildJar("foo.jar") {
    insert(
      path,
      """
        $MODULE_NAME_KEY=foo
        $MODULE_VERSION_KEY=1.0.5
        $EXTENSION_CLASSES_KEY=$EXTENSION_CLASSES_FOO
        $STATIC_EXTENSION_CLASSES_KEY=$STATIC_EXTENSION_CLASSES_FOO
      """.trimIndent(),
    )
  }

  private fun buildJarBar(
    path: String = GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH,
  ): Path = buildJar("bar.jar") {
    insert(
      path,
      """
        $MODULE_NAME_KEY=bar
        $MODULE_VERSION_KEY=2.3.5
        $EXTENSION_CLASSES_KEY=$EXTENSION_CLASSES_BAR
        $STATIC_EXTENSION_CLASSES_KEY=$STATIC_EXTENSION_CLASSES_BAR
      """.trimIndent(),
    )
  }

  private companion object {
    const val EXTENSION_CLASSES_FOO = "com.acme.foo.FooExtension,com.acme.foo.BarExtension"
    const val EXTENSION_CLASSES_BAR = "com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension"
    const val STATIC_EXTENSION_CLASSES_FOO = "com.acme.foo.FooStaticExtension"
    const val STATIC_EXTENSION_CLASSES_BAR = "com.acme.bar.SomeStaticExtension"

    fun commonAssertions(properties: Properties) {
      assertThat(properties.getProperty(MODULE_NAME_KEY)).isEqualTo(MERGED_MODULE_NAME)
      assertThat(properties.getProperty(MODULE_VERSION_KEY)).isEqualTo(MERGED_MODULE_VERSION)
      assertThat(properties.getProperty(EXTENSION_CLASSES_KEY))
        .isEqualTo("$EXTENSION_CLASSES_FOO,$EXTENSION_CLASSES_BAR")
      assertThat(properties.getProperty(STATIC_EXTENSION_CLASSES_KEY))
        .isEqualTo("$STATIC_EXTENSION_CLASSES_FOO,$STATIC_EXTENSION_CLASSES_BAR")
    }
  }
}
