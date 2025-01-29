package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_INSTALL_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.DISTRIBUTION_NAME
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import com.github.jengelman.gradle.plugins.shadow.util.isWindows
import com.github.jengelman.gradle.plugins.shadow.util.runProcess
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

@ExperimentalPathApi
class ApplicationPluginTest : BasePluginTest() {
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
      applicationBlock = """
        applicationDefaultJvmArgs = ['--add-opens=java.base/java.lang=ALL-UNNAMED']
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

    val installPath = path("build/install/")
    assertThat(installPath.walkEntries()).containsOnly(
      "myapp-shadow/bin/myapp",
      "myapp-shadow/bin/myapp.bat",
      "myapp-shadow/lib/myapp-1.0-all.jar",
    )

    commonAssertions(
      jarPath("myapp-shadow/lib/myapp-1.0-all.jar", installPath),
      entriesContained = arrayOf("shadow/Main.class", "junit/framework/Test.class"),
    )

    val unixScript = path("myapp-shadow/bin/myapp", installPath)
    val winScript = path("myapp-shadow/bin/myapp.bat", installPath)

    assertThat(unixScript.readText()).contains(
      "CLASSPATH=\$APP_HOME/lib/myapp-1.0-all.jar",
      "exec \"\$JAVACMD\" \"\$@\"",
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
  fun shadowApplicationDistributionsShouldUseShadowJar() {
    prepare(
      mainClassWithImports = true,
      applicationBlock = """
        applicationDefaultJvmArgs = ['--add-opens=java.base/java.lang=ALL-UNNAMED']
      """.trimIndent(),
      dependenciesBlock = "shadow 'junit:junit:3.8.2'",
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

    assertThat(extractedPath.walkEntries()).containsOnly(
      "myapp-shadow-1.0/bin/myapp",
      "myapp-shadow-1.0/bin/myapp.bat",
      "myapp-shadow-1.0/lib/myapp-1.0-all.jar",
      "myapp-shadow-1.0/lib/junit-3.8.2.jar",
    )

    commonAssertions(
      jarPath("myapp-shadow-1.0/lib/myapp-1.0-all.jar", extractedPath),
      entriesContained = arrayOf("shadow/Main.class"),
      classPathAttr = "junit-3.8.2.jar",
    )

    val unixScript = path("myapp-shadow-1.0/bin/myapp", extractedPath)
    val winScript = path("myapp-shadow-1.0/bin/myapp.bat", extractedPath)

    assertThat(unixScript.readText()).contains(
      "CLASSPATH=\$APP_HOME/lib/myapp-1.0-all.jar",
      "exec \"\$JAVACMD\" \"\$@\"",
      "DEFAULT_JVM_OPTS='\"--add-opens=java.base/java.lang=ALL-UNNAMED\"'",
    )
    assertThat(winScript.readText()).contains(
      "set CLASSPATH=%APP_HOME%\\lib\\myapp-1.0-all.jar",
      "set DEFAULT_JVM_OPTS=\"--add-opens=java.base/java.lang=ALL-UNNAMED\"",
    )

    val runningOutput = if (isWindows) {
      // Use `cmd` and `/c` explicitly to prevent `The process cannot access the file because it is being used by another process` errors.
      runProcess("cmd", "/c", winScript.toString(), "bar")
    } else {
      // Use `sh` explicitly since the extracted script is non-executable.
      // Avoid `chmod +x` to prevent `Text file busy` errors on Linux.
      runProcess("sh", unixScript.toString(), "bar")
    }
    assertThat(runningOutput).contains(
      "Hello, World! (bar) from Main",
      "Refs: junit.framework.Test",
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

    run(runShadowTask)

    commonAssertions(
      jarPath("build/install/myapp-shadow/lib/myapp-1.0-all.jar"),
      mainClassAttr = "shadow.Main2",
    )
  }

  @Test
  fun canAddExtraFilesIntoDistribution() {
    path("extra/echo.sh").writeText("echo 'Hello, World!'")
    prepare(
      projectBlock = """
        distributions.named('$DISTRIBUTION_NAME') {
          contents.into('extra') {
            from project.file('extra/echo.sh')
          }
        }
      """.trimIndent(),
    )

    run("shadowDistZip")

    val zipPath = path("build/distributions/myapp-shadow-1.0.zip")
    ZipFile(zipPath.toFile()).use { zip ->
      val entries = zip.entries().toList().filter { !it.isDirectory }.map { it.name }
      assertThat(entries).containsOnly(
        "myapp-shadow-1.0/bin/myapp",
        "myapp-shadow-1.0/bin/myapp.bat",
        "myapp-shadow-1.0/lib/myapp-1.0-all.jar",
        "myapp-shadow-1.0/extra/echo.sh",
      )
      assertThat(zip.getContent("myapp-shadow-1.0/extra/echo.sh"))
        .isEqualTo("echo 'Hello, World!'")
    }
  }

  private fun prepare(
    mainClassWithImports: Boolean = false,
    projectBlock: String = "",
    applicationBlock: String = "",
    settingsBlock: String = "",
    dependenciesBlock: String = "implementation 'shadow:a:1.0'",
    runShadowBlock: String = "",
  ) {
    writeMainClass(withImports = mainClassWithImports)
    projectScriptPath.appendText(
      """
        apply plugin: 'application'
        $projectBlock
        application {
          mainClass = 'shadow.Main'
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

  private fun commonAssertions(
    jarPath: JarPath,
    entriesContained: Array<String> = arrayOf("a.properties", "a2.properties", "shadow/Main.class"),
    mainClassAttr: String = "shadow.Main",
    classPathAttr: String? = null,
  ) {
    assertThat(jarPath).useAll {
      containsEntries(*entriesContained)
      getMainAttr("Main-Class").isEqualTo(mainClassAttr)
      getMainAttr("Class-Path").isEqualTo(classPathAttr)
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
