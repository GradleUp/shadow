package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.GradleException

class ShadowStats {
  private val relocations = mutableMapOf<String, String>()

  var totalTime: Long = 0
  var jarStartTime: Long = 0
  var jarEndTime: Long = 0
  var jarCount: Int = 0
  var processingJar: Boolean = false

  inline val jarTiming: Long get() = jarEndTime - jarStartTime
  inline val totalTimeSecs: Double get() = totalTime / 1000.0
  inline val averageTimePerJar: Double get() = totalTime / jarCount.toDouble()
  inline val averageTimeSecsPerJar: Double get() = averageTimePerJar / 1000

  fun relocate(src: String, dest: String) {
    relocations[src] = dest
  }

  fun getRelocationString(): String {
    return relocations.entries.joinToString("\n") { "${it.key} → ${it.value}" }
  }

  fun startJar() {
    if (processingJar) throw GradleException("Can only time one entry at a time")
    processingJar = true
    jarStartTime = System.currentTimeMillis()
  }

  fun finishJar() {
    if (processingJar) {
      jarEndTime = System.currentTimeMillis()
      jarCount++
      totalTime += jarTiming
      processingJar = false
    }
  }

  fun printStats() {
    println(this)
  }

  override fun toString(): String = buildString {
    append("*******************\n")
    append("GRADLE SHADOW STATS\n")
    append("\n")
    append("Total Jars: $jarCount (includes project)\n")
    append("Total Time: ${totalTimeSecs}s [${totalTime}ms]\n")
    append("Average Time/Jar: ${averageTimeSecsPerJar}s [${averageTimePerJar}ms]\n")
    append("*******************")
  }
}
