package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

public class KnowsTask : DefaultTask() {

  @TaskAction
  public fun knows() {
    logger.info("\nNo, The Shadow Knows....")
    this::class.java.getResourceAsStream("/shadowBanner.txt")?.let {
      logger.info(it.bufferedReader().readText())
    }
  }

  public companion object {
    public const val NAME: String = "knows"
    public const val DESC: String = "Do you know who knows?"
  }
}
