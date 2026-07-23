package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.testkit.containsNone
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import java.net.URLClassLoader
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
        plugins {
          id 'java-library'
        }
        dependencies {
          api project(':lib')
        }
        """
          .trimIndent()
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
        minimize()
        """
          .trimIndent()
    )
    path("server/src/main/java/server/Server.java")
      .writeText(
        """
        package server;
        import client.Client;
        public class Server {
          // This is to make sure that 'Client' is not removed.
          private final String client = Client.class.getName();
        }
        """
          .trimIndent()
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
        minimize {
          exclude(dependency('junit:junit:.*'))
        }
        """
          .trimIndent()
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
  @Issue("https://github.com/GradleUp/shadow/issues/744")
  @Test
  fun excludeProjectFromMinimize() {
    writeClientAndServerModules(
      serverShadowBlock =
        """
        minimize {
          exclude(project(':client'))
        }
        """
          .trimIndent()
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
        minimize {
          exclude(project(':client'))
        }
        """
          .trimIndent()
    )
    path("client/src/main/java/client/Client.java")
      .writeText(
        """
        package client;
        import junit.framework.TestCase;
        public class Client extends TestCase {
          public static void main(String[] args) {}
        }
        """
          .trimIndent()
      )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsAtLeast("client/Client.class", "server/Server.class", *junitEntries)
    }

    path("client/src/main/java/client/Client.java")
      .writeText(
        """
        package client;
        public class Client {}
        """
          .trimIndent()
      )
    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsAtLeast("client/Client.class", "server/Server.class", *junitEntries)
    }
  }

  @Issue("https://github.com/GradleUp/shadow/issues/1610")
  @Test
  fun excludeCircularDependencies() {
    val dependency = "'my:e:1.0'"
    projectScript.appendText(
      """
        dependencies {
          implementation $dependency
        }
        $shadowJarTask {
          minimize {
            exclude(dependency($dependency))
          }
        }
      """
        .trimIndent()
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

  @Issue("https://github.com/GradleUp/shadow/issues/1636")
  @Test
  fun minimizeBomDependency() {
    writeApiLibAndImplModules()
    path("impl/build.gradle")
      .appendText(
        """
        dependencies {
          api platform('my:bom:1.0')
        }
        """
          .trimIndent()
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
        minimize {
          r8 {}
        }
        """
          .trimIndent()
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
    }
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
    }
    val shadowJarUrl = outputServerShadowedJar.use { it.path.toUri().toURL() }
    URLClassLoader(arrayOf(shadowJarUrl), null).use { loader ->
      val serviceClass = loader.loadClass("service.Greeter")
      assertThat(ServiceLoader.load(serviceClass, loader).toList()).hasSize(1)
    }
  }

  @Test
  fun minimizeWithR8HonorsCustomProguardRules() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
        """
        minimize {
          r8 {
            proguardRules.add("-keep class client.Reflective { *; }")
          }
        }
        """
          .trimIndent()
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
    }
  }

  @Test
  fun minimizeWithR8UsesClasspathRules() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
        """
        minimize {
          r8 {}
        }
        """
          .trimIndent()
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
    }
  }

  @Test
  fun minimizeWithR8PreservesRepeatedLinesInClasspathRules() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
        """
        minimize {
          r8 {}
        }
        """
          .trimIndent()
    )
    path("client/src/main/resources/META-INF/proguard/client.pro")
      .writeText(
        """
        -keep class client.Reflective {
          public <init>();
        }
        -keep class client.Unused {
          public <init>();
        }
        """
          .trimIndent()
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
    }
  }

  @Test
  fun minimizeWithR8CanEnableObfuscation() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
        """
        minimize {
          r8 {
            enableObfuscation()
          }
        }
        """
          .trimIndent()
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
    }
  }

  @Test
  fun minimizeWithR8CanEnableOptimization() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverShadowBlock =
        """
        minimize {
          r8 {
            enableOptimization()
          }
        }
        """
          .trimIndent()
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
        minimize {
          exclude(project(':client'))
          r8 {}
        }
        """
          .trimIndent()
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
    }
  }

  @Test
  fun minimizeWithR8UsesJavaToolchain() {
    writeR8Repository()
    writeR8ClientAndServerModules(
      serverProjectBlock =
        """
        java {
          toolchain.languageVersion = JavaLanguageVersion.of(${JavaVersion.current().majorVersion})
        }
        """
          .trimIndent(),
      serverShadowBlock =
        """
        doFirst {
          logger.lifecycle("R8 launcher JDK " + javaLauncher.get().metadata.languageVersion.asInt())
        }
        minimize {
          r8 {}
        }
        """
          .trimIndent(),
    )

    val result = runWithSuccess(serverShadowJarPath)

    assertThat(result.output).contains("R8 launcher JDK ${JavaVersion.current().majorVersion}")
  }

  private fun writeApiLibAndImplModules() {
    settingsScript.appendText(
      """
      include 'api', 'lib', 'impl'
      """
        .trimIndent() + lineSeparator
    )
    projectScript.writeText("")

    path("lib/src/main/java/lib/LibEntity.java")
      .writeText(
        """
        package lib;
        public interface LibEntity {}
        """
          .trimIndent()
      )
    path("lib/src/main/java/lib/UnusedLibEntity.java")
      .writeText(
        """
        package lib;
        public class UnusedLibEntity implements LibEntity {}
        """
          .trimIndent()
      )
    path("lib/build.gradle")
      .writeText(
        """
        plugins {
          id 'java'
        }
        """
          .trimIndent() + lineSeparator
      )

    path("api/src/main/java/api/Entity.java")
      .writeText(
        """
        package api;
        public interface Entity {}
        """
          .trimIndent()
      )
    path("api/src/main/java/api/UnusedEntity.java")
      .writeText(
        """
        package api;
        import lib.LibEntity;
        public class UnusedEntity implements LibEntity {}
        """
          .trimIndent()
      )
    path("api/build.gradle")
      .writeText(
        """
        plugins {
          id 'java'
        }
        dependencies {
          implementation 'junit:junit:3.8.2'
          implementation project(':lib')
        }
        """
          .trimIndent() + lineSeparator
      )

    path("impl/src/main/java/impl/SimpleEntity.java")
      .writeText(
        """
        package impl;
        import api.Entity;
        public class SimpleEntity implements Entity {}
        """
          .trimIndent()
      )
    path("impl/build.gradle")
      .writeText(
        """
        ${getDefaultProjectBuildScript("java-library")}
        dependencies {
          api project(':api')
        }
        $shadowJarTask {
          minimize()
        }
      """
          .trimIndent() + lineSeparator
      )
  }

  private fun writeR8Repository() {
    settingsScript.writeText(
      settingsScript.readText().replace("mavenCentral()", "mavenCentral()\n          google()")
    )
  }

  private fun writeR8ClientAndServerModules(
    serverShadowBlock: String,
    serverProjectBlock: String = "",
  ) {
    settingsScript.appendText(
      """
      include 'client', 'server'
      """
        .trimIndent()
    )
    projectScript.writeText("")

    path("client/src/main/java/client/Used.java")
      .writeText(
        """
        package client;
        public class Used {
          public static String name() {
            return "used";
          }
        }
        """
          .trimIndent()
      )
    path("client/src/main/java/client/Unused.java")
      .writeText(
        """
        package client;
        public class Unused {}
        """
          .trimIndent()
      )
    path("client/src/main/java/client/Reflective.java")
      .writeText(
        """
        package client;
        public class Reflective {}
        """
          .trimIndent()
      )
    path("client/build.gradle")
      .writeText(
        """
        ${getDefaultProjectBuildScript("java")}
        """
          .trimIndent() + lineSeparator
      )

    path("server/src/main/java/server/Server.java")
      .writeText(
        """
        package server;
        import client.Used;
        public class Server {
          public String name() {
            return Used.name();
          }
        }
        """
          .trimIndent()
      )
    path("server/build.gradle")
      .writeText(
        """
        ${getDefaultProjectBuildScript("java")}
        $serverProjectBlock
        dependencies {
          implementation project(':client')
        }
        $shadowJarTask {
          $serverShadowBlock
        }
        """
          .trimIndent() + lineSeparator
      )
  }

  private fun writeR8ServiceModules() {
    settingsScript.appendText(
      """
      include 'service', 'server'
      """
        .trimIndent()
    )
    projectScript.writeText("")

    path("service/src/main/java/service/Greeter.java")
      .writeText(
        """
        package service;
        public interface Greeter {
          String greet();
        }
        """
          .trimIndent()
      )
    path("service/src/main/java/service/DefaultGreeter.java")
      .writeText(
        """
        package service;
        public class DefaultGreeter implements Greeter {
          public String greet() {
            return "hello";
          }
        }
        """
          .trimIndent()
      )
    path("service/src/main/resources/META-INF/services/service.Greeter")
      .writeText("service.DefaultGreeter")
    path("service/build.gradle")
      .writeText(
        """
        ${getDefaultProjectBuildScript("java")}
        """
          .trimIndent() + lineSeparator
      )

    path("server/src/main/java/server/Server.java")
      .writeText(
        """
        package server;
        public class Server {}
        """
          .trimIndent()
      )
    path("server/build.gradle")
      .writeText(
        """
        ${getDefaultProjectBuildScript("java")}
        dependencies {
          implementation project(':service')
        }
        $shadowJarTask {
          minimize {
            r8 {}
          }
        }
        """
          .trimIndent() + lineSeparator
      )
  }
}
