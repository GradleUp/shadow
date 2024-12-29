package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAtLeast
import assertk.assertions.exists
import assertk.assertions.isEqualTo
import java.util.jar.JarFile
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.apache.tools.zip.ZipFile
import org.junit.jupiter.api.Test

class ApplicationTest : BasePluginTest() {

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
          id('org.gradle.toolchains.foojay-resolver-convention') version '0.7.0'
        }
      """.trimIndent(),
      runShadowBlock = """
        doFirst {
          logger.lifecycle("Running application with JDK ${'$'}{it.javaLauncher.get().metadata.languageVersion.asInt()}")
        }
      """.trimIndent(),
    )

    val result = run(runShadowTask)

    assertThat(result.output).contains("Running application with JDK 17")
    assertThat(result.output).contains("TestApp: Hello World! (foo)")

    val installedJar = path("build/install/myapp-shadow/lib/myapp-1.0-all.jar")
    assertThat(installedJar).exists()

    assertContains(installedJar, listOf("a.properties", "a2.properties", "myapp/Main.class"))

    val jarFile = JarFile(installedJar.toFile())
    assertThat(jarFile.manifest.mainAttributes.getValue("Main-Class"))
      .isEqualTo("myapp.Main")

    path("build/install/myapp-shadow/bin/myapp").let { startScript ->
      assertThat(startScript).exists()
      assertThat(startScript.readText()).contains("CLASSPATH=\$APP_HOME/lib/myapp-1.0-all.jar")
      assertThat(startScript.readText()).contains("-jar \"\\\"\$CLASSPATH\\\"\" \"\$APP_ARGS\"")
      assertThat(startScript.readText()).contains("exec \"\$JAVACMD\" \"\$@\"")
    }
  }

  @Test
  fun shadowApplicationDistributionsShouldUseShadowJar() {
    prepare(
      projectBlock = """
        dependencies {
           shadow 'shadow:a:1.0'
        }
      """.trimIndent(),
    )

    run("shadowDistZip")

    val zip = path("build/distributions/myapp-shadow-1.0.zip")
    assertThat(zip).exists()

    val entries = ZipFile(zip.toFile()).entries.toList().map { it.name }
    assertThat(entries).containsAtLeast(
      "myapp-shadow-1.0/lib/myapp-1.0-all.jar",
      "myapp-shadow-1.0/lib/a-1.0.jar",
    )
  }

  @Test
  fun installShadowDoesNotExecuteDependentShadowTask() {
    prepare()

    run(ShadowApplicationPlugin.SHADOW_INSTALL_TASK_NAME)

    assertThat(path("build/install/myapp-shadow/lib/myapp-1.0-all.jar")).exists()
  }

  private fun prepare(
    projectBlock: String = "",
    settingsBlock: String = "",
    runShadowBlock: String = "",
  ) {
    publishArtifactA()
    path("src/main/java/myapp/Main.java").appendText(
      """
        package myapp;
        public class Main {
          public static void main(String[] args) {
            System.out.println("TestApp: Hello World! (" + args[0] + ")");
          }
        }
      """.trimIndent(),
    )
    projectScriptPath.appendText(
      """
        apply plugin: 'application'
        $projectBlock
        application {
          mainClass = 'myapp.Main'
        }
        dependencies {
          implementation 'shadow:a:1.0'
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
}
