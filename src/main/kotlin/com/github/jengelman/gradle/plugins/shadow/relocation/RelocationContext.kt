package com.github.jengelman.gradle.plugins.shadow.relocation

@JvmInline
public value class RelocateClassContext(
  public val className: String,
)

@JvmInline
public value class RelocatePathContext(
  public val path: String,
)
