package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.internal.requireResourceAsText
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class KnowsTask : DefaultTask() {

  @TaskAction
  fun knows() {
    logger.info(
      """
              No, The Shadow Knows....

              ${this::class.java.requireResourceAsText("/shadowBanner.txt")}
      """.trimIndent(),
    )
  }

  companion object {
    const val NAME: String = "knows"
    const val DESC: String = "Do you know who knows?"
  }
}
