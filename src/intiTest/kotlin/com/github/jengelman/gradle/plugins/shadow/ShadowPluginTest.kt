package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.exists
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import kotlin.io.path.appendText
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIf

class ShadowPluginTest : BasePluginTest() {

  @Test
  fun applyPlugin() {
    val projectName = "my-shadow"
    val version = "1.0.0"

    val project = ProjectBuilder.builder().withName(projectName).build().also {
      it.version = version
    }
    project.plugins.apply(ShadowPlugin::class.java)

    assertThat(project.plugins.hasPlugin(ShadowPlugin::class.java)).isTrue()
    assertThat(project.plugins.hasPlugin(LegacyShadowPlugin::class.java)).isTrue()
    assertThat(project.tasks.findByName("shadowJar")).isEqualTo(null)

    project.plugins.apply(JavaPlugin::class.java)
    val shadow = project.tasks.findByName(SHADOW_JAR_TASK_NAME) as ShadowJar
    val shadowConfig = project.configurations.findByName(ShadowBasePlugin.CONFIGURATION_NAME)

    assertThat(shadow).isNotNull()
    assertThat(shadow.archiveBaseName.get()).isEqualTo(projectName)
    assertThat(shadow.destinationDirectory.get().asFile)
      .isEqualTo(project.layout.buildDirectory.dir("libs").get().asFile)
    assertThat(shadow.archiveVersion.get()).isEqualTo(version)
    assertThat(shadow.archiveClassifier.get()).isEqualTo("all")
    assertThat(shadow.archiveExtension.get()).isEqualTo("jar")
    assertThat(shadowConfig).isNotNull().transform {
      it.artifacts.files.contains(shadow.archiveFile.get().asFile)
    }.isTrue()
  }

  @Test
  @DisabledIf(
    value = "atLeastJava21",
    disabledReason = "Gradle 8.3 doesn't support Java 21.",
  )
  fun compatibleWithMinGradleVersion() {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJar {
          mergeServiceFiles()
        }
      """.trimIndent(),
    )

    run(shadowJarTask) {
      it.withGradleVersion("8.3")
    }

    assertThat(outputShadowJar).exists()
  }

  @Test
  fun incompatibleWithLowerMinGradleVersion() {
    runWithFailure("shadowJar") {
      it.withGradleVersion("7.0")
    }
  }

  @Test
  fun shadowCopy() {
    val artifact = requireNotNull(this::class.java.classLoader.getResource("test-artifact-1.0-SNAPSHOT.jar"))
      .toURI().toPath()
    val project = requireNotNull(this::class.java.classLoader.getResource("test-project-1.0-SNAPSHOT.jar"))
      .toURI().toPath()

    projectScriptPath.appendText(
      """
        $shadowJar {
          from(file('$artifact'))
          from(file('$project'))
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()
  }

  @Test
  fun includeProjectSources() {
    path("src/main/java/shadow/Passed.java").writeText(
      """
        package shadow;
        public class Passed {}
      """.trimIndent(),
    )

    projectScriptPath.appendText(
      """
        dependencies {
         implementation 'junit:junit:3.8.2'
        }
        $shadowJar {
          archiveBaseName = 'shadow'
          archiveClassifier = null
          archiveVersion = null
        }
      """.trimIndent(),
    )

    run(shadowJarTask)
    val outputShadowJar = path("build/libs/shadow-1.0.jar")

    assertThat(outputShadowJar).exists()
    assertContains(
      outputShadowJar,
      listOf("shadow/Passed.class", "junit/framework/Test.class"),
    )
    assertDoesNotContain(
      outputShadowJar,
      listOf("/"),
    )
  }

  @Test
  fun includeProjectDependencies() {
    writeClientAndServerModules()

    val serverOutput = path("server/build/libs/server-1.0-all.jar")

    run(":server:$shadowJarTask")

    assertThat(serverOutput).exists()
    assertContains(
      serverOutput,
      listOf("client/Client.class", "server/Server.class", "junit/framework/Test.class"),
    )
  }

  @Test
  fun minimizeByKeepingOnlyTransitiveDependencies() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize()
      """.trimIndent(),
    )
    path("server/src/main/java/server/Server.java").writeText(
      """
        package server;
        import client.Client;
        public class Server {
            private final String client = Client.class.getName();
        }
      """.trimIndent(),
    )

    val serverOutput = path("server/build/libs/server-1.0-all.jar")

    run(":server:$shadowJarTask")

    assertThat(serverOutput).exists()
    assertContains(
      serverOutput,
      listOf("client/Client.class", "server/Server.class"),
    )
    assertDoesNotContain(
      serverOutput,
      listOf("junit/framework/Test.class"),
    )
  }

  @Test
  fun excludeDependencyFromMinimize() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize {
          exclude(dependency('junit:junit:.*'))
        }
      """.trimIndent(),
    )

