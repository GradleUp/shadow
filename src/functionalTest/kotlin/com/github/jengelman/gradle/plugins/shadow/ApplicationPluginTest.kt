package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_INSTALL_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_RUN_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import java.util.zip.ZipFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class ApplicationPluginTest : BasePluginTest() {
  @Test
  fun integrationWithApplicationPluginAndJavaToolchains() {
    prepare(
      projectBlock = """
        java {
          toolchain.languageVersion = JavaLanguageVersion.of(17)
        }
      """.trimIndent(),
      settingsBlock = """
        plugins {
          id 'org.gradle.toolchains.foojay-resolver-convention'
        }
      """.trimIndent(),
      runShadowBlock = """
        doFirst {
          logger.lifecycle("Running application with JDK ${'$'}{it.javaLauncher.get().metadata.languageVersion.asInt()}")
        }
      """.trimIndent(),
    )

    val result = run(SHADOW_RUN_TASK_NAME)

    assertThat(result.output).contains(
      "Running application with JDK 17",
      "Hello, World! (foo) from Main",
    )

    commonAssertions(jarPath("build/install/myapp-shadow/lib/myapp-1.0-all.jar"))

    assertThat(path("build/install/myapp-shadow/bin/myapp").readText()).contains(
      "CLASSPATH=\$APP_HOME/lib/myapp-1.0-all.jar",
      "-jar \"\\\"\$CLASSPATH\\\"\" \"\$APP_ARGS\"",
      "exec \"\$JAVACMD\" \"\$@\"",
    )
    assertThat(path("build/install/myapp-shadow/bin/myapp.bat").readText()).contains(
      "set CLASSPATH=%APP_HOME%\\lib\\myapp-1.0-all.jar",
    )
  }

  @ExperimentalPathApi
  @Test
  fun shadowApplicationDistributionsShouldUseShadowJar() {
    prepare(
      dependenciesBlock = "shadow 'shadow:a:1.0'",
    )

    run("shadowDistZip")

    val zipPath = path("build/distributions/myapp-shadow-1.0.zip")
    val extractedPath = path("build/distributions/extracted/")

    // Unzip the whole file.
    ZipFile(zipPath.toFile()).use { zip ->
      zip.entries().toList().forEach { entry ->
        val entryDest = extractedPath.resolve(entry.name)
        if (entry.isDirectory) {
          entryDest.createDirectories()
        } else {
          zip.getInputStream(entry).use { it.copyTo(entryDest.outputStream()) }
        }
      }
    }

    val extractedEntries = extractedPath.walk().map { it.relativeTo(extractedPath).toString() }.toList()
    assertThat(extractedEntries).containsOnly(
      "myapp-shadow-1.0/bin/myapp",
      "myapp-shadow-1.0/bin/myapp.bat",
      "myapp-shadow-1.0/lib/myapp-1.0-all.jar",
      "myapp-shadow-1.0/lib/a-1.0.jar",
    )

    val extractedJarPath = jarPath("myapp-shadow-1.0/lib/a-1.0.jar", extractedPath)
    val extractedShadowJarPath = jarPath("myapp-shadow-1.0/lib/myapp-1.0-all.jar", extractedPath)
    assertThat(extractedJarPath).useAll {
      containsEntries("a.properties", "a2.properties")
    }
    commonAssertions(extractedShadowJarPath, entriesContained = arrayOf("shadow/Main.class"))

    assertThat(extractedPath.resolve("myapp-shadow-1.0/bin/myapp").readText()).contains(
      "CLASSPATH=\$APP_HOME/lib/myapp-1.0-all.jar",
      "-jar \"\\\"\$CLASSPATH\\\"\" \"\$APP_ARGS\"",
      "exec \"\$JAVACMD\" \"\$@\"",
    )
    assertThat(extractedPath.resolve("myapp-shadow-1.0/bin/myapp.bat").readText()).contains(
      "set CLASSPATH=%APP_HOME%\\lib\\myapp-1.0-all.jar",
    )
  }

  @Test
  fun installShadowDoesNotExecuteDependentShadowTask() {
    prepare()

    run(SHADOW_INSTALL_TASK_NAME)

    commonAssertions(jarPath("build/install/myapp-shadow/lib/myapp-1.0-all.jar"))
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/613",
  )
  @Test
  fun canOverrideMainClassAttrInManifestBlock() {
    writeMainClass(className = "Main2")
    prepare(
      projectBlock = """
        shadowJar {
          manifest {
            attributes 'Main-Class': 'shadow.Main2'
          }
        }
      """.trimIndent(),
    )

    val result = run(SHADOW_RUN_TASK_NAME)

    assertThat(result.output).contains(
      "Hello, World! (foo) from Main2",
    )
    commonAssertions(
      jarPath("build/install/myapp-shadow/lib/myapp-1.0-all.jar"),
      mainClassAttr = "shadow.Main2",
    )
  }

  private fun prepare(
    projectBlock: String = "",
    settingsBlock: String = "",
    dependenciesBlock: String = "implementation 'shadow:a:1.0'",
    runShadowBlock: String = "",
  ) {
    writeMainClass()
    projectScriptPath.appendText(
      """
        apply plugin: 'application'
        $projectBlock
        application {
          mainClass = 'shadow.Main'
        }
        dependencies {
          $dependenciesBlock
        }
        $runShadow {
          args 'foo'
          $runShadowBlock
        }
      """.trimIndent(),
    )
    settingsScriptPath.writeText(
      getDefaultSettingsBuildScript(
        startBlock = settingsBlock,
        endBlock = "rootProject.name = 'myapp'",
      ),
    )
  }

  private fun commonAssertions(
    jarPath: JarPath,
    entriesContained: Array<String> = arrayOf("a.properties", "a2.properties", "shadow/Main.class"),
    mainClassAttr: String = "shadow.Main",
  ) {
    assertThat(jarPath).useAll {
      containsEntries(*entriesContained)
      getMainAttr("Main-Class").isEqualTo(mainClassAttr)
    }
  }

  private companion object {
    val runShadow = "tasks.named('$SHADOW_RUN_TASK_NAME')".trim()
  }
}
