package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.ShadowStats

data class RelocateClassContext(
  val className: String,
  val stats: ShadowStats,
) {
  class Builder {
    private var className: String? = null
    private var stats: ShadowStats? = null

    fun className(className: String) = apply { this.className = className }

    fun stats(stats: ShadowStats) = apply { this.stats = stats }

    fun build(): RelocateClassContext {
      return RelocateClassContext(
        requireNotNull(className),
        requireNotNull(stats),
      )
    }
  }

  companion object {
    @JvmStatic
    fun builder() = Builder()
  }
}
