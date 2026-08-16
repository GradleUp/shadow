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
import com.github.jengelman.gradle.plugins.shadow.testkit.Arguments
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getBytes
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import com.github.jengelman.gradle.plugins.shadow.testkit.runTest
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import de.infix.testBalloon.framework.core.testSuite
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.Collections
import java.util.jar.JarInputStream
import kotlin.io.path.createTempFile
import kotlin.io.path.writeBytes
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE
import org.apache.tools.zip.ZipOutputStream

val Log4j2PluginsCacheFileTransformerTests by testSuite {
  runTests(::Log4j2PluginsCacheFileTransformerTest)

  for ((pattern: String, shadedPattern: String, expected: String) in
    Log4j2PluginsCacheFileTransformerTest.relocationProvider) {
    runTest(
      "relocations_${pattern}_${shadedPattern}",
      ::Log4j2PluginsCacheFileTransformerTest,
    ) {
      relocations(pattern, shadedPattern, expected)
    }
  }
}

/**
 * Modified from
 * [org.apache.logging.log4j.maven.plugins.shade.transformer.Log4j2PluginCacheFileTransformerTest.java](https://github.com/apache/logging-log4j-transform/blob/main/log4j-transform-maven-shade-plugin-extensions/src/test/java/org/apache/logging/log4j/maven/plugins/shade/transformer/Log4j2PluginCacheFileTransformerTest.java).
 */
private class Log4j2PluginsCacheFileTransformerTest :
  BaseTransformerTest<Log4j2PluginsCacheFileTransformer>() {
  fun canTransformResource() =
    with(transformer) {
      assertThat(canTransformResource("")).isFalse()
      assertThat(canTransformResource(".")).isFalse()
      assertThat(canTransformResource("tmp.dat")).isFalse()
      assertThat(canTransformResource("$PLUGIN_CACHE_FILE.tmp")).isFalse()
      assertThat(canTransformResource("tmp/$PLUGIN_CACHE_FILE")).isFalse()
      assertThat(canTransformResource(PLUGIN_CACHE_FILE)).isTrue()
    }

  fun relocateClassesInsideDatFile() =
    with(transformer) {
      val relocator = SimpleRelocator("org.apache.logging", "new.location.org.apache.logging")
      transform(context(relocator))
      assertThat(hasTransformedResource()).isTrue()

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, true)
      }

      val tempDat = createTempFile(directory = tempDir, suffix = ".dat")
      tempDat.writeBytes(JarPath(tempJar).use { it.getBytes(PLUGIN_CACHE_FILE) })

      val cache = PluginCache()
      cache.loadCacheFiles(Collections.enumeration(listOf(tempDat.toUri().toURL())))

      assertThat(cache.getCategory("lookup")["date"]?.className)
        .isEqualTo("new.location.org.apache.logging.log4j.core.lookup.DateLookup")
    }

  // #427
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
              isNotEqualTo(-2114104185)
              isEqualTo(1911442937)
            }
            break
          }
        }
      }
    }

  fun relocations(pattern: String, shadedPattern: String, expected: String) =
    with(transformer) {
      val aggregator =
        PluginCache().apply { loadCacheFiles(Collections.enumeration(listOf(pluginCacheUrl))) }
      transform(context(SimpleRelocator(pattern, shadedPattern)))
      relocatePlugins(aggregator)

      for (pluginEntryMap in aggregator.allCategories.values) {
        for (entry in pluginEntryMap.values) {
          assertThat(entry.className).startsWith(expected)
        }
      }
    }

  companion object {
    val pluginCacheUrl: URL = requireResourceAsPath(PLUGIN_CACHE_FILE).toUri().toURL()

    fun context(vararg relocators: Relocator): TransformerContext {
      return resourceContext(PLUGIN_CACHE_FILE, relocators = relocators)
    }

    val relocationProvider =
      listOf(
        Arguments.of(
          "org.apache.logging",
          "new.location.org.apache.logging",
          "new.location.org.apache.logging",
        ),
        Arguments.of("com.apache.logging", "new.location.com.apache.logging", "org.apache.logging"),
      )
  }
}
