package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getBytes
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsStream
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.outputStream
import kotlin.metadata.jvm.KotlinModuleMetadata
import kotlin.metadata.jvm.UnstableMetadataApi
import org.junit.jupiter.api.Test

@OptIn(UnstableMetadataApi::class)
class KotlinModuleMetadataTransformerTest : BaseTransformerTest<KotlinModuleMetadataTransformer>() {

  @Test
  fun canTransformResource() =
    with(transformer) {
      assertThat(canTransformResource("META-INF/kotlin-stdlib.kotlin_module")).isTrue()
      assertThat(canTransformResource("foo/bar.kotlin_module")).isTrue()
      assertThat(canTransformResource("META-INF/MANIFEST.MF")).isFalse()
      assertThat(canTransformResource("foo/Bar.class")).isFalse()
    }

  @Test
  fun transformWithoutRelocator() =
    with(transformer) {
      val modulePath = "META-INF/kotlin-stdlib.kotlin_module"
      val originalBytes = requireResourceAsStream(modulePath).readBytes()
      transform(resourceContext(modulePath))

      val tempJar = createTempFile("shade.", ".jar")
      try {
        tempJar.outputStream().zipOutputStream().use { zos ->
          modifyOutputStream(zos, false)
        }
        val outputBytes = JarPath(tempJar).use { it.getBytes(modulePath) }
        assertThat(outputBytes).isEqualTo(originalBytes)
      } finally {
        tempJar.deleteExisting()
      }
    }

  @Test
  fun transformWithRelocator() =
    with(transformer) {
      val modulePath = "META-INF/kotlin-stdlib.kotlin_module"
      val originalBytes = requireResourceAsStream(modulePath).readBytes()
      val originalModule = KotlinModuleMetadata.read(originalBytes)

      val relocator = SimpleRelocator("kotlin", "my.kotlin")
      transform(resourceContext(modulePath, relocator))

      val tempJar = createTempFile("shade.", ".jar")
      try {
        tempJar.outputStream().zipOutputStream().use { zos ->
          modifyOutputStream(zos, false)
        }
        val expectedShadowPath = "META-INF/kotlin-stdlib.shadow.kotlin_module"
        val relocatedBytes = JarPath(tempJar).use { it.getBytes(expectedShadowPath) }
        assertThat(relocatedBytes).isNotNull()

        val relocatedModule = KotlinModuleMetadata.read(relocatedBytes)
        assertThat(relocatedModule.version.toString()).isEqualTo(originalModule.version.toString())

        val originalPkgParts = originalModule.kmModule.packageParts.entries
        val relocatedPkgParts = relocatedModule.kmModule.packageParts.entries
        assertThat(relocatedPkgParts).isNotEqualTo(originalPkgParts)
        assertThat(relocatedPkgParts.size).isEqualTo(originalPkgParts.size)

        relocatedPkgParts.forEachIndexed { index, (relocatedPkg, _) ->
          val (originalPkg, _) = originalPkgParts.elementAt(index)
          assertThat(relocatedPkg).isEqualTo(originalPkg.replace("kotlin", "my.kotlin"))
        }
      } finally {
        tempJar.deleteExisting()
      }
    }
}
