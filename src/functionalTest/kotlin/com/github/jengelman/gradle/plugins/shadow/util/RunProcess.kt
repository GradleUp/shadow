package com.github.jengelman.gradle.plugins.shadow.util

fun runProcess(
  vararg commands: String,
): String {
  val process = ProcessBuilder(commands.toList()).start()
  val exitCode = process.waitFor()

  val err = process.errorStream.readBytes().toString(Charsets.UTF_8)
  val out = process.inputStream.readBytes().toString(Charsets.UTF_8)

  if (exitCode != 0 || err.isNotEmpty()) {
    error("Error occurred when running command line: $err")
  }

  return out
}

val isWindows = System.getProperty("os.name").startsWith("Windows")
