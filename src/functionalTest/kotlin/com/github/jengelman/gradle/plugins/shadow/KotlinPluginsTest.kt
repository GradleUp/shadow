package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.testkit.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getMainAttr
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.JvmLang
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class KotlinPluginsTest : BasePluginTest() {
  @BeforeEach
  override fun beforeEach() {
    super.beforeEach()
    projectScript.writeText(getDefaultProjectBuildScript(plugin = "org.jetbrains.kotlin.multiplatform"))
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun compatKotlinJvmPlugin(excludeStdlib: Boolean) {
    val stdlib = compileOnlyStdlib(excludeStdlib)

    projectScript.writeText(
      """
        ${getDefaultProjectBuildScript(plugin = "org.jetbrains.kotlin.jvm")}
        dependencies {
          implementation 'junit:junit:3.8.2'
          $stdlib
        }
      """.trimIndent(),
    )
    val mainClassEntry = writeClass(withImports = true, jvmLang = JvmLang.Kotlin)

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      val entries = arrayOf(
        "my/",
        "META-INF/my.kotlin_module",
        mainClassEntry,
        *junitEntries,
        *manifestEntries,
      )
      if (excludeStdlib) {
        containsOnly(*entries)
      } else {
        containsAtLeast(*entries)
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun compatKmpJvmTarget(excludeStdlib: Boolean) {
    val stdlib = compileOnlyStdlib(excludeStdlib)

    val mainClassEntry = writeClass(sourceSet = "jvmMain", jvmLang = JvmLang.Kotlin)
    projectScript.appendText(
      """
        kotlin {
          jvm()
          sourceSets {
            commonMain {
              dependencies {
                implementation 'my:b:1.0'
                $stdlib
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

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      val entries = arrayOf(
        "my/",
        "META-INF/my.kotlin_module",
        mainClassEntry,
        *entriesInAB,
        *manifestEntries,
      )
      if (excludeStdlib) {
        containsOnly(*entries)
      } else {
        containsAtLeast(*entries)
      }
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/1377",
  )
  @Test
  fun compatKmpForOtherNamedJvmTarget() {
    val jvmTargetName = "newJvm"
    val jvmTargetMain = "${jvmTargetName}Main"
    val stdlib = compileOnlyStdlib(true)
    val mainClassEntry = writeClass(sourceSet = jvmTargetMain, jvmLang = JvmLang.Kotlin)
    projectScript.appendText(
      """
        kotlin {
          jvm('$jvmTargetName')
          sourceSets {
            commonMain {
              dependencies {
                implementation 'my:b:1.0'
                $stdlib
              }
            }
            $jvmTargetMain {
              dependencies {
                implementation 'my:a:1.0'
              }
            }
          }
        }
      """.trimIndent(),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      val entries = arrayOf(
        "my/",
        "META-INF/my.kotlin_module",
        mainClassEntry,
        *entriesInAB,
        *manifestEntries,
      )
      containsAtLeast(*entries)
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/1377",
  )
  @Test
  fun doNotCreateJvmTargetEagerly() {
    projectScript.appendText(
      """
        kotlin {
          mingwX64()
        }
      """.trimIndent(),
    )

    val result = runWithFailure(shadowJarPath)

    assertThat(result.output).contains(
      "Cannot locate tasks that match ':shadowJar' as task 'shadowJar' not found in root project",
    )
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun setMainClassAttributeFromMainRun(useShadowAttr: Boolean) {
    val mainClassName = "my.Main"
    val main2ClassName = "my.Main2"
    val mainAttr = if (useShadowAttr) "attributes '$mainClassAttributeKey': '$main2ClassName'" else ""
    projectScript.appendText(
      """
        kotlin {
          jvm().mainRun {
            it.mainClass.set('$mainClassName')
          }
        }
        $shadowJarTask {
          manifest {
            $mainAttr
          }
        }
      """.trimIndent(),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getMainAttr(mainClassAttributeKey).isEqualTo(if (useShadowAttr) main2ClassName else mainClassName)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun setManifestAttrsFromJvmTargetJar(useShadowAttr: Boolean) {
    val mainClassName = "my.Main"
    val main2ClassName = "my.Main2"
    val mainAttr = if (useShadowAttr) "attributes '$mainClassAttributeKey': '$main2ClassName'" else ""
    projectScript.appendText(
      """
        kotlin {
          jvm()
        }
        tasks.named('jvmJar', Jar) {
          manifest {
            attributes '$mainClassAttributeKey': '$mainClassName'
          }
        }
        $shadowJarTask {
          manifest {
            $mainAttr
          }
        }
      """.trimIndent(),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getMainAttr(mainClassAttributeKey).isEqualTo(if (useShadowAttr) main2ClassName else mainClassName)
    }
  }

  @Test
  fun registerShadowJarForFirstJvmTarget() {
    val jvmTargetName = "newJvm"
    projectScript.appendText(
      """
        kotlin {
          jvm() // Default JVM target.
          jvm('$jvmTargetName')
          sourceSets {
            commonMain {
              dependencies {
                implementation 'my:a:1.0'
              }
            }
            jvmMain {
              dependencies {
                implementation 'my:b:1.0'
              }
            }
            ${jvmTargetName}Main {
              dependencies {
                implementation 'my:c:1.0'
              }
            }
          }
        }
      """.trimIndent(),
    )

    val result = runWithFailure(shadowJarPath, infoArgument)

    assertThat(result.output).contains(
      "$SHADOW_JAR_TASK_NAME task already exists, skipping configuration for target: $jvmTargetName", // Logged from Shadow.
      "Declaring multiple Kotlin Targets of the same type is not supported.", // Thrown from KGP.
    )
  }

  private fun compileOnlyStdlib(exclude: Boolean): String {
    return if (exclude) {
      // Disable the stdlib dependency added via `implementation`.
      path("gradle.properties").writeText("kotlin.stdlib.default.dependency=false")
      "compileOnly 'org.jetbrains.kotlin:kotlin-stdlib'"
    } else {
      ""
    }
  }
}
