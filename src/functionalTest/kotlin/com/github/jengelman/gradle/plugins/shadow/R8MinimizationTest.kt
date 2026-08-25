package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
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
  @Test
  fun shrinkUnusedDependencyClasses() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
        """
        |minimize {
        |  r8 {}
        |}
        """
          .trimMargin()
    )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsExactly(
        "client/Used.class",
        "server/Server.class",
        manifestEntry,
      )
      classLoader {
        loadClass("server.Server")
        loadClass("client.Used")
      }
    }
    val inputConfigPath = path("server/build/tmp/shadowJar/r8/rules.pro").toRealPath()
    val outputConfigDir = path("server/build/shadowJar/r8").toRealPath()
    assertThat(path("server/build/shadowJar/r8/configuration.txt").readText().invariantEolString)
      .isEqualTo(
        """
        |# The proguard configuration file for the following section is $inputConfigPath
        |-basedirectory '$outputConfigDir'
        |-dontoptimize
        |-keep,includedescriptorclasses class server.Server { *; }
        |# End of content from $inputConfigPath
        |"""
          .trimMargin()
      )
  }

  @Test
  fun keepServiceProviders() {
    writeR8Repository()
    writeR8ServiceModules()

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsExactly(
        "server/Server.class",
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
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
        """
        |minimize {
        |  r8 {
        |    proguardRules.add("-keep class client.Reflective { *; }")
        |    configurationFile.set(layout.buildDirectory.file("r8/config/final-configuration.txt"))
        |  }
        |}
        """
          .trimMargin()
    )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsExactly(
        "client/Reflective.class",
        "client/Used.class",
        "server/Server.class",
        manifestEntry,
      )
      classLoader {
        loadClass("server.Server")
        loadClass("client.Used")
        loadClass("client.Reflective")
      }
    }
    val inputConfigPath = path("server/build/tmp/shadowJar/r8/rules.pro").toRealPath()
    val outputConfigDir = path("server/build/r8/config").toRealPath()
    assertThat(path("server/build/r8/config/final-configuration.txt").readText().invariantEolString)
      .isEqualTo(
        """
        |# The proguard configuration file for the following section is $inputConfigPath
        |-basedirectory '$outputConfigDir'
        |-dontoptimize
        |-keep,includedescriptorclasses class server.Server { *; }
        |-keep class client.Reflective { *; }
        |# End of content from $inputConfigPath
        |"""
          .trimMargin()
      )
  }

  @Test
  fun canKeepDirectories() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
        """
        |minimize {
        |  r8 {
        |    proguardRules.add("-keepdirectories")
        |  }
        |}
        """
          .trimMargin()
    )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsExactly(
        "client/Used.class",
        "server/Server.class",
        *manifestEntries,
        "client/",
        "server/",
      )
      classLoader {
        loadClass("server.Server")
        loadClass("client.Used")
      }
    }
  }

  @Test
  fun generateReportsRelativeToConfigurationFile() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
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

    runWithSuccess(serverShadowJarPath)

    val inputConfigPath = path("server/build/tmp/shadowJar/r8/rules.pro").toRealPath()
    val outputConfigDir = path("server/build/r8").toRealPath()
    assertThat(path("server/build/r8/configuration.txt").readText().invariantEolString)
      .isEqualTo(
        """
        |# The proguard configuration file for the following section is $inputConfigPath
        |-basedirectory '$outputConfigDir'
        |-dontoptimize
        |-keep,includedescriptorclasses class server.Server { *; }
        |-printmapping reports/mapping.txt
        |-printseeds reports/seeds.txt
        |-printusage reports/usage.txt
        |# End of content from $inputConfigPath
        |"""
          .trimMargin()
      )
    assertThat(path("server/build/r8/reports/mapping.txt").readText()).contains("client.Used")
    assertThat(path("server/build/r8/reports/seeds.txt").readText()).contains("server.Server")
    assertThat(path("server/build/r8/reports/usage.txt").readText())
      .contains("client.Reflective", "client.Unused")
  }

  @Test
  fun useClasspathRules() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
        """
        |minimize {
        |  r8 {}
        |}
        """
          .trimMargin()
    )
    path("client/src/main/resources/META-INF/proguard/client.pro")
      .writeText("-keep class client.Reflective { *; }")

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsExactly(
        "client/Reflective.class",
        "client/Used.class",
        "server/Server.class",
        manifestEntry,
        "META-INF/proguard/client.pro",
      )
      classLoader {
        loadClass("server.Server")
        loadClass("client.Used")
        loadClass("client.Reflective")
      }
    }
    val inputConfigPath = path("server/build/tmp/shadowJar/r8/rules.pro").toRealPath()
    val outputConfigDir = path("server/build/shadowJar/r8").toRealPath()
    val embeddedConfigPath =
      "${path("server/build/libs/server-1.0-all.jar").toRealPath()}:META-INF/proguard/client.pro"
    assertThat(path("server/build/shadowJar/r8/configuration.txt").readText().invariantEolString)
      .isEqualTo(
        """
        |# The proguard configuration file for the following section is $inputConfigPath
        |-basedirectory '$outputConfigDir'
        |-dontoptimize
        |-keep,includedescriptorclasses class server.Server { *; }
        |# End of content from $inputConfigPath
        |# The proguard configuration file for the following section is $embeddedConfigPath
        |-keep class client.Reflective { *; }
        |# End of content from $embeddedConfigPath
        |"""
          .trimMargin()
      )
  }

  @Test
  fun preserveRepeatedLinesInClasspathRules() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
        """
        |minimize {
        |  r8 {}
        |}
        """
          .trimMargin()
    )
    path("client/src/main/resources/META-INF/proguard/client.pro")
      .writeText(
        """
        |-keep class client.Reflective {
        |  public <init>();
        |}
        |-keep class client.Unused {
        |  public <init>();
        |}
        """
          .trimMargin()
      )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsExactly(
        "client/Reflective.class",
        "client/Unused.class",
        "client/Used.class",
        "server/Server.class",
        manifestEntry,
        "META-INF/proguard/client.pro",
      )
      classLoader {
        loadClass("server.Server")
        loadClass("client.Used")
        loadClass("client.Reflective")
        loadClass("client.Unused")
      }
    }
  }

  @Test
  fun canEnableObfuscation() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
        """
        |minimize {
        |  r8 {
        |    enableObfuscation()
        |  }
        |}
        """
          .trimMargin()
    )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsExactly(
        "a/a.class",
        "server/Server.class",
        manifestEntry,
      )
      classLoader {
        loadClass("server.Server")
        loadClass("a.a")
      }
    }
  }

  @Test
  fun canEnableOptimization() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
        """
        |minimize {
        |  r8 {
        |    enableOptimization()
        |  }
        |}
        """
          .trimMargin()
    )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsExactly(
        "server/Server.class",
        manifestEntry,
      )
    }
  }

  @Test
  fun honorDependencyExcludes() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
        """
        |minimize {
        |  exclude(project(':client'))
        |  r8 {}
        |}
        """
          .trimMargin()
    )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsExactly(
        "client/Reflective.class",
        "client/Unused.class",
        "client/Used.class",
        "server/Server.class",
        manifestEntry,
      )
      classLoader {
        loadClass("server.Server")
        loadClass("client.Used")
        loadClass("client.Unused")
        loadClass("client.Reflective")
      }
    }
  }

  @Test
  fun useJavaToolchain() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverProjectBlock =
        """
        |java {
        |  toolchain.languageVersion = JavaLanguageVersion.of(${JavaVersion.current().majorVersion})
        |}
        """
          .trimMargin(),
      serverShadowBlock =
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

    val result = runWithSuccess(serverShadowJarPath)

    assertThat(result.output).contains("R8 launcher JDK ${JavaVersion.current().majorVersion}")
  }

  private fun writeR8ClientAndServerModules(
    serverShadowBlock: String,
    serverProjectBlock: String = "",
  ) {
    settingsScript.appendText(
      """
      |include 'client', 'server'
      """
        .trimMargin()
    )
    projectScript.writeText("")

    path("client/src/main/java/client/Used.java")
      .writeText(
        """
        |package client;
        |public class Used {
        |  public static String name() {
        |    return "used";
        |  }
        |}
        """
          .trimMargin()
      )
    path("client/src/main/java/client/Unused.java")
      .writeText(
        """
        |package client;
        |public class Unused {}
        """
          .trimMargin()
      )
    path("client/src/main/java/client/Reflective.java")
      .writeText(
        """
        |package client;
        |public class Reflective {}
        """
          .trimMargin()
      )
    path("client/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java")}
        |
        """
          .trimMargin()
      )

    path("server/src/main/java/server/Server.java")
      .writeText(
        """
        |package server;
        |import client.Used;
        |public class Server {
        |  public String name() {
        |    return Used.name();
        |  }
        |}
        """
          .trimMargin()
      )
    path("server/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java")}
        |$serverProjectBlock
        |dependencies {
        |  implementation project(':client')
        |}
        |$shadowJarTask {
        |  $serverShadowBlock
        |}
        |
        """
          .trimMargin()
      )
  }

  private fun writeR8ServiceModules() {
    settingsScript.appendText(
      """
      |include 'service', 'server'
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

    path("server/src/main/java/server/Server.java")
      .writeText(
        """
        |package server;
        |public class Server {}
        """
          .trimMargin()
      )
    path("server/build.gradle")
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
