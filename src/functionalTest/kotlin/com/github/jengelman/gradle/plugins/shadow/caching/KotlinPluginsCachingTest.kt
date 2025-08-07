package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class KotlinPluginsCachingTest : BaseCachingTest() {
  @Test
  fun kotlinMainRunChanged() {
    val mainClassName = "my.Main"
    val main2ClassName = "my.Main2"

    val projectBuildScript = getDefaultProjectBuildScript(
      plugin = "org.jetbrains.kotlin.multiplatform",
      withGroup = true,
      withVersion = true,
    )
    projectScriptPath.writeText(
      """
        $projectBuildScript
        kotlin {
          jvm().mainRun {
            it.mainClass.set('$mainClassName')
          }
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
}
