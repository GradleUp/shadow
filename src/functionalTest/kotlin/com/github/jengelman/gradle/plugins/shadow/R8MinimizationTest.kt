package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.classLoader
import com.github.jengelman.gradle.plugins.shadow.testkit.containsExactly
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.invariantEolString
import com.github.jengelman.gradle.plugins.shadow.testkit.loadClass
import com.github.jengelman.gradle.plugins.shadow.testkit.loadService
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.api.JavaVersion
import org.junit.jupiter.api.Test

class R8MinimizationTest : BasePluginTest() {
  private val appShadowJarPath = ":app:$SHADOW_JAR_TASK_NAME"
  private val outputAppShadowedJar: JarPath
    get() = jarPath("app/build/libs/app-1.0-all.jar")

  @Test
  fun shrinkUnusedDependencyClasses() {
    writeR8AppAndLibModules(
      appShadowBlock =
        """
        |minimize {
        |  r8 {}
        |}
        """
          .trimMargin()
    )

    runWithSuccess(appShadowJarPath)

    assertThat(outputAppShadowedJar).useAll {
      containsExactly(
        "app/App.class",
        "lib/Used.class",
        manifestEntry,
      )
      classLoader {
        loadClass("app.App")
        loadClass("lib.Used")
      }
    }
    val inputConfigPath = path("app/build/tmp/shadowJar/r8/rules.pro").toRealPath()
    val outputConfigDir = path("app/build/shadowJar/r8").toRealPath()
    assertThat(path("app/build/shadowJar/r8/configuration.txt").readText().invariantEolString)
      .isEqualTo(
        """
        |# The proguard configuration file for the following section is $inputConfigPath
        |-basedirectory '$outputConfigDir'
        |-dontoptimize
        |-keep,includedescriptorclasses class app.App { *; }
        |# End of content from $inputConfigPath
        |"""
          .trimMargin()
      )
  }

  @Test
  fun keepServiceProviders() {
    writeR8AppAndServiceModules()

    runWithSuccess(appShadowJarPath)

    assertThat(outputAppShadowedJar).useAll {
      containsExactly(
        "app/App.class",
        "service/DefaultGreeter.class",
        "service/Greeter.class",
        manifestEntry,
        "META-INF/services/service.Greeter",
      )
      getContent("META-INF/services/service.Greeter").isEqualTo("service.DefaultGreeter\n")
      classLoader {
        val serviceClass = loadClass("service.Greeter")
        loadService(serviceClass).hasSize(1)
      }
    }
  }

  @Test
  fun honorCustomProguardRules() {
    writeR8AppAndLibModules(
      appShadowBlock =
        """
        |minimize {
        |  r8 {
        |    proguardRules.add("-keep class lib.Reflective { *; }")
        |    configurationFile.set(layout.buildDirectory.file("r8/config/final-configuration.txt"))
        |  }
        |}
        """
          .trimMargin()
    )

    runWithSuccess(appShadowJarPath)

    assertThat(outputAppShadowedJar).useAll {
      containsExactly(
        "app/App.class",
        "lib/Reflective.class",
        "lib/Used.class",
        manifestEntry,
      )
      classLoader {
        loadClass("app.App")
        loadClass("lib.Used")
        loadClass("lib.Reflective")
      }
    }
    val inputConfigPath = path("app/build/tmp/shadowJar/r8/rules.pro").toRealPath()
    val outputConfigDir = path("app/build/r8/config").toRealPath()
    assertThat(path("app/build/r8/config/final-configuration.txt").readText().invariantEolString)
      .isEqualTo(
        """
        |# The proguard configuration file for the following section is $inputConfigPath
        |-basedirectory '$outputConfigDir'
        |-dontoptimize
        |-keep,includedescriptorclasses class app.App { *; }
        |-keep class lib.Reflective { *; }
        |# End of content from $inputConfigPath
        |"""
          .trimMargin()
      )
  }

  @Test
  fun canKeepDirectories() {
    writeR8AppAndLibModules(
      appShadowBlock =
        """
        |minimize {
        |  r8 {
        |    proguardRules.add("-keepdirectories")
        |  }
        |}
        """
          .trimMargin()
    )

    runWithSuccess(appShadowJarPath)

    assertThat(outputAppShadowedJar).useAll {
      containsExactly(
        "app/App.class",
        "lib/Used.class",
        *manifestEntries,
        "app/",
        "lib/",
      )
      classLoader {
        loadClass("app.App")
        loadClass("lib.Used")
      }
    }
  }

