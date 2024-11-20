package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.ShadowStats

data class RelocatePathContext(
  val path: String,
  val stats: ShadowStats,
) {
  class Builder {
    private var path = ""
    private var stats = ShadowStats()

    fun path(path: String): Builder = apply { this.path = path }
    fun stats(stats: ShadowStats): Builder = apply { this.stats = stats }
    fun build(): RelocatePathContext = RelocatePathContext(path, stats)
  }

  companion object {
    @JvmStatic
    fun builder(): Builder = Builder()
  }
}
