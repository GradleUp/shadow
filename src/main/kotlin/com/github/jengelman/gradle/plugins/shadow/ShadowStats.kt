package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.GradleException

public class ShadowStats {
  private val relocations = mutableMapOf<String, String>()

  public var totalTime: Long = 0
  public var jarStartTime: Long = 0
  public var jarEndTime: Long = 0
  public var jarCount: Int = 0
  public var processingJar: Boolean = false

  public inline val jarTiming: Long get() = jarEndTime - jarStartTime
  public inline val totalTimeSecs: Double get() = totalTime / 1000.0
  public inline val averageTimePerJar: Double get() = totalTime / jarCount.toDouble()
  public inline val averageTimeSecsPerJar: Double get() = averageTimePerJar / 1000

  public fun relocate(src: String, dest: String) {
    relocations[src] = dest
  }

  public fun getRelocationString(): String {
    return relocations.entries.joinToString("\n") { "${it.key} → ${it.value}" }
  }

  public fun startJar() {
    if (processingJar) throw GradleException("Can only time one entry at a time")
    processingJar = true
    jarStartTime = System.currentTimeMillis()
  }

  public fun finishJar() {
    if (processingJar) {
      jarEndTime = System.currentTimeMillis()
      jarCount++
      totalTime += jarTiming
      processingJar = false
    }
  }

  public fun printStats() {
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
