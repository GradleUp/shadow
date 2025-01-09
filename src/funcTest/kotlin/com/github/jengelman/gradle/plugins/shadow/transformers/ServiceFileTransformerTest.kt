package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class ServiceFileTransformerTest : BaseTransformerTest() {
  @Test
  fun serviceResourceTransformer() {
    projectScriptPath.appendText(
      transform<ServiceFileTransformer>(
        shadowJarBlock = fromJar(buildJarOne(), buildJarTwo()),
        transformerBlock = """
          exclude 'META-INF/services/com.acme.*'
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      transform { it.getContent(ENTRY_SERVICES_SHADE) }.isEqualTo(CONTENT_ONE_TWO)
      transform { it.getContent(ENTRY_SERVICES_FOO) }.isEqualTo("one")
    }
  }

  @Test
  fun serviceResourceTransformerAlternatePath() {
    val one = buildJarOne {
      insert(ENTRY_FOO_SHADE, CONTENT_ONE)
    }
    val two = buildJarTwo {
      insert(ENTRY_FOO_SHADE, CONTENT_TWO)
    }
    projectScriptPath.appendText(
      transform<ServiceFileTransformer>(
        shadowJarBlock = fromJar(one, two),
        transformerBlock = """
          path = 'META-INF/foo'
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(ENTRY_FOO_SHADE) }
    assertThat(content).isEqualTo(CONTENT_ONE_TWO)
  }

  @Test
  fun serviceResourceTransformerShortSyntax() {
    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(buildJarOne(), buildJarTwo())}
          mergeServiceFiles {
            exclude 'META-INF/services/com.acme.*'
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      transform { it.getContent(ENTRY_SERVICES_SHADE) }.isEqualTo(CONTENT_ONE_TWO)
      transform { it.getContent(ENTRY_SERVICES_FOO) }.isEqualTo("one")
    }
  }

  @Test
  fun serviceResourceTransformerShortSyntaxRelocation() {
    val one = buildJarOne {
      insert(
        "META-INF/services/java.sql.Driver",
        """
          oracle.jdbc.OracleDriver
          org.apache.hive.jdbc.HiveDriver
        """.trimIndent(),
      )
      insert(
        "META-INF/services/org.apache.axis.components.compiler.Compiler",
        "org.apache.axis.components.compiler.Javac",
      )
      insert(
        "META-INF/services/org.apache.commons.logging.LogFactory",
        "org.apache.commons.logging.impl.LogFactoryImpl",
      )
    }
    val two = buildJarTwo {
      insert(
        "META-INF/services/java.sql.Driver",
        """
          org.apache.derby.jdbc.AutoloadedDriver
          com.mysql.jdbc.Driver
        """.trimIndent(),
      )
      insert(
        "META-INF/services/org.apache.axis.components.compiler.Compiler",
        "org.apache.axis.components.compiler.Jikes",
      )
      insert(
        "META-INF/services/org.apache.commons.logging.LogFactory",
        "org.mortbay.log.Factory",
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(one, two)}
          mergeServiceFiles()
          relocate("org.apache", "myapache") {
            exclude 'org.apache.axis.components.compiler.Jikes'
            exclude 'org.apache.commons.logging.LogFactory'
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      transform { it.getContent("META-INF/services/java.sql.Driver") }
        .isEqualTo(
          """
            oracle.jdbc.OracleDriver
            myapache.hive.jdbc.HiveDriver
            myapache.derby.jdbc.AutoloadedDriver
            com.mysql.jdbc.Driver
          """.trimIndent(),
        )
      transform { it.getContent("META-INF/services/myapache.axis.components.compiler.Compiler") }
        .isEqualTo(
          """
            myapache.axis.components.compiler.Javac
            org.apache.axis.components.compiler.Jikes
          """.trimIndent(),
        )
      transform { it.getContent("META-INF/services/org.apache.commons.logging.LogFactory") }
        .isEqualTo(
          """
            myapache.commons.logging.impl.LogFactoryImpl
            org.mortbay.log.Factory
          """.trimIndent(),
        )
    }
  }

  @Test
  fun serviceResourceTransformerShortSyntaxAlternatePath() {
    val one = buildJarOne {
      insert(ENTRY_FOO_SHADE, CONTENT_ONE)
    }
    val two = buildJarTwo {
      insert(ENTRY_FOO_SHADE, CONTENT_TWO)
    }
    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(one, two)}
          mergeServiceFiles("META-INF/foo")
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(ENTRY_FOO_SHADE) }
    assertThat(content).isEqualTo(CONTENT_ONE_TWO)
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/70",
    "https://github.com/GradleUp/shadow/issues/71",
  )
  @Test
  fun applyTransformersToProjectResources() {
    val servicesShadowEntry = "META-INF/services/shadow.Shadow"
    val one = buildJarOne {
      insert(servicesShadowEntry, CONTENT_ONE)
    }.toUri().toURL().path
    localRepo.module("shadow", "two", "1.0") {
      buildJar {
        insert(servicesShadowEntry, CONTENT_TWO)
      }
    }.publish()

    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'shadow:two:1.0'
          implementation files('$one')
        }
        $shadowJar {
          mergeServiceFiles()
        }
      """.trimIndent(),
    )
    path("src/main/resources/$servicesShadowEntry").writeText(CONTENT_THREE)

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(servicesShadowEntry) }
    assertThat(content).isEqualTo(CONTENT_THREE + "\n" + CONTENT_ONE_TWO)
  }
}
