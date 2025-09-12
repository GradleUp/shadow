package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.io.path.readBytes
import kotlin.metadata.jvm.KotlinModuleMetadata
import kotlin.metadata.jvm.UnstableMetadataApi
import org.junit.jupiter.api.Test

@OptIn(UnstableMetadataApi::class)
class KotlinModuleMetadataAdapterTest {

  @Test
  fun moduleMetadataFileNotChanged() {
    val expected = requireResourceAsPath("META-INF/kotlin-stdlib.kotlin_module").readBytes()
    val adapter = moshi.adapter(KotlinModuleMetadata::class.java)
    val json = adapter.toJson(KotlinModuleMetadata.read(expected))
    val actual = requireNotNull(adapter.fromJson(json)).write()
    assertThat(actual).isEqualTo(expected)
  }
}
