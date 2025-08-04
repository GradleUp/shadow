package com.github.jengelman.gradle.plugins.shadow

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.DISTRIBUTION_NAME
import com.github.jengelman.gradle.plugins.shadow.internal.classPathAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import com.github.jengelman.gradle.plugins.shadow.util.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import com.github.jengelman.gradle.plugins.shadow.util.isWindows
import com.github.jengelman.gradle.plugins.shadow.util.runProcess
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.appendText
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

@ExperimentalPathApi
class ApplicationPluginTest : BasePluginTest() {
  private lateinit var mainClass: String

  @Test
  fun integrationWithApplicationPluginAndJavaToolchains() {
    prepare(
      mainClassWithImports = true,
      dependenciesBlock = "implementation 'junit:junit:3.8.2'",
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

    val result = run(runShadowTask)

    assertThat(result.output).contains(
      "Running application with JDK 17",
      "Hello, World! (foo) from Main",
      "Refs: junit.framework.Test",
    )
  }

  @Test
  fun installShadowOutputs() {
    prepare(
      mainClassWithImports = true,
      dependenciesBlock = "implementation 'junit:junit:3.8.2'",
      applicationBlock = "applicationDefaultJvmArgs = ['--add-opens=java.base/java.lang=ALL-UNNAMED']",
    )

    run(installShadowDistTask)

    val installPath = path("build/install/")
    assertThat(installPath.walkEntries()).containsOnly(
      "myapp-shadow/bin/myapp",
      "myapp-shadow/bin/myapp.bat",
      "myapp-shadow/lib/myapp-1.0-all.jar",
    )

    commonAssertions(
      jarPath("myapp-shadow/lib/myapp-1.0-all.jar", installPath),
      entriesContained = arrayOf(mainClass, *junitEntries),
    )

    val unixScript = path("myapp-shadow/bin/myapp", installPath)
    val winScript = path("myapp-shadow/bin/myapp.bat", installPath)

    assertThat(unixScript.readText()).contains(
      "CLASSPATH=\$APP_HOME/lib/myapp-1.0-all.jar",
      "exec \"\$JAVACMD\" \"$@\"",
      "DEFAULT_JVM_OPTS='\"--add-opens=java.base/java.lang=ALL-UNNAMED\"'",
    )
    assertThat(winScript.readText()).contains(
      "set CLASSPATH=%APP_HOME%\\lib\\myapp-1.0-all.jar",
      "set DEFAULT_JVM_OPTS=\"--add-opens=java.base/java.lang=ALL-UNNAMED\"",
    )

    val runningOutput = if (isWindows) {
      runProcess(winScript.toString(), "bar")
    } else {
      runProcess(unixScript.toString(), "bar")
    }
    assertThat(runningOutput).contains(
      "Hello, World! (bar) from Main",
      "Refs: junit.framework.Test",
    )
  }

  @Test
  fun installShadowDoesNotExecuteDependentShadowTask() {
    prepare()

    run(installShadowDistTask)

    commonAssertions(jarPath("build/install/myapp-shadow/lib/myapp-1.0-all.jar"))
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/613",
  )
  @Test
  fun canOverrideMainClassAttrInManifestBlock() {
    val main2ClassEntry = writeClass(className = "Main2")
    prepare(
      projectBlock = """
        shadowJar {
          manifest {
            attributes '$mainClassAttributeKey': 'my.Main2'
          }
        }
      """.trimIndent(),
    )
    val assertions = { output: String, arg: String ->
      assertThat(output).all {
        // Prefer main class from `application.main` over the one in manifest attributes.
        contains("Hello, World! ($arg) from Main")
        doesNotContain("Hello, World! ($arg) from Main2")
      }
    }

    assertions(run(runShadowTask, shadowJarTask).output, "foo")
    commonAssertions(
      jarPath("build/libs/myapp-1.0-all.jar"),
      entriesContained = entriesInA + arrayOf(mainClass, main2ClassEntry),
      mainClassAttr = "my.Main2",
    )

    projectScriptPath.appendText(
      """
        run {
          args 'bar'
        }
      """.trimIndent(),
    )
    assertions(run(":run").output, "bar")
  }

  @Test
  fun canAddExtraFilesIntoDistribution() {
    path("extra/echo.sh").writeText("echo 'Hello, World!'")
    path("some/dir/hello.txt").writeText("'Hello, World!'")
    prepare(
      projectBlock = """
        distributions.named('$DISTRIBUTION_NAME') {
          contents.from('extra/echo.sh') {
            into 'extra'
          }
        }
      """.trimIndent(),
      applicationBlock = """
        applicationDistribution.from('some/dir') {
          include '*.txt'
        }
      """.trimIndent(),
    )

    run(shadowDistZipTask)

    val zipPath = path("build/distributions/myapp-shadow-1.0.zip")
    ZipFile(zipPath.toFile()).use { zip ->
      val entries = zip.entries().toList().filter { !it.isDirectory }.map { it.name }
      assertThat(entries).containsOnly(
        "myapp-shadow-1.0/bin/myapp",
        "myapp-shadow-1.0/bin/myapp.bat",
        "myapp-shadow-1.0/lib/myapp-1.0-all.jar",
        "myapp-shadow-1.0/extra/echo.sh",
        "myapp-shadow-1.0/hello.txt",
      )
      assertThat(zip.getContent("myapp-shadow-1.0/extra/echo.sh"))
        .isEqualTo("echo 'Hello, World!'")
      assertThat(zip.getContent("myapp-shadow-1.0/hello.txt"))
        .isEqualTo("'Hello, World!'")
    }
  }

  @Test
  fun canIncludeSrcDistByDefault() {
    path("src/dist/echo.sh").writeText("echo 'Hello, World!'")
    prepare()

    run(shadowDistZipTask)

    val zipPath = path("build/distributions/myapp-shadow-1.0.zip")
    ZipFile(zipPath.toFile()).use { zip ->
      val entries = zip.entries().toList().filter { !it.isDirectory }.map { it.name }
      assertThat(entries).containsOnly(
        "myapp-shadow-1.0/bin/myapp",
        "myapp-shadow-1.0/bin/myapp.bat",
        "myapp-shadow-1.0/lib/myapp-1.0-all.jar",
        "myapp-shadow-1.0/echo.sh",
      )
      assertThat(zip.getContent("myapp-shadow-1.0/echo.sh"))
        .isEqualTo("echo 'Hello, World!'")
    }
  }

  private fun prepare(
    mainClassWithImports: Boolean = false,
    projectBlock: String = "",
    applicationBlock: String = "",
    settingsBlock: String = "",
    dependenciesBlock: String = "implementation 'my:a:1.0'",
    runShadowBlock: String = "",
  ) {
    mainClass = writeClass(withImports = mainClassWithImports)
    projectScriptPath.appendText(
      """
        apply plugin: 'application'
        $projectBlock
        application {
          mainClass = 'my.Main'
          $applicationBlock
        }
        dependencies {
          $dependenciesBlock
        }
        $runShadow {
          args 'foo'
          $runShadowBlock
        }
      """.trimIndent() + lineSeparator,
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
    entriesContained: Array<String> = entriesInA + mainClass,
    mainClassAttr: String = "my.Main",
    classPathAttr: String? = null,
  ) {
    assertThat(jarPath).useAll {
      containsAtLeast(*entriesContained)
      getMainAttr(mainClassAttributeKey).isEqualTo(mainClassAttr)
      getMainAttr(classPathAttributeKey).isEqualTo(classPathAttr)
    }
  }

  private companion object {
    fun Path.walkEntries(): Sequence<String> {
      return walk()
        .filter { it.isRegularFile() }
        .map { it.relativeTo(this) }
        .map { it.toString().replace(FileSystems.getDefault().separator, "/") }
    }
  }
}
