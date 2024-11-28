package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executor

class CompileException(
  cause: Throwable?,
  val lineNo: Int,
) : RuntimeException(cause) {
  private companion object {
    private const val serialVersionUID: Long = 0
  }
}
