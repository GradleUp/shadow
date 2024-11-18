package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.ShadowStats

data class RelocateClassContext(
    val className: String,
    val stats: ShadowStats,
) {
    class Builder {
        private var className = ""
        private var stats = ShadowStats()

        fun className(className: String): Builder = apply { this.className = className }
        fun stats(stats: ShadowStats): Builder = apply { this.stats = stats }
        fun build(): RelocateClassContext = RelocateClassContext(className, stats)
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
