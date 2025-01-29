package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import assertk.assertions.contains
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_SCRIPTS_TASK_NAME
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class ApplicationCachingTest : BaseCachingTest() {
  override lateinit var taskPath: String

  @Test
  fun runShadowExecutedCorrectlyAfterShadowJarChanged() {
    taskPath = runShadowTask
    prepare()
    val resourcePath = path("src/main/resources/my/resource.txt")
    resourcePath.writeText(
      """
        Hello, World! %s from resource 1
      """.trimIndent(),
    )
    val assertions = { resName: String ->
      assertExecutionSuccess("Hello, World! foo from $resName")
      // `runTask` is not cacheable, so it's always executed.
      assertExecutionSuccess("Hello, World! foo from $resName")
    }

    path("src/main/java/my/App.java").writeText(
      """
        package my;
        public class App {
          public static void main(String[] args) throws Exception {
            if (args.length == 0) {
              throw new IllegalArgumentException("No arguments provided.");
            }
            var is = App.class.getResourceAsStream("resource.txt");
            String content = String.format(new String(is.readAllBytes()), (Object[]) args);
            System.out.println(content);
          }
        }
      """.trimIndent(),
    )
    assertions("resource 1")

    resourcePath.writeText(
      """
        Hello, World! %s from resource 2
      """.trimIndent(),
    )
    assertions("resource 2")
  }

  @Test
  fun startShadowScriptsExecutedCorrectlyAfterShadowJarChanged() {
    taskPath = ":$SHADOW_SCRIPTS_TASK_NAME"
    prepare()
    writeMainClass()
    val assertions = { classifier: String ->
      assertExecutionSuccess()
      assertThat(path("build/scriptsShadow/myapp").readText()).contains(
        "CLASSPATH=\$APP_HOME/lib/myapp-1.0-$classifier.jar",
      )
      assertThat(path("build/scriptsShadow/myapp.bat").readText()).contains(
        "set CLASSPATH=%APP_HOME%\\lib\\myapp-1.0-$classifier.jar",
      )
      // `startShadowScripts` is not cacheable, so it's always executed.
      assertExecutionSuccess()
    }

    assertions("all")

    projectScriptPath.appendText(
      """
        $shadowJar {
          archiveClassifier = 'foo'
        }
      """.trimIndent(),
    )

    assertions("foo")
  }

  private fun prepare(
    projectBlock: String = "",
    applicationBlock: String = "",
    settingsBlock: String = "",
    dependenciesBlock: String = "",
    runShadowBlock: String = "",
  ) {
    projectScriptPath.appendText(
      """
        apply plugin: 'application'
        $projectBlock
        application {
          mainClass = 'my.App'
          $applicationBlock
        }
        dependencies {
          $dependenciesBlock
        }
        $runShadow {
          args 'foo'
          $runShadowBlock
        }
      """.trimIndent() + System.lineSeparator(),
    )
    settingsScriptPath.writeText(
      getDefaultSettingsBuildScript(
        startBlock = settingsBlock,
        endBlock = "rootProject.name = 'myapp'",
      ),
    )
  }
}
