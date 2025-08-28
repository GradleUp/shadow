package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.containsMatch
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.DuplicatesStrategy.EXCLUDE
import org.gradle.api.file.DuplicatesStrategy.FAIL
import org.gradle.api.file.DuplicatesStrategy.INCLUDE
import org.gradle.api.file.DuplicatesStrategy.INHERIT
import org.gradle.api.file.DuplicatesStrategy.WARN
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
        $shadowJarTask {
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
    projectScript.appendText(config)

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
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
        $shadowJarTask {
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
    projectScript.appendText(config)

    run(shadowJarPath)

    val content = outputShadowedJar.use { it.getContent(ENTRY_FOO_SHADE) }
    assertThat(content).isEqualTo(CONTENT_ONE_TWO)
  }

  @Test
  fun serviceResourceTransformerWithRelocation() {
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

    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(one, two)}
        }
        $shadowJarTask {
          mergeServiceFiles()
          relocate("org.apache", "myapache") {
            exclude 'org.apache.axis.components.compiler.Jikes'
            exclude 'org.apache.commons.logging.LogFactory'
          }
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
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
  fun transformProjectResources() {
    val servicesBarEntry = "META-INF/services/foo.Bar"
    val one = buildJarOne {
      insert(servicesBarEntry, CONTENT_ONE)
    }
    val two = buildJarTwo {
      insert(servicesBarEntry, CONTENT_TWO)
    }
    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(one, two)}
        }
        $shadowJarTask {
          mergeServiceFiles()
        }
      """.trimIndent(),
    )
    path("src/main/resources/$servicesBarEntry").writeText(CONTENT_THREE)

    run(shadowJarPath)

    val content = outputShadowedJar.use { it.getContent(servicesBarEntry) }
    assertThat(content).isEqualTo(CONTENT_THREE + "\n" + CONTENT_ONE_TWO)
  }

  @ParameterizedTest
  @MethodSource("withThrowingProvider")
  fun honorDuplicatesStrategyWithThrowing(
    strategy: DuplicatesStrategy,
    outputRegex: String,
  ) {
    writeDuplicatesStrategy(strategy)

    val result = runWithFailure(shadowJarPath)

    assertThat(result.output).containsMatch(outputRegex.toRegex())
  }

  @ParameterizedTest
  @MethodSource("withoutThrowingProvider")
  fun honorDuplicatesStrategyWithoutThrowing(
    strategy: DuplicatesStrategy,
    firstValue: String,
    secondValue: String,
  ) {
    writeDuplicatesStrategy(strategy)

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getContent(ENTRY_SERVICES_SHADE).isEqualTo(firstValue)
      getContent(ENTRY_SERVICES_FOO).isEqualTo(secondValue)
    }
  }

  @Test
  fun strategyCanBeOverriddenByFilesMatching() {
    writeDuplicatesStrategy(EXCLUDE)
    projectScript.appendText(
      """
        $shadowJarTask {
          filesMatching('$ENTRY_SERVICES_SHADE') {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
          }
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getContent(ENTRY_SERVICES_SHADE).isEqualTo(CONTENT_ONE_TWO)
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one")
    }
  }

  @Test
  fun strategyCanBeOverriddenByFilesNotMatching() {
    writeDuplicatesStrategy(INCLUDE)
    projectScript.appendText(
      """
        $shadowJarTask {
          filesNotMatching('$ENTRY_SERVICES_SHADE') {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
          }
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getContent(ENTRY_SERVICES_SHADE).isEqualTo(CONTENT_ONE_TWO)
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one")
    }
  }

  @ParameterizedTest
  @MethodSource("eachFileStrategyProvider")
  fun strategyCanBeOverriddenByEachFile(
    default: DuplicatesStrategy,
    override: DuplicatesStrategy,
    matchPath: String,
  ) {
    writeDuplicatesStrategy(default)
    projectScript.appendText(
      """
        $shadowJarTask {
          eachFile {
            if (path == '$matchPath') {
              duplicatesStrategy = DuplicatesStrategy.$override
            }
          }
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getContent(ENTRY_SERVICES_SHADE).isEqualTo(CONTENT_ONE_TWO)
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one")
    }
  }

  private fun writeDuplicatesStrategy(strategy: DuplicatesStrategy) {
    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(buildJarOne(), buildJarTwo())}
        }
        $shadowJarTask {
          duplicatesStrategy = DuplicatesStrategy.$strategy
          mergeServiceFiles()
        }
      """.trimIndent() + lineSeparator,
    )
  }

  private companion object {
    @JvmStatic
    fun withThrowingProvider() = listOf(
      Arguments.of(FAIL, "Cannot copy zip entry .* to .* because zip entry .* has already been copied there"),
      Arguments.of(INHERIT, "Entry .* is a duplicate but no duplicate handling strategy has been set"),
    )

    @JvmStatic
    fun withoutThrowingProvider() = listOf(
      Arguments.of(EXCLUDE, CONTENT_ONE, "one"),
      Arguments.of(INCLUDE, CONTENT_ONE_TWO, "one\ntwo"),
      Arguments.of(WARN, CONTENT_ONE_TWO, "one\ntwo"),
    )

    @JvmStatic
    fun eachFileStrategyProvider() = listOf(
      Arguments.of(EXCLUDE, INCLUDE, ENTRY_SERVICES_SHADE),
      Arguments.of(INCLUDE, EXCLUDE, ENTRY_SERVICES_FOO),
    )
  }
}
