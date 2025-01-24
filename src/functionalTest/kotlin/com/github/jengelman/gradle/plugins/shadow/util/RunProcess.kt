package com.github.jengelman.gradle.plugins.shadow.util

fun runProcess(vararg args: String): String {
  val process = ProcessBuilder(preCommands + args).start()
  val exitCode = process.waitFor()

  val err = process.errorStream.readBytes().toString(Charsets.UTF_8)
  val out = process.inputStream.readBytes().toString(Charsets.UTF_8)

  if (exitCode != 0 || err.isNotEmpty()) {
    error("Error occurred when running command line: $err")
  }

  return out
}

val isWindows = System.getProperty("os.name").startsWith("Windows")
private val preCommands = if (isWindows) listOf("cmd", "/c") else listOf("sh")