  @Test
  fun generateReportsRelativeToConfigurationFile() {
    writeR8AppAndLibModules(
      appShadowBlock =
        """
        |minimize {
        |  r8 {
        |    enableObfuscation()
        |    configurationFile.set(layout.buildDirectory.file("r8/configuration.txt"))
        |    proguardRules.addAll(
        |      "-printmapping reports/mapping.txt",
        |      "-printseeds reports/seeds.txt",
        |      "-printusage reports/usage.txt",
        |    )
        |  }
        |}
        """
          .trimMargin()
    )

    runWithSuccess(appShadowJarPath)

    val inputConfigPath = path("app/build/tmp/shadowJar/r8/rules.pro").toRealPath()
    val outputConfigDir = path("app/build/r8").toRealPath()
    assertThat(path("app/build/r8/configuration.txt").readText().invariantEolString)
      .isEqualTo(
        """
        |# The proguard configuration file for the following section is $inputConfigPath
        |-basedirectory '$outputConfigDir'
        |-dontoptimize
        |-keep,includedescriptorclasses class app.App { *; }
        |-printmapping reports/mapping.txt
        |-printseeds reports/seeds.txt
        |-printusage reports/usage.txt
        |# End of content from $inputConfigPath
        |"""
          .trimMargin()
      )
    assertThat(path("app/build/r8/reports/mapping.txt").readText()).contains("lib.Used")
    assertThat(path("app/build/r8/reports/seeds.txt").readText()).contains("app.App")
    assertThat(path("app/build/r8/reports/usage.txt").readText())
      .contains("lib.Reflective", "lib.Unused")
  }

  @Test
  fun useClasspathRules() {
    writeR8AppAndLibModules(
      appShadowBlock =
        """
        |minimize {
        |  r8 {}
        |}
        """
          .trimMargin()
    )
    path("lib/src/main/resources/META-INF/proguard/lib.pro")
      .writeText("-keep class lib.Reflective { *; }")

    runWithSuccess(appShadowJarPath)

    assertThat(outputAppShadowedJar).useAll {
      containsExactly(
        "app/App.class",
        "lib/Reflective.class",
        "lib/Used.class",
        manifestEntry,
        "META-INF/proguard/lib.pro",
      )
      classLoader {
        loadClass("app.App")
        loadClass("lib.Used")
        loadClass("lib.Reflective")
      }
    }
    val inputConfigPath = path("app/build/tmp/shadowJar/r8/rules.pro").toRealPath()
    val outputConfigDir = path("app/build/shadowJar/r8").toRealPath()
    val embeddedConfigPath =
      "${path("app/build/libs/app-1.0-all.jar").toRealPath()}:META-INF/proguard/lib.pro"
    assertThat(path("app/build/shadowJar/r8/configuration.txt").readText().invariantEolString)
      .isEqualTo(
        """
        |# The proguard configuration file for the following section is $inputConfigPath
        |-basedirectory '$outputConfigDir'
        |-dontoptimize
        |-keep,includedescriptorclasses class app.App { *; }
        |# End of content from $inputConfigPath
        |# The proguard configuration file for the following section is $embeddedConfigPath
        |-keep class lib.Reflective { *; }
        |# End of content from $embeddedConfigPath
        |"""
          .trimMargin()
      )
  }

  @Test
  fun preserveRepeatedLinesInClasspathRules() {
    writeR8AppAndLibModules(
      appShadowBlock =
        """
        |minimize {
        |  r8 {}
        |}
        """
          .trimMargin()
    )
    path("lib/src/main/resources/META-INF/proguard/lib.pro")
      .writeText(
        """
        |-keep class lib.Reflective {
        |  public <init>();
        |}
        |-keep class lib.Unused {
        |  public <init>();
        |}
        """
          .trimMargin()
      )

    runWithSuccess(appShadowJarPath)

    assertThat(outputAppShadowedJar).useAll {
      containsExactly(
        "app/App.class",
        "lib/Reflective.class",
        "lib/Unused.class",
        "lib/Used.class",
        manifestEntry,
        "META-INF/proguard/lib.pro",
      )
      classLoader {
        loadClass("app.App")
        loadClass("lib.Used")
        loadClass("lib.Reflective")
        loadClass("lib.Unused")
      }
    }
  }

  @Test
  fun canEnableObfuscation() {
    writeR8AppAndLibModules(
      appShadowBlock =
        """
        |minimize {
        |  r8 {
        |    enableObfuscation()
        |  }
        |}
        """
          .trimMargin()
    )

    runWithSuccess(appShadowJarPath)

    assertThat(outputAppShadowedJar).useAll {
      containsExactly(
        "a/a.class",
        "app/App.class",
        manifestEntry,
      )
      classLoader {
        loadClass("app.App")
        loadClass("a.a")
      }
    }
  }

