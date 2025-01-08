package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.NoOpTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.useAll
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class TransformCachingTest : BaseCachingTest() {
  @Test
  fun shadowJarIsNotCachedWhenCustomTransformsAreUsed() {
    path("src/main/java/server/Server.java").writeText(
      """
        package server;
        public class Server {}
      """.trimIndent(),
    )

    projectScriptPath.appendText(
      """
        $shadowJar {
          // Use NoOpTransformer to mock a custom transformer here.
          transform(${NoOpTransformer::class.java.name}.INSTANCE)
        }
      """.trimIndent(),
    )

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class")
    }

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class")
    }
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingServiceFileTransformer() {
    path("src/main/java/server/Server.java").writeText(
      """
        package server;
        public class Server {}
      """.trimIndent(),
    )

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class")
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          transform(${ServiceFileTransformer::class.java.name}) {
            path = 'META-INF/foo'
          }
        }
      """.trimIndent(),
    )

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class")
    }

    assertShadowJarIsCachedAndRelocatable()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class")
    }

    val replaced = projectScriptPath.readText().replace("META-INF/foo", "META-INF/bar")
    projectScriptPath.writeText(replaced)

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class")
    }

    assertShadowJarIsCachedAndRelocatable()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class")
    }
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingAppendingTransformer() {
    path("src/main/resources/foo/bar.properties").writeText("foo=bar")
    path("src/main/java/server/Server.java").writeText(
      """
        package server;
        public class Server {}
      """.trimIndent(),
    )

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class")
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          transform(${AppendingTransformer::class.java.name}) {
            resource = 'foo/bar.properties'
          }
        }
      """.trimIndent(),
    )

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class", "foo/bar.properties")
    }

    assertShadowJarIsCachedAndRelocatable()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class", "foo/bar.properties")
    }

    path("src/main/resources/foo/bar.properties").toFile().delete()
    path("src/main/resources/foo/baz.properties").writeText("foo=baz")
    val replaced = projectScriptPath.readText().replace("foo/bar.properties", "foo/baz.properties")
    projectScriptPath.writeText(replaced)

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class", "foo/baz.properties")
    }

    assertShadowJarIsCachedAndRelocatable()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class", "foo/baz.properties")
    }
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingXmlAppendingTransformer() {
    path("src/main/resources/foo/bar.xml").writeText("<foo>bar</foo>")
    path("src/main/java/server/Server.java").writeText(
      """
        package server;
        public class Server {}
      """.trimIndent(),
    )

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class")
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          transform(${XmlAppendingTransformer::class.java.name}) {
            resource = 'foo/bar.xml'
          }
        }
      """.trimIndent(),
    )

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class", "foo/bar.xml")
    }

    assertShadowJarIsCachedAndRelocatable()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class", "foo/bar.xml")
    }

    path("src/main/resources/foo/bar.xml").toFile().delete()
    path("src/main/resources/foo/baz.xml").writeText("<foo>baz</foo>")
    val replaced = projectScriptPath.readText().replace("foo/bar.xml", "foo/baz.xml")
    projectScriptPath.writeText(replaced)

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class", "foo/baz.xml")
    }

    assertShadowJarIsCachedAndRelocatable()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class", "foo/baz.xml")
    }
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingGroovyExtensionModuleTransformer() {
    path("src/main/java/server/Server.java").writeText(
      """
        package server;
        public class Server {}
      """.trimIndent(),
    )

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class")
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          transform(${GroovyExtensionModuleTransformer::class.java.name})
        }
      """.trimIndent(),
    )

    assertShadowJarExecutes()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class")
    }

    assertShadowJarIsCachedAndRelocatable()
    assertThat(outputShadowJar).useAll {
      containsEntries("server/Server.class")
    }
  }
}
