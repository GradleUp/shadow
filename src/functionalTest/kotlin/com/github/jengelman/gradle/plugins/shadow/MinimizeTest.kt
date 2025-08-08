package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.util.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.util.containsNone
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MinimizeTest : BasePluginTest() {
  /**
   * 'api' used as api for 'impl', and depended on 'lib'. 'junit' is independent.
   * The minimize step shall remove 'junit', but not 'api'.
   * Unused classes of 'api' and theirs dependencies also shouldn't be removed.
   */
  @Test
  fun useMinimizeWithDependenciesWithApiScope() {
    writeApiLibAndImplModules()

    run(":impl:$SHADOW_JAR_TASK_NAME")

    assertThat(jarPath("impl/build/libs/impl-all.jar")).useAll {
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
   * 'api' used as api for 'impl', and 'lib' used as api for 'api'.
   * Unused classes of 'api' and 'lib' shouldn't be removed.
   */
  @Test
  fun useMinimizeWithTransitiveDependenciesWithApiScope() {
    writeApiLibAndImplModules()
    path("api/build.gradle").writeText(
      """
        plugins {
          id 'java-library'
        }
        dependencies {
          api project(':lib')
        }
      """.trimIndent(),
    )

    run(":impl:$SHADOW_JAR_TASK_NAME")

    assertThat(jarPath("impl/build/libs/impl-all.jar")).useAll {
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

  /**
   * 'Server' depends on 'Client'. 'junit' is independent.
   * The minimize shall remove 'junit'.
   */
  @Test
  fun minimizeByKeepingOnlyTransitiveDependencies() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize()
      """.trimIndent(),
    )
    path("server/src/main/java/server/Server.java").writeText(
      """
        package server;
        import client.Client;
        public class Server {
          // This is to make sure that 'Client' is not removed.
          private final String client = Client.class.getName();
        }
      """.trimIndent(),
    )

    run(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsAtLeast(
        "client/Client.class",
        "server/Server.class",
      )
      containsNone(
        "junit/framework/Test.class",
      )
    }
  }

  /**
   * 'Client', 'Server' and 'junit' are independent.
   * 'junit' is excluded from the minimize step.
   * The minimize step shall remove 'Client' but not 'junit'.
   */
  @Test
  fun excludeDependencyFromMinimize() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize {
          exclude(dependency('junit:junit:.*'))
        }
      """.trimIndent(),
    )

    run(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsAtLeast(
        "server/Server.class",
        *junitEntries,
      )
      containsNone(
        "client/Client.class",
      )
    }
  }

  /**
   * 'Client', 'Server' and 'junit' are independent.
   * Unused classes of 'client' and theirs dependencies shouldn't be removed.
   */
  @Test
  fun excludeProjectFromMinimize() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize {
          exclude(project(':client'))
        }
      """.trimIndent(),
    )

    run(serverShadowJarPath)

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
   * 'Client', 'Server' and 'junit' are independent.
   * Unused classes of 'client' and theirs dependencies shouldn't be removed.
   */
  @Test
  fun excludeProjectFromMinimizeShallNotExcludeTransitiveDependenciesThatAreUsedInSubproject() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize {
          exclude(project(':client'))
        }
      """.trimIndent(),
    )
    path("client/src/main/java/client/Client.java").writeText(
      """
        package client;
        import junit.framework.TestCase;
        public class Client extends TestCase {
          public static void main(String[] args) {}
        }
      """.trimIndent(),
    )

    run(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsAtLeast(
        "client/Client.class",
        "server/Server.class",
        *junitEntries,
      )
    }

    path("client/src/main/java/client/Client.java").writeText(
      """
        package client;
        public class Client {}
      """.trimIndent(),
    )
    run(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsAtLeast(
        "client/Client.class",
        "server/Server.class",
        *junitEntries,
      )
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun enableMinimizationByCliOption(enable: Boolean) {
    writeClientAndServerModules()

    if (enable) {
      run(serverShadowJarPath, "--minimize-jar")
    } else {
      run(serverShadowJarPath)
    }

    assertThat(outputServerShadowedJar).useAll {
      if (enable) {
        containsAtLeast(
          "server/Server.class",
          *manifestEntries,
        )
        containsNone(
          "client/Client.class",
        )
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

  private fun writeApiLibAndImplModules() {
    settingsScript.appendText(
      """
        include 'api', 'lib', 'impl'
      """.trimIndent() + lineSeparator,
    )
    projectScript.writeText("")

    path("lib/src/main/java/lib/LibEntity.java").writeText(
      """
        package lib;
        public interface LibEntity {}
      """.trimIndent(),
    )
    path("lib/src/main/java/lib/UnusedLibEntity.java").writeText(
      """
        package lib;
        public class UnusedLibEntity implements LibEntity {}
      """.trimIndent(),
    )
    path("lib/build.gradle").writeText(
      """
        plugins {
          id 'java'
        }
      """.trimIndent() + lineSeparator,
    )

    path("api/src/main/java/api/Entity.java").writeText(
      """
        package api;
        public interface Entity {}
      """.trimIndent(),
    )
    path("api/src/main/java/api/UnusedEntity.java").writeText(
      """
        package api;
        import lib.LibEntity;
        public class UnusedEntity implements LibEntity {}
      """.trimIndent(),
    )
    path("api/build.gradle").writeText(
      """
        plugins {
          id 'java'
        }
        dependencies {
          implementation 'junit:junit:3.8.2'
          implementation project(':lib')
        }
      """.trimIndent() + lineSeparator,
    )

    path("impl/src/main/java/impl/SimpleEntity.java").writeText(
      """
        package impl;
        import api.Entity;
        public class SimpleEntity implements Entity {}
      """.trimIndent(),
    )
    path("impl/build.gradle").writeText(
      """
        ${getDefaultProjectBuildScript("java-library")}
        dependencies {
          api project(':api')
        }
        $shadowJarTask {
          minimize()
        }
      """.trimIndent() + lineSeparator,
    )
  }
}
