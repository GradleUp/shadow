package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.classLoader
import com.github.jengelman.gradle.plugins.shadow.testkit.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.testkit.containsNone
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.loadClass
import kotlin.io.path.appendText
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText
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
      classLoader {
        loadClass("impl.SimpleEntity")
        loadClass("api.UnusedEntity")
        loadClass("lib.LibEntity")
      }
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
      classLoader {
        loadClass("impl.SimpleEntity")
        loadClass("api.UnusedEntity")
        loadClass("lib.UnusedLibEntity")
      }
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
      classLoader {
        loadClass("client.Client")
        loadClass("server.Server")
      }
    }
  }

  @Test
  fun minimizeSourcesJar() {
    path("src/main/java/my/Main.java")
      .writeText(
        """
        |package my;
        |import h.H;
        |import k.CustomUtils;
        |public class Main {
        |  H h;
        |  CustomUtils u;
        |}
        """
          .trimMargin()
      )
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'my:h:1.0'
      |  implementation 'my:k:1.0'
      |}
      |$shadowJarTask {
      |  minimize()
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsAtLeast("my/Main.class", "h/H.class", "k/CustomUtils.class")
      containsNone("h/UnusedH.class", "k/CustomUnusedUtils.class")
    }
    assertThat(outputShadowedSourcesJar).useAll {
      containsAtLeast("my/Main.java", "h/H.java", "k/Utils.kt")
      containsNone("h/UnusedH.java", "k/UnusedUtils.kt")
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
      classLoader {
        loadClass("server.Server")
        loadClass("junit.framework.Test")
      }
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
      classLoader {
        loadClass("client.Client")
        loadClass("server.Server")
        loadClass("junit.framework.Test")
      }
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
      classLoader {
        loadClass("client.Client")
        loadClass("server.Server")
        loadClass("junit.framework.TestCase")
      }
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
      classLoader {
        loadClass("client.Client")
        loadClass("server.Server")
        loadClass("junit.framework.Test")
      }
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
      classLoader {
        loadClass("server.Server")
        if (!enable) {
          loadClass("client.Client")
        }
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

  private fun writeApiLibAndImplModules() {
    settingsScript.appendText(
      """
      |include 'api', 'lib', 'impl'
      |"""
        .trimMargin()
    )
    projectScript.deleteExisting()

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
}
