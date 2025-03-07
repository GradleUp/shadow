package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.all
import assertk.assertThat
import assertk.assertions.containsMatch
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class ServiceFileTransformerTest : BaseTransformerTest() {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun serviceResourceTransformer(shortSyntax: Boolean) {
    val config = if (shortSyntax) {
      """
        dependencies {
          ${implementationFiles(buildJarOne(), buildJarTwo())}
        }
        $shadowJar {
          mergeServiceFiles {
            exclude 'META-INF/services/com.acme.*'
          }
        }
      """.trimIndent()
    } else {
      transform<ServiceFileTransformer>(
        dependenciesBlock = implementationFiles(buildJarOne(), buildJarTwo()),
        transformerBlock = """
          exclude 'META-INF/services/com.acme.*'
        """.trimIndent(),
      )
    }
    projectScriptPath.appendText(config)

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      getContent(ENTRY_SERVICES_SHADE).isEqualTo(CONTENT_ONE_TWO)
      getContent(ENTRY_SERVICES_FOO).isEqualTo("two")
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun serviceResourceTransformerAlternatePath(shortSyntax: Boolean) {
    val one = buildJarOne {
      insert(ENTRY_FOO_SHADE, CONTENT_ONE)
    }
    val two = buildJarTwo {
      insert(ENTRY_FOO_SHADE, CONTENT_TWO)
    }
    val config = if (shortSyntax) {
      """
        dependencies {
          ${implementationFiles(one, two)}
        }
        $shadowJar {
          mergeServiceFiles("META-INF/foo")
        }
      """.trimIndent()
    } else {
      transform<ServiceFileTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          path = 'META-INF/foo'
        """.trimIndent(),
      )
    }
    projectScriptPath.appendText(config)

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(ENTRY_FOO_SHADE) }
    assertThat(content).isEqualTo(CONTENT_ONE_TWO)
  }

  @Test
  fun serviceResourceTransformerRelocation() {
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
        dependencies {
          ${implementationFiles(one, two)}
        }
        $shadowJar {
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
      getContent("META-INF/services/java.sql.Driver").isEqualTo(
        """
          oracle.jdbc.OracleDriver
          myapache.hive.jdbc.HiveDriver
          myapache.derby.jdbc.AutoloadedDriver
          com.mysql.jdbc.Driver
        """.trimIndent(),
      )
      getContent("META-INF/services/myapache.axis.components.compiler.Compiler").isEqualTo(
        """
          myapache.axis.components.compiler.Javac
          org.apache.axis.components.compiler.Jikes
        """.trimIndent(),
      )
      getContent("META-INF/services/org.apache.commons.logging.LogFactory").isEqualTo(
        """
          myapache.commons.logging.impl.LogFactoryImpl
          org.mortbay.log.Factory
        """.trimIndent(),
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/70",
    "https://github.com/GradleUp/shadow/issues/71",
  )
  @Test
  fun applyTransformersToProjectResources() {
    val servicesBarEntry = "META-INF/services/foo.Bar"
    val one = buildJarOne {
      insert(servicesBarEntry, CONTENT_ONE)
    }
    localRepo.module("foo", "bar", "1.0") {
      buildJar {
        insert(servicesBarEntry, CONTENT_TWO)
      }
    }.publish()

    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'foo:bar:1.0'
          ${implementationFiles(one)}
        }
        $shadowJar {
          mergeServiceFiles()
        }
      """.trimIndent(),
    )
    path("src/main/resources/$servicesBarEntry").writeText(CONTENT_THREE)

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(servicesBarEntry) }
    assertThat(content).isEqualTo(CONTENT_THREE + "\n" + CONTENT_ONE_TWO)
  }

  @ParameterizedTest
  @MethodSource("withThrowingProvider")
  fun honorDuplicatesStrategyWithThrowing(
    strategy: DuplicatesStrategy,
    outputRegex: String,
  ) {
    writeDuplicatesStrategy(strategy)

    val result = runWithFailure(shadowJarTask)

    assertThat(result).all {
      transform { it.output }.containsMatch(outputRegex.toRegex())
    }
  }

  @ParameterizedTest
  @MethodSource("withoutThrowingProvider")
  fun honorDuplicatesStrategyWithoutThrowing(
    strategy: DuplicatesStrategy,
    firstValue: String,
    secondValue: String,
  ) {
    writeDuplicatesStrategy(strategy)

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      getContent(ENTRY_SERVICES_SHADE).isEqualTo(firstValue)
      getContent(ENTRY_SERVICES_FOO).isEqualTo(secondValue)
    }
  }

  private fun writeDuplicatesStrategy(strategy: DuplicatesStrategy) {
    projectScriptPath.appendText(
      """
        dependencies {
          ${implementationFiles(buildJarOne(), buildJarTwo())}
        }
        $shadowJar {
          duplicatesStrategy = DuplicatesStrategy.$strategy
          mergeServiceFiles()
        }
      """.trimIndent(),
    )
  }

  private companion object {
    @JvmStatic
    fun withThrowingProvider() = listOf(
      Arguments.of(
        DuplicatesStrategy.FAIL,
        "Cannot copy zip entry .* to .* because zip entry .* has already been copied there",
      ),
      Arguments.of(
        DuplicatesStrategy.INHERIT,
        "Entry .* is a duplicate but no duplicate handling strategy has been set",
      ),
    )

    @JvmStatic
    fun withoutThrowingProvider() = listOf(
      Arguments.of(DuplicatesStrategy.EXCLUDE, CONTENT_ONE, "one"),
      Arguments.of(DuplicatesStrategy.INCLUDE, CONTENT_ONE_TWO, "one\ntwo"),
      Arguments.of(DuplicatesStrategy.WARN, CONTENT_ONE_TWO, "one\ntwo"),
    )
  }
}
