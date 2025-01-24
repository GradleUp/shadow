package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_INSTALL_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_RUN_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import com.github.jengelman.gradle.plugins.shadow.util.isWindows
import com.github.jengelman.gradle.plugins.shadow.util.runProcess
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.setPosixFilePermissions
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
      "Hello, World! (foo) from Main",
      "Refs: junit.framework.Test",
    )

    assertThat(path("build/install/").walkEntries()).containsOnly(
      "myapp-shadow/bin/myapp",
      "myapp-shadow/bin/myapp.bat",
      "myapp-shadow/lib/myapp-1.0-all.jar",
    )

    commonAssertions(
      jarPath("build/install/myapp-shadow/lib/myapp-1.0-all.jar"),
      entriesContained = arrayOf("shadow/Main.class", "junit/framework/Test.class"),
    )

    val unixScript = path("build/install/myapp-shadow/bin/myapp")
    val winScript = path("build/install/myapp-shadow/bin/myapp.bat")

    assertThat(unixScript.readText()).contains(
      "CLASSPATH=\$APP_HOME/lib/myapp-1.0-all.jar",
      "-jar \"\\\"\$CLASSPATH\\\"\" \"\$APP_ARGS\"",
      "exec \"\$JAVACMD\" \"\$@\"",
    )
    assertThat(winScript.readText()).contains(
      "set CLASSPATH=%APP_HOME%\\lib\\myapp-1.0-all.jar",
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

    val extractedShadowJarPath = jarPath("myapp-shadow-1.0/lib/myapp-1.0-all.jar", extractedPath)
    commonAssertions(
      extractedShadowJarPath,
      entriesContained = arrayOf("shadow/Main.class"),
      classPathAttr = "junit-3.8.2.jar",
    )

    val unixScript = path("myapp-shadow-1.0/bin/myapp", extractedPath).apply {
      // Make the extracted script executable.
      setPosixFilePermissions(
        setOf(
          PosixFilePermission.OWNER_EXECUTE,
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.GROUP_EXECUTE,
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.OTHERS_EXECUTE,
          PosixFilePermission.OTHERS_READ,
        ),
      )
    }
    val winScript = path("myapp-shadow-1.0/bin/myapp.bat", extractedPath)

    assertThat(unixScript.readText()).contains(
      "CLASSPATH=\$APP_HOME/lib/myapp-1.0-all.jar",
      "-jar \"\\\"\$CLASSPATH\\\"\" \"\$APP_ARGS\"",
      "exec \"\$JAVACMD\" \"\$@\"",
    )
    assertThat(winScript.readText()).contains(
      "set CLASSPATH=%APP_HOME%\\lib\\myapp-1.0-all.jar",
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

    val result = run(SHADOW_RUN_TASK_NAME)

    assertThat(result.output).contains(
      "Hello, World! (foo) from Main2",
    )
    commonAssertions(
      jarPath("build/install/myapp-shadow/lib/myapp-1.0-all.jar"),
      mainClassAttr = "shadow.Main2",
    )
  }

  private fun prepare(
    mainClassWithImports: Boolean = false,
    projectBlock: String = "",
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
    val runShadow = "tasks.named('$SHADOW_RUN_TASK_NAME')".trim()

    fun Path.walkEntries(): Sequence<String> {
      return walk()
        .filter { it.isRegularFile() }
        .map { it.relativeTo(this) }
        .map { it.toString().replace(FileSystems.getDefault().separator, "/") }
    }
  }
}
