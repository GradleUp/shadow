package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.github.jengelman.gradle.plugins.shadow.util.exists
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

    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'shadow:a:1.0'
          implementation 'shadow:b:1.0'
        }
      """.trimIndent() + System.lineSeparator(),
    )
  }

  @Test
  fun includeAllDependencies() {
    run(shadowJarTask)
    outputShadowJar.assertContains(
      listOf("a.properties", "a2.properties", "b.properties"),
    )
  }

  @Test
  fun excludeFiles() {
    projectScriptPath.appendText(
      """
        $shadowJar {
          exclude 'a2.properties'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    outputShadowJar.assertContains(
      listOf("a.properties", "b.properties"),
    )
    outputShadowJar.assertDoesNotContain(
      listOf("a2.properties"),
    )
  }

  @Test
  fun excludeDependency() {
    publishArtifactCD()
    dependOnAndExcludeArtifactD()

    run(shadowJarTask)

    commonAssertions()
  }

  @Test
  fun excludeDependencyUsingWildcardSyntax() {
    publishArtifactCD()
    projectScriptPath.appendText(
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

    commonAssertions()
  }

  @Test
  fun dependencyExclusionsAffectUpToDateCheck() {
    publishArtifactCD()
    dependOnAndExcludeArtifactD()

    run(shadowJarTask)

    commonAssertions()

    val replaced = projectScriptPath.readText()
      .replace("exclude(dependency('shadow:d:1.0'))", "exclude(dependency('shadow:c:1.0'))")
    projectScriptPath.writeText(replaced)
    val result = run(shadowJarTask)

    assertThat(result.task(":shadowJar")).isNotNull()
      .transform { it.outcome }.isEqualTo(TaskOutcome.SUCCESS)
    outputShadowJar.assertContains(
      listOf("a.properties", "a2.properties", "b.properties", "d.properties"),
    )
    outputShadowJar.assertDoesNotContain(
      listOf("c.properties"),
    )
  }

  @Test
  fun projectExclusionsAffectUpToDateCheck() {
    publishArtifactCD()
    dependOnAndExcludeArtifactD()

    run(shadowJarTask)

    commonAssertions()

    val replaced = projectScriptPath.readText()
      .replace("exclude(dependency('shadow:d:1.0'))", "exclude 'a.properties'")
    projectScriptPath.writeText(replaced)

    val result = run(shadowJarTask)

    assertThat(result.task(":shadowJar")).isNotNull()
      .transform { it.outcome }.isEqualTo(TaskOutcome.SUCCESS)
    outputShadowJar.assertContains(
      listOf("a2.properties", "b.properties", "c.properties", "d.properties"),
    )
    outputShadowJar.assertDoesNotContain(
      listOf("a.properties"),
    )
  }

  @Test
  fun includeDependencyAndExcludeOthers() {
    publishArtifactCD()
    projectScriptPath.appendText(
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

    outputShadowJar.assertContains(
      listOf("d.properties", "shadow/Passed.class"),
    )
    outputShadowJar.assertDoesNotContain(
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

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).exists()
    outputServerShadowJar.assertDoesNotContain(
      listOf("client/Client.class"),
    )
    outputServerShadowJar.assertContains(
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

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).exists()
    outputServerShadowJar.assertDoesNotContain(
      listOf("junit/framework/Test.class"),
    )
    outputServerShadowJar.assertContains(
      listOf("client/Client.class", "server/Server.class"),
    )
  }

  @Test
  fun verifyExcludePrecedenceOverInclude() {
    projectScriptPath.appendText(
      """
        $shadowJar {
          include '*.jar'
          include '*.properties'
          exclude 'a2.properties'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    outputShadowJar.assertContains(
      listOf("a.properties", "b.properties"),
    )
    outputShadowJar.assertDoesNotContain(
      listOf("a2.properties"),
    )
  }

  @Test
  fun handleExcludeWithCircularDependency() {
    publishArtifactCD(circular = true)
    dependOnAndExcludeArtifactD()

    run(shadowJarTask)

    commonAssertions()
  }

  private fun dependOnAndExcludeArtifactD() {
    projectScriptPath.appendText(
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

  private fun commonAssertions() {
    outputShadowJar.assertContains(
      listOf("a.properties", "a2.properties", "b.properties", "c.properties"),
    )
    outputShadowJar.assertDoesNotContain(
      listOf("d.properties"),
    )
  }
}
