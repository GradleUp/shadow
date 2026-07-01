package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/** Minimal R8 configuration for [ShadowJar.minimize]. */
public interface R8Spec {
  /**
   * Additional R8 command line arguments.
   *
   * Defaults to `--no-minification`, so R8 shrinks without renaming classes.
   */
  @get:Input public val args: ListProperty<String>

  /** Additional ProGuard/R8 keep rules. */
  @get:Input public val keepRules: ListProperty<String>

  /** Files containing additional ProGuard/R8 keep rules. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public val keepRuleFiles: ConfigurableFileCollection
}
