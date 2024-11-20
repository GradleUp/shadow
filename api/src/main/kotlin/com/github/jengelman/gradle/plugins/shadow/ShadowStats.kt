package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.GradleException

open class ShadowStats {
  open var totalTime: Long = 0
  open var jarStartTime: Long = 0
  open var jarEndTime: Long = 0
  open var jarCount: Int = 1
  open var processingJar: Boolean = false
  open val relocations: MutableMap<String, String> = mutableMapOf()

  open val relocationString: String
    get() {
      return relocations.map { (k, v) -> "$k → $v" }
        .sorted()
        .joinToString("\n")
    }

  open val jarTiming: Long
    get() = jarEndTime - jarStartTime

  open val totalTimeSecs: Double
    get() = totalTime / 1000.0

  open val averageTimePerJar: Double
    get() = totalTime / jarCount.toDouble()

  open val averageTimeSecsPerJar: Double
    get() = averageTimePerJar / 1000.0

  open val buildScanData: Map<String, String>
    get() = mapOf(
      "dependencies" to jarCount.toString(),
      "relocations" to relocationString,
    )

  open fun relocate(src: String, dst: String) {
    relocations[src] = dst
  }

  open fun startJar() {
    if (processingJar) throw GradleException("Can only time one entry at a time")
    processingJar = true
    jarStartTime = System.currentTimeMillis()
  }

  open fun finishJar() {
    if (processingJar) {
      jarEndTime = System.currentTimeMillis()
      jarCount++
      totalTime += jarTiming
      processingJar = false
    }
  }

  open fun printStats() {
    println(this)
  }

  override fun toString(): String {
    return """
      *******************
      GRADLE SHADOW STATS

      Total Jars: $jarCount (includes project)
      Total Time: ${totalTimeSecs}s [${totalTime}ms]
      Average Time/Jar: ${averageTimeSecsPerJar}s [${averageTimePerJar}ms]
      *******************
    """.trimIndent()
  }
}
