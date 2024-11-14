package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.GradleException

class ShadowStats {
    var totalTime: Long = 0
    var jarStartTime: Long = 0
    var jarEndTime: Long = 0
    var jarCount: Int = 1
    var processingJar: Boolean = false
    val relocations: MutableMap<String, String> = mutableMapOf()

    val relocationString: String
        get() {
            val maxLength = relocations.keys.map { it.length }.maxOrNull() ?: 0
            return relocations.map { (k, v) -> "$k → $v" }
                .sorted()
                .joinToString("\n")
        }

    val jarTiming: Long
        get() = jarEndTime - jarStartTime

    val totalTimeSecs: Double
        get() = totalTime / 1000.0

    val averageTimePerJar: Double
        get() = totalTime / jarCount.toDouble()

    val averageTimeSecsPerJar: Double
        get() = averageTimePerJar / 1000.0

    val buildScanData: Map<String, String>
        get() = mapOf(
            "dependencies" to jarCount.toString(),
            "relocations" to relocationString,
        )

    fun relocate(src: String, dst: String) {
        relocations[src] = dst
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
