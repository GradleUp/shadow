package com.github.jengelman.gradle.plugins.shadow

import groovy.util.logging.Slf4j
import org.gradle.api.GradleException

@Slf4j
class ShadowStats {

    long totalTime
    long jarStartTime
    long jarEndTime
    int jarCount
    boolean processingJar

    void startJar() {
        if (processingJar) throw new GradleException("Can only time one entry at a time")
        processingJar = true
        jarStartTime = System.currentTimeMillis()
    }

    void finishJar() {
        if (processingJar) {
            jarEndTime = System.currentTimeMillis()
            totalTime += jarTiming
            processingJar = false
        }
    }

    void printStats() {
        println ""
        println "*******************"
        println "GRADLE SHADOW STATS"
        println ""
        println "Total Jars: $jarCount"
        println "Total Time: ${totalTimeSecs}s [${totalTime}ms]"
        println "Average Time/Jar: ${averageTimeSecsPerJar}s [${averageTimePerJar}ms]"
        println "*******************"
        println ""
    }

    long getJarTiming() {
        jarEndTime - jarStartTime
    }

    double getTotalTimeSecs() {
        totalTime / 1000
    }

    double getAverageTimePerJar() {
        totalTime / jarCount
    }

    double getAverageTimeSecsPerJar() {
        averageTimePerJar / 1000
    }
}
