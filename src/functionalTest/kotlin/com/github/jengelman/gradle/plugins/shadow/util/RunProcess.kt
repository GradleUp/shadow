package com.github.jengelman.gradle.plugins.shadow.util

fun runProcess(
  vararg commands: String,
): String {
  val process = ProcessBuilder(commands.toList()).start()
  val exitCode = process.waitFor()

  val err = process.errorStream.bufferedReader().use { it.readText() }
  val out = process.inputStream.bufferedReader().use { it.readText() }

  check(exitCode == 0 && err.isEmpty()) {
    "Error occurred when running command line: $err"
  }

  return out
}

val isWindows = System.getProperty("os.name").startsWith("Windows")
