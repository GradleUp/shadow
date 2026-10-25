package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import assertk.fail
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getBytes
import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer.Companion.PLUGIN_CACHE_FILE
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.URL
import java.util.Collections
import java.util.jar.JarInputStream
import kotlin.io.path.createTempFile
import kotlin.io.path.writeBytes
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor
import org.apache.tools.zip.ZipOutputStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Modified from
 * [org.apache.logging.log4j.maven.plugins.shade.transformer.Log4j2PluginCacheFileTransformerTest.java](https://github.com/apache/logging-log4j-transform/blob/main/log4j-transform-maven-shade-plugin-extensions/src/test/java/org/apache/logging/log4j/maven/plugins/shade/transformer/Log4j2PluginCacheFileTransformerTest.java).
 */
class Log4j2PluginsCacheFileTransformerTest :
  BaseTransformerTest<Log4j2PluginsCacheFileTransformer>() {
  @Test
  fun canTransformResource() =
    with(transformer) {
      assertThat(canTransformResource("")).isFalse()
      assertThat(canTransformResource(".")).isFalse()
      assertThat(canTransformResource("tmp.dat")).isFalse()
      assertThat(canTransformResource("$PLUGIN_CACHE_FILE.tmp")).isFalse()
      assertThat(canTransformResource("tmp/$PLUGIN_CACHE_FILE")).isFalse()
      assertThat(canTransformResource(PLUGIN_CACHE_FILE)).isTrue()
    }

  @Test
  fun pluginCacheFilePathMatchesLog4j() {
    assertThat(PLUGIN_CACHE_FILE).isEqualTo(PluginProcessor.PLUGIN_CACHE_FILE)
  }

  @Test
  fun relocateClassesInsideDatFile() =
    with(transformer) {
      val relocator = SimpleRelocator("org.apache.logging", "new.location.org.apache.logging")
      transform(context(relocator))
      assertThat(hasTransformedResource()).isTrue()

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, true)
      }

      val cache = loadPluginCache(JarPath(tempJar).use { it.getBytes(PLUGIN_CACHE_FILE) })

      assertThat(cache.getCategory("lookup")["date"]?.className)
        .isEqualTo("new.location.org.apache.logging.log4j.core.lookup.DateLookup")
    }

  @Test // #427
  fun transformAndModifyOutputStream() =
    with(transformer) {
      assertThat(hasTransformedResource()).isFalse()

      transform(context())
      assertThat(hasTransformedResource()).isTrue()
      transform(context())
      assertThat(hasTransformedResource()).isTrue()

      val jarBuff = ByteArrayOutputStream()
      ZipOutputStream(jarBuff).use { modifyOutputStream(it, false) }
      JarInputStream(jarBuff.toByteArray().inputStream()).use { inputStream ->
        while (true) {
          val jarEntry = inputStream.nextJarEntry
          if (jarEntry == null) {
            fail("No expected resource in the output jar.")
          } else if (jarEntry.name == PLUGIN_CACHE_FILE) {
            assertThat(inputStream.readAllBytes().contentHashCode()).all {
              // Hash of the original plugin cache file.
              isNotEqualTo(-2114104185)
              isEqualTo(1911442937)
            }
            break
          }
        }
      }
    }

  @Test
  fun mergeSameAsLog4jPluginCache() =
    with(transformer) {
      val one =
        datBytes(
          "Lookup" to listOf(entry("date", "foo.DateLookup"), entry("env", "foo.EnvLookup")),
          "Converter" to listOf(entry("message", "foo.MessageConverter")),
        )
      val two =
        datBytes(
          "lookup" to
            listOf(entry("date", "bar.DateLookup"), entry("java", "bar.JavaLookup", true)),
          "Core" to listOf(entry("console", "bar.ConsoleAppender", defer = true)),
        )
      transform(TransformerContext(PLUGIN_CACHE_FILE, one.inputStream()))
      transform(TransformerContext(PLUGIN_CACHE_FILE, two.inputStream()))

      val actual = transformToJar().use { it.getBytes(PLUGIN_CACHE_FILE) }

      val expected =
        ByteArrayOutputStream().also { output ->
          PluginCache().apply {
            loadCacheFiles(Collections.enumeration(listOf(one, two).map(::datUrl)))
            writeCache(output)
          }
        }
      assertThat(actual.contentEquals(expected.toByteArray())).isTrue()
      assertThat(loadPluginCache(actual).getCategory("lookup")["date"]?.className)
        .isEqualTo("foo.DateLookup")
    }

  @ParameterizedTest
  @MethodSource("relocationProvider")
  fun relocations(pattern: String, shadedPattern: String, expected: String) =
    with(transformer) {
      transform(context(SimpleRelocator(pattern, shadedPattern)))

      val cache = loadPluginCache(transformToJar().use { it.getBytes(PLUGIN_CACHE_FILE) })

      for (pluginEntryMap in cache.allCategories.values) {
        for (entry in pluginEntryMap.values) {
          assertThat(entry.className).startsWith(expected)
        }
      }
    }

  private fun loadPluginCache(bytes: ByteArray): PluginCache {
    return PluginCache().apply { loadCacheFiles(Collections.enumeration(listOf(datUrl(bytes)))) }
  }

  /** Writes the bytes to a temp file to avoid JarURLConnection locking jars on Windows. */
  private fun datUrl(bytes: ByteArray): URL {
    val tempDat = createTempFile(directory = tempDir, suffix = ".dat")
    tempDat.writeBytes(bytes)
    return tempDat.toUri().toURL()
  }

  private class Entry(
    val key: String,
    val className: String,
    val printable: Boolean,
    val defer: Boolean,
  )

  private companion object {
    fun entry(key: String, className: String, printable: Boolean = false, defer: Boolean = false) =
      Entry(key, className, printable, defer)

    /** Writes the raw `Log4j2Plugins.dat` format, keeping the category names as they are. */
    fun datBytes(vararg categories: Pair<String, List<Entry>>): ByteArray {
      val bytes = ByteArrayOutputStream()
      DataOutputStream(bytes).use { output ->
        output.writeInt(categories.size)
        categories.forEach { (category, entries) ->
          output.writeUTF(category)
          output.writeInt(entries.size)
          entries.forEach {
            output.writeUTF(it.key)
            output.writeUTF(it.className)
            output.writeUTF(it.key)
            output.writeBoolean(it.printable)
            output.writeBoolean(it.defer)
          }
        }
      }
      return bytes.toByteArray()
    }

    fun context(vararg relocators: Relocator): TransformerContext {
      return resourceContext(PLUGIN_CACHE_FILE, relocators = relocators)
    }

    @JvmStatic
    fun relocationProvider() =
      listOf(
        // test with matching relocator
        Arguments.of(
          "org.apache.logging",
          "new.location.org.apache.logging",
          "new.location.org.apache.logging",
        ),
        // test without matching relocator
        Arguments.of("com.apache.logging", "new.location.com.apache.logging", "org.apache.logging"),
      )
  }
}
