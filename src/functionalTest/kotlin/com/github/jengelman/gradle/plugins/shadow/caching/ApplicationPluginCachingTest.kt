package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class ApplicationPluginCachingTest : BaseCachingTest() {
  override val taskPath: String = runShadowTask

  @Test
  fun runShadowIsNotCacheable() {
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
  fun applicationChanged() {
    val mainClassName = "my.Main"
    val main2ClassName = "my.Main2"

    projectScriptPath.appendText(
      """
        apply plugin: 'application'
        application {
          mainClass = '$mainClassName'
        }
      """.trimIndent(),
    )

    assertCompositeExecutions {
      getMainAttr(mainClassAttributeKey).isEqualTo(mainClassName)
    }

    val replaced = projectScriptPath.readText().replace(mainClassName, main2ClassName)
    projectScriptPath.writeText(replaced)

    assertCompositeExecutions {
      getMainAttr(mainClassAttributeKey).isEqualTo(main2ClassName)
    }
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
