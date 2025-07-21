package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import kotlin.io.path.writeText
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class AndroidPluginTest : BasePluginTest() {
  @ParameterizedTest
  @MethodSource("androidIdsProvider")
  fun doesNotCompatAgp(pluginId: String) {
    projectScriptPath.writeText(getDefaultProjectBuildScript(plugin = pluginId))

    assertThat(runWithFailure().output).contains(
      "Shadow does not support using with AGP, you may need Android Fused Library plugin instead.",
    )
  }

  private companion object {
    @JvmStatic
    fun androidIdsProvider() = listOf(
      "com.android.application",
      "com.android.library",
      "com.android.test",
      "com.android.dynamic-feature",
    )
  }
}
