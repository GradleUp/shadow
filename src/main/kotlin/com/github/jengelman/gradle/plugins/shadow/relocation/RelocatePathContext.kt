package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.ShadowStats

data class RelocatePathContext(
  val path: String,
  val stats: ShadowStats,
)
