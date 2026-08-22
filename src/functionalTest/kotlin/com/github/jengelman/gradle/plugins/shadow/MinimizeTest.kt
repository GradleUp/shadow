package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.classLoader
import com.github.jengelman.gradle.plugins.shadow.testkit.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.testkit.containsNone
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.invariantEolString
import java.util.ServiceLoader
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.api.JavaVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MinimizeTest : BasePluginTest() {
  private val outputImplShadowedJar: JarPath
    get() = jarPath("impl/build/libs/impl-1.0-all.jar")

  /**
   * 'api' used as api for 'impl', and depended on 'lib'. 'junit' is independent. The minimize step
   * shall remove 'junit', but not 'api'. Unused classes of 'api' and theirs dependencies also
   * shouldn't be removed.
   */
  @Test
  fun useMinimizeWithDependenciesWithApiScope() {
    writeApiLibAndImplModules()

    runWithSuccess(":impl:$SHADOW_JAR_TASK_NAME")

    assertThat(outputImplShadowedJar).useAll {
      containsAtLeast(
        "api/",
        "lib/",
        "impl/",
        "impl/SimpleEntity.class",
        "api/Entity.class",
        "api/UnusedEntity.class",
        "lib/LibEntity.class",
        *manifestEntries,
      )
    }
  }

  /**
   * 'api' used as api for 'impl', and 'lib' used as api for 'api'. Unused classes of 'api' and
   * 'lib' shouldn't be removed.
   */
  @Test
  fun useMinimizeWithTransitiveDependenciesWithApiScope() {
    writeApiLibAndImplModules()
    path("api/build.gradle")
      .writeText(
        """
        |plugins {
        |  id 'java-library'
        |}
        |dependencies {
        |  api project(':lib')
        |}
        """
          .trimMargin()
      )

    runWithSuccess(":impl:$SHADOW_JAR_TASK_NAME")

    assertThat(outputImplShadowedJar).useAll {
      containsOnly(
        "api/",
        "impl/",
        "lib/",
        "impl/SimpleEntity.class",
        "api/Entity.class",
        "api/UnusedEntity.class",
        "lib/LibEntity.class",
        "lib/UnusedLibEntity.class",
        *manifestEntries,
      )
    }
  }

  /** 'Server' depends on 'Client'. 'junit' is independent. The minimize shall remove 'junit'. */
  @Test
  fun minimizeByKeepingOnlyTransitiveDependencies() {
    writeClientAndServerModules(
      serverShadowBlock =
        """
        |minimize()
        """
          .trimMargin()
    )
    path("server/src/main/java/server/Server.java")
      .writeText(
        """
        |package server;
        |import client.Client;
        |public class Server {
        |  // This is to make sure that 'Client' is not removed.
        |  private final String client = Client.class.getName();
        |}
        """
          .trimMargin()
      )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsAtLeast("client/Client.class", "server/Server.class")
      containsNone("junit/framework/Test.class")
    }
  }

  /**
   * 'Client', 'Server' and 'junit' are independent. 'junit' is excluded from the minimize step. The
   * minimize step shall remove 'Client' but not 'junit'.
   */
  @Test
  fun excludeDependencyFromMinimize() {
    writeClientAndServerModules(
      serverShadowBlock =
        """
        |minimize {
        |  exclude(dependency('junit:junit:.*'))
        |}
        """
          .trimMargin()
    )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsAtLeast("server/Server.class", *junitEntries)
      containsNone("client/Client.class")
    }
  }

  /**
   * 'Client', 'Server' and 'junit' are independent. Unused classes of 'client' and theirs
   * dependencies shouldn't be removed.
   */
  @Test // #744
  fun excludeProjectFromMinimize() {
    writeClientAndServerModules(
      serverShadowBlock =
        """
        |minimize {
        |  exclude(project(':client'))
        |}
        """
          .trimMargin()
    )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsOnly(
        "client/",
        "server/",
        "client/Client.class",
        "server/Server.class",
        *junitEntries,
        *manifestEntries,
      )
    }
  }

  /**
   * 'Client', 'Server' and 'junit' are independent. Unused classes of 'client' and theirs
   * dependencies shouldn't be removed.
   */
  @Test
  fun excludeProjectFromMinimizeShallNotExcludeTransitiveDependenciesThatAreUsedInSubproject() {
    writeClientAndServerModules(
      serverShadowBlock =
        """
        |minimize {
        |  exclude(project(':client'))
        |}
        """
          .trimMargin()
    )
    path("client/src/main/java/client/Client.java")
      .writeText(
        """
        |package client;
        |import junit.framework.TestCase;
        |public class Client extends TestCase {
        |  public static void main(String[] args) {}
        |}
        """
          .trimMargin()
      )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsAtLeast("client/Client.class", "server/Server.class", *junitEntries)
    }

    path("client/src/main/java/client/Client.java")
      .writeText(
        """
        |package client;
        |public class Client {}
        """
          .trimMargin()
      )
    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsAtLeast("client/Client.class", "server/Server.class", *junitEntries)
    }
  }

  @Test // #1610
  fun excludeCircularDependencies() {
    val dependency = "'my:e:1.0'"
    projectScript.appendText(
      """
      |dependencies {
      |  implementation $dependency
      |}
      |$shadowJarTask {
      |  minimize {
      |    exclude(dependency($dependency))
      |  }
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("e.properties", "f.properties", *manifestEntries)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun enableMinimizationByCliOption(enable: Boolean) {
    writeClientAndServerModules()

    if (enable) {
      runWithSuccess(serverShadowJarPath, "--minimize-jar")
    } else {
      runWithSuccess(serverShadowJarPath, "--no-minimize-jar")
    }

    assertThat(outputServerShadowedJar).useAll {
      if (enable) {
        containsAtLeast("server/Server.class", *manifestEntries)
        containsNone("client/Client.class")
      } else {
        containsOnly(
          "client/",
          "server/",
          "client/Client.class",
          "server/Server.class",
          *junitEntries,
          *manifestEntries,
        )
      }
    }
  }

  @Test // #1636
  fun minimizeBomDependency() {
    writeApiLibAndImplModules()
    path("impl/build.gradle")
      .appendText(
        """
        |dependencies {
        |  api platform('my:bom:1.0')
        |}
        """
          .trimMargin()
      )

    runWithSuccess(":impl:$SHADOW_JAR_TASK_NAME")

    assertThat(outputImplShadowedJar).useAll {
      containsAtLeast(
        "api/",
        "lib/",
        "impl/",
        "impl/SimpleEntity.class",
        "api/Entity.class",
        "api/UnusedEntity.class",
        "lib/LibEntity.class",
        *manifestEntries,
      )
    }
  }

  @Test
  fun minimizeWithR8ShrinksUnusedDependencyClasses() {
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
      containsOnly(
        "server/",
        "server/Server.class",
        "client/",
        "client/Used.class",
        *manifestEntries,
      )
      classLoader { loader ->
        val server = loader.loadClass("server.Server")
        val used = loader.loadClass("client.Used")
        assertThat(server.name).isEqualTo("server.Server")
        assertThat(used.name).isEqualTo("client.Used")
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
  fun minimizeWithR8KeepsServiceProviders() {
    writeR8Repository()
    writeR8ServiceModules()

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsOnly(
        "server/",
        "server/Server.class",
        "service/",
        "service/Greeter.class",
        "service/DefaultGreeter.class",
        "META-INF/services/",
        "META-INF/services/service.Greeter",
        *manifestEntries,
      )
      getContent("META-INF/services/service.Greeter").isEqualTo("service.DefaultGreeter\n")
      classLoader(null) { loader ->
        val serviceClass = loader.loadClass("service.Greeter")
        assertThat(ServiceLoader.load(serviceClass, loader).toList()).hasSize(1)
      }
    }
  }

  @Test
  fun minimizeWithR8HonorsCustomProguardRules() {
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
      containsOnly(
        "server/",
        "server/Server.class",
        "client/",
        "client/Used.class",
        "client/Reflective.class",
        *manifestEntries,
      )
      classLoader { loader ->
        val server = loader.loadClass("server.Server")
        val used = loader.loadClass("client.Used")
        val reflective = loader.loadClass("client.Reflective")
        assertThat(server.name).isEqualTo("server.Server")
        assertThat(used.name).isEqualTo("client.Used")
        assertThat(reflective.name).isEqualTo("client.Reflective")
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
  fun minimizeWithR8GeneratesReportsRelativeToConfigurationFile() {
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
  fun minimizeWithR8UsesClasspathRules() {
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
      containsOnly(
        "client/",
        "client/Used.class",
        "client/Reflective.class",
        "server/",
        "server/Server.class",
        "META-INF/proguard/",
        "META-INF/proguard/client.pro",
        *manifestEntries,
      )
      classLoader { loader ->
        val server = loader.loadClass("server.Server")
        val used = loader.loadClass("client.Used")
        val reflective = loader.loadClass("client.Reflective")
        assertThat(server.name).isEqualTo("server.Server")
        assertThat(used.name).isEqualTo("client.Used")
        assertThat(reflective.name).isEqualTo("client.Reflective")
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
  fun minimizeWithR8PreservesRepeatedLinesInClasspathRules() {
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
      containsOnly(
        "client/",
        "client/Used.class",
        "client/Reflective.class",
        "client/Unused.class",
        "server/",
        "server/Server.class",
        "META-INF/proguard/",
        "META-INF/proguard/client.pro",
        *manifestEntries,
      )
      classLoader { loader ->
        val server = loader.loadClass("server.Server")
        val used = loader.loadClass("client.Used")
        val reflective = loader.loadClass("client.Reflective")
        val unused = loader.loadClass("client.Unused")
        assertThat(server.name).isEqualTo("server.Server")
        assertThat(used.name).isEqualTo("client.Used")
        assertThat(reflective.name).isEqualTo("client.Reflective")
        assertThat(unused.name).isEqualTo("client.Unused")
      }
    }
  }

  @Test
  fun minimizeWithR8CanEnableObfuscation() {
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
      containsOnly(
        "server/",
        "server/Server.class",
        "a/",
        "a/a.class",
        *manifestEntries,
      )
      classLoader { loader ->
        val server = loader.loadClass("server.Server")
        val obfuscated = loader.loadClass("a.a")
        assertThat(server.name).isEqualTo("server.Server")
        assertThat(obfuscated.name).isEqualTo("a.a")
      }
    }
  }

  @Test
  fun minimizeWithR8CanEnableOptimization() {
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
      containsOnly(
        "server/",
        "server/Server.class",
        *manifestEntries,
      )
    }
  }

  @Test
  fun minimizeWithR8HonorsDependencyExcludes() {
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
      containsOnly(
        "server/",
        "server/Server.class",
        "client/",
        "client/Used.class",
        "client/Unused.class",
        "client/Reflective.class",
        *manifestEntries,
      )
      classLoader { loader ->
        val server = loader.loadClass("server.Server")
        val used = loader.loadClass("client.Used")
        val unused = loader.loadClass("client.Unused")
        val reflective = loader.loadClass("client.Reflective")
        assertThat(server.name).isEqualTo("server.Server")
        assertThat(used.name).isEqualTo("client.Used")
        assertThat(unused.name).isEqualTo("client.Unused")
        assertThat(reflective.name).isEqualTo("client.Reflective")
      }
    }
  }

  @Test
  fun minimizeWithR8UsesJavaToolchain() {
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

  private fun writeApiLibAndImplModules() {
    settingsScript.appendText(
      """
      |include 'api', 'lib', 'impl'
      |"""
        .trimMargin()
    )
    projectScript.writeText("")

    path("lib/src/main/java/lib/LibEntity.java")
      .writeText(
        """
        |package lib;
        |public interface LibEntity {}
        """
          .trimMargin()
      )
    path("lib/src/main/java/lib/UnusedLibEntity.java")
      .writeText(
        """
        |package lib;
        |public class UnusedLibEntity implements LibEntity {}
        """
          .trimMargin()
      )
    path("lib/build.gradle")
      .writeText(
        """
        |plugins {
        |  id 'java'
        |}
        |"""
          .trimMargin()
      )

    path("api/src/main/java/api/Entity.java")
      .writeText(
        """
        |package api;
        |public interface Entity {}
        """
          .trimMargin()
      )
    path("api/src/main/java/api/UnusedEntity.java")
      .writeText(
        """
        |package api;
        |import lib.LibEntity;
        |public class UnusedEntity implements LibEntity {}
        """
          .trimMargin()
      )
    path("api/build.gradle")
      .writeText(
        """
        |plugins {
        |  id 'java'
        |}
        |dependencies {
        |  implementation 'junit:junit:3.8.2'
        |  implementation project(':lib')
        |}
        |"""
          .trimMargin()
      )

    path("impl/src/main/java/impl/SimpleEntity.java")
      .writeText(
        """
        |package impl;
        |import api.Entity;
        |public class SimpleEntity implements Entity {}
        """
          .trimMargin()
      )
    path("impl/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java-library")}
        |dependencies {
        |  api project(':api')
        |}
        |$shadowJarTask {
        |  minimize()
        |}
        |
        """
          .trimMargin()
      )
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
