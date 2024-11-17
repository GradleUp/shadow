package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.GradleException

public open class ShadowStats {
  public open var totalTime: Long = 0
  public open var jarStartTime: Long = 0
  public open var jarEndTime: Long = 0
  public open var jarCount: Int = 1
  public open var processingJar: Boolean = false
  public open val relocations: MutableMap<String, String> = mutableMapOf()

  public open val relocationString: String
    get() {
      return relocations.map { (k, v) -> "$k → $v" }
        .sorted()
        .joinToString("\n")
    }

  public open val jarTiming: Long
    get() = jarEndTime - jarStartTime

  public open val totalTimeSecs: Double
    get() = totalTime / 1000.0

  public open val averageTimePerJar: Double
    get() = totalTime / jarCount.toDouble()

  public open val averageTimeSecsPerJar: Double
    get() = averageTimePerJar / 1000.0

  public open val buildScanData: Map<String, String>
    get() = mapOf(
      "dependencies" to jarCount.toString(),
      "relocations" to relocationString,
    )

  public open fun relocate(src: String, dst: String) {
    relocations[src] = dst
  }

  public open fun startJar() {
    if (processingJar) throw GradleException("Can only time one entry at a time")
    processingJar = true
    jarStartTime = System.currentTimeMillis()
  }

  public open fun finishJar() {
    if (processingJar) {
      jarEndTime = System.currentTimeMillis()
      jarCount++
      totalTime += jarTiming
      processingJar = false
    }
  }

  public open fun printStats() {
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
