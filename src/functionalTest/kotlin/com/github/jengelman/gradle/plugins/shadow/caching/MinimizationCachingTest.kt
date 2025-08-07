package com.github.jengelman.gradle.plugins.shadow.caching

import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class MinimizationCachingTest : BaseCachingTest() {
  override val outputShadowJar: JarPath get() = outputServerShadowJar

  @Test
  fun minimizeChanged() {
    taskPath = serverShadowJarTask

    writeClientAndServerModules()
    path("server/src/main/java/server/Server.java").writeText(
      """
        package server;
        public class Server {}
      """.trimIndent(),
    )

    assertCompositeExecutions {
      containsOnly(
        "client/",
        "server/",
        "client/Client.class",
        "server/Server.class",
        *junitEntries,
        *manifestEntries,
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
      containsOnly(
        "server/",
        "server/Server.class",
        *junitEntries,
        *manifestEntries,
      )
    }
  }
}
