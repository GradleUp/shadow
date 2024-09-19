package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.ShadowStats

data class RelocateClassContext(
  val className: String,
  val stats: ShadowStats,
)
