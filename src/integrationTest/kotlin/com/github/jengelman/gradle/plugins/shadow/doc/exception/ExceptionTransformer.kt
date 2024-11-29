package com.github.jengelman.gradle.plugins.shadow.doc.exception

class ExceptionTransformer(
  private val sourceClassName: String,
  private val sourceFileName: String,
  private val lineNumber: Int,
) {
  fun transform(throwable: Throwable, offset: Int): Throwable {
    var errorLine = 0
    val frame = throwable.stackTrace.find { it.fileName == sourceClassName }
      ?: throwable.stackTrace.find { it.fileName == "Example.java" }
    if (frame != null) {
      errorLine = frame.lineNumber
    }
    errorLine -= offset
    throwable.stackTrace = buildList {
      add(StackTraceElement(sourceClassName, "javadoc", sourceFileName, lineNumber + errorLine))
      addAll(throwable.stackTrace)
    }.toTypedArray()
    return throwable
  }
}