    val serverOutput = path("server/build/libs/server-1.0-all.jar")

    run(":server:$shadowJarTask")

    assertThat(serverOutput).exists()
    assertContains(
      serverOutput,
      listOf("server/Server.class", "junit/framework/Test.class"),
    )
    assertDoesNotContain(
      serverOutput,
      listOf("client/Client.class"),
    )
  }

  @Test
  fun excludeProjectFromMinimize() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize {
          exclude(project(':client'))
        }
      """.trimIndent(),
    )

    val serverOutput = path("server/build/libs/server-1.0-all.jar")

    run(":server:$shadowJarTask")

    assertThat(serverOutput).exists()
    assertContains(
      serverOutput,
      listOf("client/Client.class", "server/Server.class"),
    )
  }

  @Test
  fun excludeProjectFromMinimizeShallNotExcludeTransitiveDependenciesThatAreUsedInSubproject() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize {
          exclude(project(':client'))
        }
      """.trimIndent(),
    )

    val serverOutput = path("server/build/libs/server-1.0-all.jar")

    run(":server:$shadowJarTask")

    assertThat(serverOutput).exists()
    assertContains(
      serverOutput,
      listOf("client/Client.class", "server/Server.class", "junit/framework/TestCase.class"),
    )
  }

  @Test
  fun excludeProjectFromMinimizeShallNotExcludeTransitiveDependenciesFromSubprojectThatAreNotUsed() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize {
          exclude(project(':client'))
        }
      """.trimIndent(),
    )

    val serverOutput = path("server/build/libs/server-1.0-all.jar")

    run(":server:$shadowJarTask")

    assertContains(
      serverOutput,
      listOf("client/Client.class", "server/Server.class", "junit/framework/TestCase.class"),
    )
  }

  @Test
  fun useMinimizeWithDependenciesWithApiScope() {
    writeApiLibAndImplModules()

    run(":impl:$shadowJarTask")
    val implOutput = path("impl/build/libs/impl-all.jar")

    assertThat(implOutput).exists()
    assertContains(
      implOutput,
      listOf("impl/SimpleEntity.class", "api/Entity.class", "api/UnusedEntity.class", "lib/LibEntity.class"),
    )
    assertDoesNotContain(
      implOutput,
      listOf("junit/framework/Test.class", "lib/UnusedLibEntity.class"),
    )
  }

  @Test
  fun useMinimizeWithTransitiveDependenciesWithApiScope() {
    writeApiLibAndImplModules()

    run(":impl:$shadowJarTask")
    val implOutput = path("impl/build/libs/impl-all.jar")

    assertThat(implOutput).exists()
    assertContains(
      implOutput,
      listOf(
        "impl/SimpleEntity.class",
        "api/Entity.class",
        "api/UnusedEntity.class",
        "lib/LibEntity.class",
        "lib/UnusedLibEntity.class",
      ),
    )
  }

  @Test
  fun dependOnProjectShadowJar() {
    writeClientAndServerModules()
    path("server/build.gradle").writeText(
      """
        plugins {
          id 'java'
        }
        dependencies {
          implementation project(path: ':client', configuration: 'shadow')
        }
      """.trimIndent(),
    )

    val serverOutput = path("server/build/libs/server.jar")

    run(":server:jar")

    assertThat(serverOutput).exists()
    assertContains(
      serverOutput,
      listOf("server/Server.class"),
    )
    assertDoesNotContain(
      serverOutput,
      listOf("client/Client.class", "junit/framework/Test.class", "client/junit/framework/Test.class"),
    )
  }

  private companion object {
    @JvmStatic
    fun atLeastJava21(): Boolean = javaVersion >= 21
  }
}
