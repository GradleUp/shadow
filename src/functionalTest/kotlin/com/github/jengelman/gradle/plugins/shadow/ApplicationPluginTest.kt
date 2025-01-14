package com.github.jengelman.gradle.plugins.shadow

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.exists
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_INSTALL_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_RUN_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import com.github.jengelman.gradle.plugins.shadow.util.getStream
import java.util.zip.ZipFile
import kotlin.io.path.appendText
import kotlin.io.path.outputStream
import kotlin.io.path.readText
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
      "TestApp: Hello World! (foo)",
    )

    commonAssertions(jarPath("build/install/myapp-shadow/lib/myapp-1.0-all.jar"))

    assertThat(path("build/install/myapp-shadow/bin/myapp")).all {
      exists()
      transform { it.readText() }.contains(
        "CLASSPATH=\$APP_HOME/lib/myapp-1.0-all.jar",
        "-jar \"\\\"\$CLASSPATH\\\"\" \"\$APP_ARGS\"",
        "exec \"\$JAVACMD\" \"\$@\"",
      )
    }
    assertThat(path("build/install/myapp-shadow/bin/myapp.bat")).all {
      exists()
      transform { it.readText() }.contains(
        "set CLASSPATH=%APP_HOME%\\lib\\myapp-1.0-all.jar",
      )
    }
  }

  @Test
  fun shadowApplicationDistributionsShouldUseShadowJar() {
    prepare(
      dependenciesBlock = "shadow 'shadow:a:1.0'",
    )

    run("shadowDistZip")

    ZipFile(path("build/distributions/myapp-shadow-1.0.zip").toFile()).use { zip ->
      val fileEntries = zip.entries().toList().map { it.name }.filter { !it.endsWith("/") }
      assertThat(fileEntries).containsOnly(
        "myapp-shadow-1.0/bin/myapp",
        "myapp-shadow-1.0/bin/myapp.bat",
        "myapp-shadow-1.0/lib/myapp-1.0-all.jar",
        "myapp-shadow-1.0/lib/a-1.0.jar",
      )

      val extractedJar = path("extracted/myapp-1.0-all.jar")
      zip.getStream("myapp-shadow-1.0/lib/myapp-1.0-all.jar")
        .use { it.copyTo(extractedJar.outputStream()) }
      commonAssertions(JarPath(extractedJar), entriesContained = arrayOf("myapp/Main.class"))

      assertThat(zip.getContent("myapp-shadow-1.0/bin/myapp")).contains(
        "CLASSPATH=\$APP_HOME/lib/myapp-1.0-all.jar",
        "-jar \"\\\"\$CLASSPATH\\\"\" \"\$APP_ARGS\"",
        "exec \"\$JAVACMD\" \"\$@\"",
      )
      assertThat(zip.getContent("myapp-shadow-1.0/bin/myapp.bat")).contains(
        "set CLASSPATH=%APP_HOME%\\lib\\myapp-1.0-all.jar",
      )
    }
  }

  @Test
  fun installShadowDoesNotExecuteDependentShadowTask() {
    prepare()

    run(SHADOW_INSTALL_TASK_NAME)

    commonAssertions(jarPath("build/install/myapp-shadow/lib/myapp-1.0-all.jar"))
  }

  private fun prepare(
    projectBlock: String = "",
    settingsBlock: String = "",
    dependenciesBlock: String = "implementation 'shadow:a:1.0'",
    runShadowBlock: String = "",
  ) {
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
    entriesContained: Array<String> = arrayOf("a.properties", "a2.properties", "myapp/Main.class"),
  ) {
    assertThat(jarPath).useAll {
      containsEntries(*entriesContained)
      getMainAttr("Main-Class").isEqualTo("myapp.Main")
    }
  }

  private companion object {
    val runShadow = "tasks.named('$SHADOW_RUN_TASK_NAME')".trim()
  }
}