  @Test
  fun canEnableOptimization() {
    writeR8AppAndLibModules(
      appShadowBlock =
        """
        |minimize {
        |  r8 {
        |    enableOptimization()
        |  }
        |}
        """
          .trimMargin()
    )

    runWithSuccess(appShadowJarPath)

    assertThat(outputAppShadowedJar).useAll {
      containsExactly(
        "app/App.class",
        manifestEntry,
      )
    }
  }

  @Test
  fun honorDependencyExcludes() {
    writeR8AppAndLibModules(
      appShadowBlock =
        """
        |minimize {
        |  exclude(project(':lib'))
        |  r8 {}
        |}
        """
          .trimMargin()
    )

    runWithSuccess(appShadowJarPath)

    assertThat(outputAppShadowedJar).useAll {
      containsExactly(
        "app/App.class",
        "lib/Reflective.class",
        "lib/Unused.class",
        "lib/Used.class",
        manifestEntry,
      )
      classLoader {
        loadClass("app.App")
        loadClass("lib.Used")
        loadClass("lib.Unused")
        loadClass("lib.Reflective")
      }
    }
  }

  @Test
  fun useJavaToolchain() {
    writeR8AppAndLibModules(
      appProjectBlock =
        """
        |java {
        |  toolchain.languageVersion = JavaLanguageVersion.of(${JavaVersion.current().majorVersion})
        |}
        """
          .trimMargin(),
      appShadowBlock =
        """
        |doFirst {
        |  logger.lifecycle("R8 launcher JDK " + javaLauncher.get().metadata.languageVersion.asInt())
        |}
        |minimize {
        |  r8 {}
        |}
        """
          .trimMargin(),
    )

    val result = runWithSuccess(appShadowJarPath)

    assertThat(result.output).contains("R8 launcher JDK ${JavaVersion.current().majorVersion}")
  }

  private fun writeR8AppAndLibModules(
    appShadowBlock: String,
    appProjectBlock: String = "",
  ) {
    settingsScript.appendText(
      """
      |include 'app', 'lib'
      """
        .trimMargin()
    )
    projectScript.writeText("")

    path("lib/src/main/java/lib/Used.java")
      .writeText(
        """
        |package lib;
        |public class Used {
        |  public static String name() {
        |    return "used";
        |  }
        |}
        """
          .trimMargin()
      )
    path("lib/src/main/java/lib/Unused.java")
      .writeText(
        """
        |package lib;
        |public class Unused {}
        """
          .trimMargin()
      )
    path("lib/src/main/java/lib/Reflective.java")
      .writeText(
        """
        |package lib;
        |public class Reflective {}
        """
          .trimMargin()
      )
    path("lib/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java")}
        |
        """
          .trimMargin()
      )

    path("app/src/main/java/app/App.java")
      .writeText(
        """
        |package app;
        |import lib.Used;
        |public class App {
        |  public String name() {
        |    return Used.name();
        |  }
        |}
        """
          .trimMargin()
      )
    path("app/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java")}
        |$appProjectBlock
        |dependencies {
        |  implementation project(':lib')
        |}
        |$shadowJarTask {
        |  $appShadowBlock
        |}
        |
        """
          .trimMargin()
      )
  }

  private fun writeR8AppAndServiceModules() {
    settingsScript.appendText(
      """
      |include 'app', 'service'
      """
        .trimMargin()
    )
    projectScript.writeText("")

    path("service/src/main/java/service/Greeter.java")
      .writeText(
        """
        |package service;
        |public interface Greeter {
        |  String greet();
        |}
        """
          .trimMargin()
      )
    path("service/src/main/java/service/DefaultGreeter.java")
      .writeText(
        """
        |package service;
        |public class DefaultGreeter implements Greeter {
        |  public String greet() {
        |    return "hello";
        |  }
        |}
        """
          .trimMargin()
      )
    path("service/src/main/resources/META-INF/services/service.Greeter")
      .writeText("service.DefaultGreeter")
    path("service/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java")}
        |
        """
          .trimMargin()
      )

    path("app/src/main/java/app/App.java")
      .writeText(
        """
        |package app;
        |public class App {}
        """
          .trimMargin()
      )
    path("app/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java")}
        |dependencies {
        |  implementation project(':service')
        |}
        |$shadowJarTask {
        |  minimize {
        |    r8 {}
        |  }
        |}
        |
        """
          .trimMargin()
      )
  }
}
