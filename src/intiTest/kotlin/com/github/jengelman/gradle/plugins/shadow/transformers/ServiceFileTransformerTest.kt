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

    val text1 = outputShadowJar.getEntryContent(ENTRY_SERVICES_SHADE)
    assertThat(text1).isEqualTo(CONTENT_ONE_TWO)

    val text2 = outputShadowJar.getEntryContent(ENTRY_SERVICES_FOO)
    assertThat(text2).isEqualTo("one")
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
          path = "META-INF/foo"
        """.trimIndent(),
      ),
    )

    run(shadowJarTask)

    val text = outputShadowJar.getEntryContent(ENTRY_FOO_SHADE)
    assertThat(text).isEqualTo(CONTENT_ONE_TWO)
  }

  @Test
  fun serviceResourceTransformerShortSyntax() {
    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(buildJarOne(), buildJarTwo())}
          mergeServiceFiles {
            exclude("META-INF/services/com.acme.*")
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val text1 = outputShadowJar.getEntryContent(ENTRY_SERVICES_SHADE)
    assertThat(text1).isEqualTo(CONTENT_ONE_TWO)

    val text2 = outputShadowJar.getEntryContent(ENTRY_SERVICES_FOO)
    assertThat(text2).isEqualTo("one")
  }

  @Test
  fun serviceResourceTransformerShortSyntaxRelocation() {
    val one = buildJarOne {
      insert(
        "META-INF/services/java.sql.Driver",
        "oracle.jdbc.OracleDriver\norg.apache.hive.jdbc.HiveDriver",
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
        "org.apache.derby.jdbc.AutoloadedDriver\ncom.mysql.jdbc.Driver",
      )
      insert(
        "META-INF/services/org.apache.axis.components.compiler.Compiler",
        "org.apache.axis.components.compiler.Jikes",
      )
      insert("META-INF/services/org.apache.commons.logging.LogFactory", "org.mortbay.log.Factory")
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(one, two)}
          mergeServiceFiles()
          relocate("org.apache", "myapache") {
            exclude("org.apache.axis.components.compiler.Jikes")
            exclude("org.apache.commons.logging.LogFactory")
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val text1 = outputShadowJar.getEntryContent("META-INF/services/java.sql.Driver")
    assertThat(text1).isEqualTo(
      """
        oracle.jdbc.OracleDriver
        myapache.hive.jdbc.HiveDriver
        myapache.derby.jdbc.AutoloadedDriver
        com.mysql.jdbc.Driver
      """.trimIndent(),
    )

    val text2 = outputShadowJar.getEntryContent("META-INF/services/myapache.axis.components.compiler.Compiler")
    assertThat(text2).isEqualTo(
      """
        myapache.axis.components.compiler.Javac
        org.apache.axis.components.compiler.Jikes
      """.trimIndent(),
    )

    val text3 = outputShadowJar.getEntryContent("META-INF/services/org.apache.commons.logging.LogFactory")
    assertThat(text3).isEqualTo(
      """
        myapache.commons.logging.impl.LogFactoryImpl
        org.mortbay.log.Factory
      """.trimIndent(),
    )
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

    val text = outputShadowJar.getEntryContent(ENTRY_FOO_SHADE)
    assertThat(text).isEqualTo(CONTENT_ONE_TWO)
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
    repo.module("shadow", "two", "1.0")
      .insertFile(servicesShadowEntry, CONTENT_TWO)
      .publish()

    projectScriptPath.appendText(
      """
        dependencies {
          implementation("shadow:two:1.0")
          implementation(files('$one'))
        }
        $shadowJar {
          mergeServiceFiles()
        }
      """.trimIndent(),
    )
    path("src/main/resources/$servicesShadowEntry").writeText(CONTENT_THREE)

    run(shadowJarTask)

    val text = outputShadowJar.getEntryContent(servicesShadowEntry)
    assertThat(text).isEqualTo(CONTENT_THREE + "\n" + CONTENT_ONE_TWO)
  }
}
