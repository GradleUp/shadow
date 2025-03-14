package com.github.jengelman.gradle.plugins.shadow.caching

import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import com.github.jengelman.gradle.plugins.shadow.util.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.util.containsNone
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class MinimizationCachingTest : BaseCachingTest() {
  override val taskPath: String = serverShadowJarTask
  override val outputShadowJar: JarPath get() = outputServerShadowJar

  @Test
  fun shadowJarIsCachedCorrectlyWhenMinimizationIsAdded() {
    writeClientAndServerModules()
    path("server/src/main/java/server/Server.java").writeText(
      """
        package server;
        public class Server {}
      """.trimIndent(),
    )

    assertCompositeExecutions {
      containsAtLeast(
        "server/Server.class",
        "client/Client.class",
        *junitEntries,
      )
    }

    path("server/build.gradle").appendText(
      """
        $shadowJar {
          minimize {
            exclude(dependency('junit:junit:.*'))
          }
        }
      """.trimIndent(),
    )

    assertCompositeExecutions {
      containsAtLeast(
        "server/Server.class",
        *junitEntries,
      )
      containsNone(
        "client/Client.class",
      )
    }
  }
}
