package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.getBytes
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsStream
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
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
    }

  @Test
  fun transformWithoutRelocator() =
    with(transformer) {
      val modulePath = "META-INF/kotlin-stdlib.kotlin_module"
      val originalBytes = requireResourceAsStream(modulePath).readBytes()
      transform(resourceContext(modulePath))

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }
      val outputBytes = JarPath(tempJar).use { it.getBytes(modulePath) }
      assertThat(outputBytes).isEqualTo(originalBytes)
    }

  @Test // #843
  fun transformWithRelocator() =
    with(transformer) {
      val modulePath = "META-INF/kotlin-stdlib.kotlin_module"
      val originalBytes = requireResourceAsStream(modulePath).readBytes()
      val originalModule = KotlinModuleMetadata.read(originalBytes)

      val relocator = SimpleRelocator("kotlin", "my.kotlin")
      transform(resourceContext(modulePath, relocator))

      tempJar.zipOutputStream().use { zos ->
        modifyOutputStream(zos, false)
      }
      val expectedShadowPath = "META-INF/kotlin-stdlib.shadow.kotlin_module"
      val relocatedBytes = JarPath(tempJar).use { it.getBytes(expectedShadowPath) }
      assertThat(relocatedBytes).isNotNull()

      val relocatedModule = KotlinModuleMetadata.read(relocatedBytes)
      assertThat(relocatedModule.version.toString()).isEqualTo(originalModule.version.toString())

      // No implementation for writing the optionalAnnotationClasses property yet.
      // https://github.com/JetBrains/kotlin/blob/81502985ae0a2f5b21e121ffc180c3f4dd467e17/libraries/kotlinx-metadata/jvm/src/kotlin/metadata/jvm/KotlinModuleMetadata.kt#L71
      assertThat(relocatedModule.kmModule.optionalAnnotationClasses).isEmpty()

      val originalPkgParts = originalModule.kmModule.packageParts.entries
      val relocatedPkgParts = relocatedModule.kmModule.packageParts.entries
      assertThat(relocatedPkgParts).isNotEqualTo(originalPkgParts)
      assertThat(relocatedPkgParts.size).isEqualTo(originalPkgParts.size)

      relocatedPkgParts.forEachIndexed { index, (relocatedPkg, relocatedParts) ->
        val (originalPkg, originalParts) = originalPkgParts.elementAt(index)
        assertThat(relocatedPkg).isNotEqualTo(originalPkg)
        assertThat(relocatedPkg).isEqualTo(originalPkg.replace("kotlin", "my.kotlin"))

        if (originalParts.fileFacades.isEmpty()) {
          assertThat(relocatedParts.fileFacades).isEmpty()
        } else {
          assertThat(relocatedParts.fileFacades).isNotEmpty()
          assertThat(relocatedParts.fileFacades).isNotEqualTo(originalParts.fileFacades)
          assertThat(relocatedParts.fileFacades)
            .isEqualTo(originalParts.fileFacades.map { it.replace("kotlin/", "my/kotlin/") })
        }

        if (originalParts.multiFileClassParts.isEmpty()) {
          assertThat(relocatedParts.multiFileClassParts).isEmpty()
        } else {
          assertThat(relocatedParts.multiFileClassParts).isNotEmpty()
          assertThat(relocatedParts.multiFileClassParts)
            .isNotEqualTo(originalParts.multiFileClassParts)
          assertThat(relocatedParts.multiFileClassParts)
            .isEqualTo(
              originalParts.multiFileClassParts.entries.associateTo(mutableMapOf()) { (name, facade)
                ->
                name.replace("kotlin/", "my/kotlin/") to facade.replace("kotlin/", "my/kotlin/")
              }
            )
        }
      }
    }
}
