package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.util.JvmLang
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class KotlinPluginsTest : BasePluginTest() {
  @BeforeEach
  override fun setup() {
    super.setup()
    val projectBuildScript = getDefaultProjectBuildScript(
      plugin = "org.jetbrains.kotlin.multiplatform",
      withGroup = true,
      withVersion = true,
    )
    projectScriptPath.writeText(projectBuildScript)
  }

  @Test
  fun compatKotlinJvmPlugin() {
    projectScriptPath.writeText(
      """
        ${getDefaultProjectBuildScript(plugin = "org.jetbrains.kotlin.jvm", withGroup = true, withVersion = true)}
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent(),
    )
    val mainClassEntry = writeClass(withImports = true, jvmLang = JvmLang.Kotlin)

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        mainClassEntry,
        *junitEntries,
      )
    }
  }

  @Test
  fun compatKmpJvmTarget() {
    val mainClassEntry = writeClass(sourceSet = "jvmMain", jvmLang = JvmLang.Kotlin)
    projectScriptPath.appendText(
      """
        kotlin {
          jvm()
          sourceSets {
            commonMain {
              dependencies {
                implementation 'my:b:1.0'
              }
            }
            jvmMain {
              dependencies {
                implementation 'my:a:1.0'
              }
            }
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        mainClassEntry,
        *entriesInAB,
      )
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun canSetMainClassAttribute(useShadowAttr: Boolean) {
    val mainClassEntry = writeClass(sourceSet = "jvmMain", jvmLang = JvmLang.Kotlin)
    val main2ClassEntry = writeClass(sourceSet = "jvmMain", jvmLang = JvmLang.Kotlin, className = "Main2")
    val mainClassName = "my.Main"
    val main2ClassName = "my.Main2"
    val mainAttr = if (useShadowAttr) "attributes '$mainClassAttributeKey': '$main2ClassName'" else ""
    projectScriptPath.appendText(
      """
        kotlin {
          jvm().mainRun {
            it.mainClass.set('$mainClassName')
          }
        }
        $shadowJar {
          manifest {
            $mainAttr
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        mainClassEntry,
        main2ClassEntry,
      )
      if (useShadowAttr) {
        getMainAttr(mainClassAttributeKey).isEqualTo(main2ClassName)
      } else {
        getMainAttr("Main-Class").isEqualTo(mainClassName)
      }
    }
  }
}
