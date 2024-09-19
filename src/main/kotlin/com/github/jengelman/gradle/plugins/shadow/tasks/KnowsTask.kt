package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class KnowsTask : DefaultTask() {
  @TaskAction
  fun knows() {
    println("\nNo, The Shadow Knows....")
    this::class.java.getResourceAsStream("/shadowBanner.txt")?.let {
      println(it.bufferedReader().readText())
    }
  }

  companion object {
    const val NAME: String = "knows"
    const val DESC: String = "Do you know who knows?"
  }
}
