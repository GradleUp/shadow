package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.ShadowStats

public data class RelocatePathContext(
  val path: String,
  val stats: ShadowStats,
) {
  public class Builder {
    private var path: String = ""
    private var stats: ShadowStats = ShadowStats()

    public fun path(path: String): Builder = apply { this.path = path }
    public fun stats(stats: ShadowStats): Builder = apply { this.stats = stats }
    public fun build(): RelocatePathContext = RelocatePathContext(path, stats)
  }

  public companion object {
    @JvmStatic
    public fun builder(): Builder = Builder()
  }
}
