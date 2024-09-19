package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.ShadowStats

data class RelocatePathContext(
  val path: String,
  val stats: ShadowStats,
) {
  class Builder {
    private var path: String? = null
    private var stats: ShadowStats? = null

    fun path(path: String) = apply { this.path = path }

    fun stats(stats: ShadowStats) = apply { this.stats = stats }

    fun build(): RelocateClassContext {
      return RelocateClassContext(
        requireNotNull(path),
        requireNotNull(stats),
      )
    }
  }

  companion object {
    @JvmStatic
    fun builder() = Builder()
  }
}
