package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.ShadowStats

public data class RelocateClassContext(
  val className: String,
  val stats: ShadowStats,
) {
  public class Builder {
    private var className = ""
    private var stats = ShadowStats()

    public fun className(className: String): Builder = apply { this.className = className }
    public fun stats(stats: ShadowStats): Builder = apply { this.stats = stats }
    public fun build(): RelocateClassContext = RelocateClassContext(className, stats)
  }

  public companion object {
    @JvmStatic
    public fun builder(): Builder = Builder()
  }
}
