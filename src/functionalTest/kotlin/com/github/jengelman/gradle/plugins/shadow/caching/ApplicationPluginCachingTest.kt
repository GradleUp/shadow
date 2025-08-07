package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class ApplicationPluginCachingTest : BaseCachingTest() {
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
}
