package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.GradleException

public class ShadowStats {
  public var totalTime: Long = 0
  public var jarStartTime: Long = 0
  public var jarEndTime: Long = 0
  public var jarCount: Int = 1
  public var processingJar: Boolean = false
  public val relocations: MutableMap<String, String> = mutableMapOf()

  public val relocationString: String
    get() {
      val maxLength = relocations.keys.map { it.length }.maxOrNull() ?: 0
      return relocations.map { (k, v) -> "$k → $v" }
        .sorted()
        .joinToString("\n")
    }

  public val jarTiming: Long
    get() = jarEndTime - jarStartTime

  public val totalTimeSecs: Double
    get() = totalTime / 1000.0

  public val averageTimePerJar: Double
    get() = totalTime / jarCount.toDouble()

  public val averageTimeSecsPerJar: Double
    get() = averageTimePerJar / 1000.0

  public val buildScanData: Map<String, String>
    get() = mapOf(
      "dependencies" to jarCount.toString(),
      "relocations" to relocationString,
    )

  public fun relocate(src: String, dst: String) {
    relocations[src] = dst
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

  override fun toString(): String {
    return buildString {
      append("*******************\n")
      append("GRADLE SHADOW STATS\n")
      append("\n")
      append("Total Jars: $jarCount (includes project)\n")
      append("Total Time: ${totalTimeSecs}s [${totalTime}ms]\n")
      append("Average Time/Jar: ${averageTimeSecsPerJar}s [${averageTimePerJar}ms]\n")
      append("*******************")
    }
  }
}
