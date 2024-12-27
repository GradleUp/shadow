package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.exists
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FilteringTest : BasePluginTest() {

  @BeforeEach
  override fun setup() {
    super.setup()
    publishArtifactA()
    publishArtifactB()

    buildScript.appendText(System.lineSeparator())
    buildScript.appendText(
      """
        dependencies {
          implementation 'shadow:a:1.0'
          implementation 'shadow:b:1.0'
        }
      """.trimIndent(),
    )
  }

  @Test
  fun includeAllDependencies() {
    run(shadowJarTask)
    assertContains(
      outputShadowJar,
      listOf("a.properties", "a2.properties", "b.properties"),
    )
  }

  @Test
  fun excludeFiles() {
    buildScript.appendText(System.lineSeparator())
    buildScript.appendText(
      """
        $shadowJar {
          exclude 'a2.properties'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertContains(
      outputShadowJar,
      listOf("a.properties", "b.properties"),
    )
    assertDoesNotContain(
      outputShadowJar,
      listOf("a2.properties"),
    )
  }

  @Test
  fun excludeDependency() {
    publishArtifactCD()
    dependOnAndExcludeArtifactD()

    run(shadowJarTask)

    assertContains(
      outputShadowJar,
      listOf("a.properties", "a2.properties", "b.properties", "c.properties"),
    )
    assertDoesNotContain(
      outputShadowJar,
      listOf("d.properties"),
    )
  }

  @Test
  fun excludeDependencyUsingWildcardSyntax() {
    publishArtifactCD()
    buildScript.appendText(System.lineSeparator())
    buildScript.appendText(
      """
        dependencies {
          implementation 'shadow:d:1.0'
        }
        $shadowJar {
          dependencies {
            exclude(dependency('shadow:d:.*'))
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertContains(
      outputShadowJar,
      listOf("a.properties", "a2.properties", "b.properties", "c.properties"),
    )
    assertDoesNotContain(
      outputShadowJar,
      listOf("d.properties"),
    )
  }

  @Test
  fun dependencyExclusionsAffectUpToDateCheck() {
    publishArtifactCD()
    dependOnAndExcludeArtifactD()

    run(shadowJarTask)

    assertContains(
      outputShadowJar,
      listOf("a.properties", "a2.properties", "b.properties", "c.properties"),
    )
    assertDoesNotContain(
      outputShadowJar,
      listOf("d.properties"),
    )

    val replaced = buildScript.readText()
      .replace("exclude(dependency('shadow:d:1.0'))", "exclude(dependency('shadow:c:1.0'))")
    buildScript.writeText(replaced)
    val result = run(shadowJarTask)

    assertThat(result.task(":shadowJar")).isNotNull()
      .transform { it.outcome }.isEqualTo(TaskOutcome.SUCCESS)
    assertContains(
      outputShadowJar,
      listOf("a.properties", "a2.properties", "b.properties", "d.properties"),
    )
    assertDoesNotContain(
      outputShadowJar,
      listOf("c.properties"),
    )
  }

  @Test
  fun projectExclusionsAffectUpToDateCheck() {
    publishArtifactCD()
    dependOnAndExcludeArtifactD()

    run(shadowJarTask)

    assertContains(
      outputShadowJar,
      listOf("a.properties", "a2.properties", "b.properties", "c.properties"),
    )
    assertDoesNotContain(
      outputShadowJar,
      listOf("d.properties"),
    )

    val replaced = buildScript.readText()
      .replace("exclude(dependency('shadow:d:1.0'))", "exclude 'a.properties'")
    buildScript.writeText(replaced)

    val result = run(shadowJarTask)

    assertThat(result.task(":shadowJar")).isNotNull()
      .transform { it.outcome }.isEqualTo(TaskOutcome.SUCCESS)
    assertContains(
      outputShadowJar,
      listOf("a2.properties", "b.properties", "c.properties", "d.properties"),
    )
    assertDoesNotContain(
      outputShadowJar,
      listOf("a.properties"),
    )
  }

  @Test
  fun includeDependencyAndExcludeOthers() {
    publishArtifactCD()
    buildScript.appendText(System.lineSeparator())
    buildScript.appendText(
      """
        dependencies {
          implementation 'shadow:d:1.0'
        }
        $shadowJar {
          dependencies {
            include(dependency('shadow:d:1.0'))
          }
        }
      """.trimIndent(),
    )
    path("src/main/java/shadow/Passed.java").writeText(
      """
        package shadow;
        public class Passed {}
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertContains(
      outputShadowJar,
      listOf("d.properties", "shadow/Passed.class"),
    )
    assertDoesNotContain(
      outputShadowJar,
      listOf("a.properties", "a2.properties", "b.properties", "c.properties"),
    )
  }

  @Test
  fun filterProjectDependencies() {
    writeClientAndServerModules(
      serverShadowBlock = """
        dependencies {
          exclude(project(':client'))
        }
      """.trimIndent(),
    )

    val serverOutput = path("server/build/libs/server-1.0-all.jar")
    run(":server:$shadowJarTask")

    assertThat(serverOutput).exists()
    assertDoesNotContain(
      serverOutput,
      listOf("client/Client.class"),
    )
    assertContains(
      serverOutput,
      listOf("server/Server.class", "junit/framework/Test.class"),
    )
  }

  @Test
  fun excludeATransitiveProjectDependency() {
    writeClientAndServerModules(
      serverShadowBlock = """
        dependencies {
          exclude(dependency { it.moduleGroup == 'junit' })
        }
      """.trimIndent(),
    )

    val serverOutput = path("server/build/libs/server-1.0-all.jar")
    run(":server:$shadowJarTask")

    assertThat(serverOutput).exists()
    assertDoesNotContain(
      serverOutput,
      listOf("junit/framework/Test.class"),
    )
    assertContains(
      serverOutput,
      listOf("client/Client.class", "server/Server.class"),
    )
  }

  @Test
  fun verifyExcludePrecedenceOverInclude() {
    buildScript.appendText(System.lineSeparator())
    buildScript.appendText(
      """
        $shadowJar {
          include '*.jar'
          include '*.properties'
          exclude 'a2.properties'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertContains(
      outputShadowJar,
      listOf("a.properties", "b.properties"),
    )
    assertDoesNotContain(
      outputShadowJar,
      listOf("a2.properties"),
    )
  }

  @Test
  fun handleExcludeWithCircularDependency() {
    publishArtifactCD()
    dependOnAndExcludeArtifactD()

    run(shadowJarTask)

    assertContains(
      outputShadowJar,
      listOf("a.properties", "a2.properties", "b.properties", "c.properties"),
    )
    assertDoesNotContain(
      outputShadowJar,
      listOf("d.properties"),
    )
  }

  private fun dependOnAndExcludeArtifactD() {
    buildScript.appendText(System.lineSeparator())
    buildScript.appendText(
      """
        dependencies {
          implementation 'shadow:d:1.0'
        }
        $shadowJar {
          dependencies {
            exclude(dependency('shadow:d:1.0'))
          }
        }
      """.trimIndent(),
    )
  }

  private fun writeClientAndServerModules(
    serverShadowBlock: String = "",
  ) {
    settingsScript.appendText(System.lineSeparator())
    settingsScript.appendText(
      """
        include 'client', 'server'
      """.trimIndent(),
    )
    buildScript.writeText("")

    path("client/src/main/java/client/Client.java").writeText(
      """
        package client;
        public class Client {}
      """.trimIndent(),
    )
    path("client/build.gradle").writeText(
      """
        ${getProjectBuildScript("java", versionInfo = "version = '1.0'")}
        dependencies { implementation 'junit:junit:3.8.2' }
      """.trimIndent(),
    )

    path("server/src/main/java/server/Server.java").writeText(
      """
        package server;
        import client.Client;
        public class Server {}
      """.trimIndent(),
    )
    path("server/build.gradle").writeText(
      """
        ${getProjectBuildScript("java", versionInfo = "version = '1.0'")}
        dependencies {
          implementation project(':client')
        }
        $shadowJar {
          $serverShadowBlock
        }
      """.trimIndent(),
    )
  }
}
