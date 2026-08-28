package com.github.jengelman.gradle.plugins.shadow.tasks

/** A tool that can minimize a shadowed JAR. */
public enum class MinimizeTool {
  /** Shadow's default, simple dependency analyzer. */
  DEPENDENCY_ANALYZER,

  /** R8 classfile shrinking. */
  R8,
}
