package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class GroovyExtensionModuleTransformerTest : BaseTransformerTest() {
  @Test
  fun groovyExtensionModuleTransformer() {
    val one = buildJarOne {
      insert(
        ENTRY_SERVICE_EXTENSION_MODULE,
        """
          moduleName=foo
          moduleVersion=1.0.5
          extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
          staticExtensionClasses=com.acme.foo.FooStaticExtension
        """.trimIndent(),
      )
    }
    val two = buildJarTwo {
      insert(
        ENTRY_SERVICE_EXTENSION_MODULE,
        """
          moduleName=bar
          moduleVersion=2.3.5
          extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
          staticExtensionClasses=com.acme.bar.SomeStaticExtension
        """.trimIndent(),
      )
    }
    projectScriptPath.appendText(
      transform<GroovyExtensionModuleTransformer>(
        shadowJarBlock = fromJar(one, two),
      ),
    )

    run(shadowJarTask)

    val props = outputShadowJar.getContent(ENTRY_SERVICE_EXTENSION_MODULE).toProperties()
    assertThat(props.getProperty("moduleName")).isEqualTo("MergedByShadowJar")
    assertThat(props.getProperty("moduleVersion")).isEqualTo("1.0.0")
    assertThat(props.getProperty("extensionClasses"))
      .isEqualTo("com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension")
    assertThat(props.getProperty("staticExtensionClasses"))
      .isEqualTo("com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension")
  }

  @Test
  fun groovyExtensionModuleTransformerWorksForGroovy25Plus() {
    val one = buildJarOne {
      insert(
        ENTRY_GROOVY_EXTENSION_MODULE,
        """
          moduleName=foo
          moduleVersion=1.0.5
          extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
          staticExtensionClasses=com.acme.foo.FooStaticExtension
        """.trimIndent(),
      )
    }
    val two = buildJarTwo {
      insert(
        ENTRY_SERVICE_EXTENSION_MODULE,
        """
          moduleName=bar
          moduleVersion=2.3.5
          extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
          staticExtensionClasses=com.acme.bar.SomeStaticExtension
        """.trimIndent(),
      )
    }
    projectScriptPath.appendText(
      transform<GroovyExtensionModuleTransformer>(
        shadowJarBlock = fromJar(one, two),
      ),
    )

    run(shadowJarTask)

    val props = outputShadowJar.getContent(ENTRY_GROOVY_EXTENSION_MODULE).toProperties()
    assertThat(props.getProperty("moduleName")).isEqualTo("MergedByShadowJar")
    assertThat(props.getProperty("moduleVersion")).isEqualTo("1.0.0")
    assertThat(props.getProperty("extensionClasses"))
      .isEqualTo("com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension")
    assertThat(props.getProperty("staticExtensionClasses"))
      .isEqualTo("com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension")
    assertThat(outputShadowJar).doesNotContainEntries(
      ENTRY_SERVICE_EXTENSION_MODULE,
    )
  }

  @Test
  fun groovyExtensionModuleTransformerShortSyntax() {
    val one = buildJarOne {
      insert(
        ENTRY_SERVICE_EXTENSION_MODULE,
        """
          moduleName=foo
          moduleVersion=1.0.5
          extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
          staticExtensionClasses=com.acme.foo.FooStaticExtension
        """.trimIndent(),
      )
    }
    val two = buildJarTwo {
      insert(
        ENTRY_SERVICE_EXTENSION_MODULE,
        """
          moduleName=bar
          moduleVersion=2.3.5
          extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
          staticExtensionClasses=com.acme.bar.SomeStaticExtension
        """.trimIndent(),
      )
    }
    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(one, two)}
          mergeGroovyExtensionModules()
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val props = outputShadowJar.getContent(ENTRY_SERVICE_EXTENSION_MODULE).toProperties()
    assertThat(props.getProperty("moduleName")).isEqualTo("MergedByShadowJar")
    assertThat(props.getProperty("moduleVersion")).isEqualTo("1.0.0")
    assertThat(props.getProperty("extensionClasses"))
      .isEqualTo("com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension")
    assertThat(props.getProperty("staticExtensionClasses"))
      .isEqualTo("com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension")
  }
}
