package com.github.jengelman.gradle.plugins.shadow.doc.exception

class ExceptionTransformer(
  private val className: String,
  private val methodName: String,
  private val sourcePath: String,
  private val lineNumber: Int,
) {
  fun transform(throwable: Throwable): Throwable {
    throwable.stackTrace = buildList {
      add(StackTraceElement(className, methodName, sourcePath, lineNumber))
      addAll(throwable.stackTrace)
    }.toTypedArray()
    return throwable
  }
}
