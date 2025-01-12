package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.isRegular
import java.util.jar.Attributes
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class TransformersTest : BaseTransformerTest() {
  @Test
  fun manifestRetained() {
    writeMainClass()
    projectScriptPath.appendText(
      """
        jar {
          manifest {
            attributes 'Main-Class': 'shadow.Main'
            attributes 'Test-Entry': 'PASSED'
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    commonAssertions {
      assertThat(getValue("Test-Entry")).isEqualTo("PASSED")
      assertThat(getValue("Main-Class")).isEqualTo("shadow.Main")
    }
  }

  @Test
  fun manifestTransformed() {
    writeMainClass()

    projectScriptPath.appendText(MANIFEST_ATTRS)

    run(shadowJarTask)

    commonAssertions()
  }

  @Issue("https://github.com/GradleUp/shadow/issues/82")
  @Test
  fun shadowManifestLeaksToJarManifest() {
    writeMainClass()
    projectScriptPath.appendText(MANIFEST_ATTRS)

    run("jar", shadowJarTask)

    commonAssertions()

    val mf = jarPath("build/libs/shadow-1.0.jar").use { it.manifest }
    assertThat(mf).isNotNull()
    assertThat(mf.mainAttributes.getValue("Test-Entry")).isEqualTo("FAILED")
    assertThat(mf.mainAttributes.getValue("Main-Class")).isEqualTo("shadow.Main")
    assertThat(mf.mainAttributes.getValue("New-Entry")).isNull()
  }

  @Test
  fun canUseCustomTransformer() {
    writeMainClass()
    projectScriptPath.appendText(
      """
        $shadowJar {
          // Use NoOpTransformer to mock a custom transformer here.
          transform(${NoOpTransformer::class.java.name}.INSTANCE)
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).isRegular()
  }

  @ParameterizedTest
  @MethodSource("transformerConfigurations")
  fun otherTransformers(pair: Pair<String, KClass<*>>) {
    val (configuration, transformer) = pair
    if (configuration.contains("test/some.file")) {
      path("test/some.file").writeText("some content")
    }
    projectScriptPath.appendText(
      """
        $shadowJar {
          transform(${transformer.java.name}) $configuration
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).isRegular()
  }

  private fun commonAssertions(
    mainAttributesBlock: Attributes.() -> Unit = {
      assertThat(getValue("Test-Entry")).isEqualTo("PASSED")
      assertThat(getValue("Main-Class")).isEqualTo("shadow.Main")
      assertThat(getValue("New-Entry")).isEqualTo("NEW")
    },
  ) {
    val mf = outputShadowJar.use { it.manifest }
    assertThat(mf).isNotNull()
    mainAttributesBlock(mf.mainAttributes)
  }

  private companion object {
    val MANIFEST_ATTRS = """
        jar {
          manifest {
            attributes 'Main-Class': 'shadow.Main'
            attributes 'Test-Entry': 'FAILED'
          }
        }
        $shadowJar {
          manifest {
            attributes 'New-Entry': 'NEW'
            attributes 'Test-Entry': 'PASSED'
          }
        }
    """.trimIndent()

    @JvmStatic
    fun transformerConfigurations() = listOf(
      "" to ApacheLicenseResourceTransformer::class,
      "" to ApacheNoticeResourceTransformer::class,
      "" to ComponentsXmlResourceTransformer::class,
      "" to DontIncludeResourceTransformer::class,
      "{ resource.set(\"test.file\"); file.fileValue(file(\"test/some.file\")) }" to IncludeResourceTransformer::class,
      "" to Log4j2PluginsCacheFileTransformer::class,
      "" to ManifestAppenderTransformer::class,
      "" to ManifestResourceTransformer::class,
      "{ keyTransformer = { it.toLowerCase() } }" to PropertiesFileTransformer::class,
    )
  }
}
