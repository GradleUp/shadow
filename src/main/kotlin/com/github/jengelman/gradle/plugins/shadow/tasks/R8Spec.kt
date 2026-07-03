package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/** Minimal R8 configuration for [ShadowJar.minimize]. */
@ShadowDslMarker
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

  /**
   * Enable R8 name obfuscation while keeping Shadow's default no-optimization behavior.
   *
   * This removes Shadow's default `--no-minification` argument. Optimization remains disabled
   * unless [enableOptimization] is also called.
   */
  public fun enableObfuscation()

  /**
   * Enable R8 optimization while keeping Shadow's default no-obfuscation behavior.
   *
   * This removes Shadow's generated `-dontoptimize` rule. Name obfuscation remains disabled unless
   * [enableObfuscation] is also called.
   */
  public fun enableOptimization()
}
