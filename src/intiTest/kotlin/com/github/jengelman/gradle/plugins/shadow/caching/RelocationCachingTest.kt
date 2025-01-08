package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import com.github.jengelman.gradle.plugins.shadow.util.useAll
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class RelocationCachingTest : BaseCachingTest() {
  /**
   * TODO: have to invistigate why `:` is necessary here.
   */
  override val shadowJarTask: String = ":" + super.shadowJarTask

  /**
   * Ensure that we get a cache miss when relocation changes and that caching works with relocation
   */
  @Test
  fun shadowJarIsCachedCorrectlyWhenRelocationIsAdded() {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent() + System.lineSeparator(),
    )
    path("src/main/java/server/Server.java").writeText(
      """
        package server;
        import junit.framework.Test;
        public class Server {}
      """.trimIndent(),
    )

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "server/Server.class",
        "junit/framework/Test.class",
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          relocate 'junit.framework', 'foo.junit.framework'
        }
      """.trimIndent(),
    )

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "server/Server.class",
        "foo/junit/framework/Test.class",
      )
      doesNotContainEntries(
        "junit/framework/Test.class",
      )
    }

    assertShadowJarIsCachedAndRelocatable()
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "server/Server.class",
        "foo/junit/framework/Test.class",
      )
      doesNotContainEntries(
        "junit/framework/Test.class",
      )
    }
  }
}
