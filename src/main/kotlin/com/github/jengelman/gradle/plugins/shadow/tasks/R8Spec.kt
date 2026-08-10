package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowDsl
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/** Minimal R8 configuration for [ShadowJar.minimize]. */
@ShadowDsl
public interface R8Spec {
  /**
   * The maximum heap size for the R8 worker process.
   *
   * Defaults to the effective maximum heap size of the Gradle daemon. The value uses JVM memory
   * notation, such as `2g` or `512m`.
   */
  @get:Internal // Doesn't affect the output.
  public val maxHeapSize: Property<String>

  /**
   * Additional R8 command line arguments.
   *
   * Defaults to `--no-minification`, so R8 shrinks without renaming classes.
   */
  @get:Input public val args: ListProperty<String>

  @Deprecated(
    message = "Use `proguardRules` instead. This will be removed in Shadow 10.",
    replaceWith = ReplaceWith("proguardRules"),
  )
  @get:Input
  public val keepRules: ListProperty<String>
    get() = proguardRules

  /** Additional R8/ProGuard rules. */
  @get:Input public val proguardRules: ListProperty<String>

  @Deprecated(
    message = "Use `proguardRuleFiles` instead. This will be removed in Shadow 10.",
    replaceWith = ReplaceWith("proguardRuleFiles"),
  )
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public val keepRuleFiles: ConfigurableFileCollection
    get() = proguardRuleFiles

  /** Files containing additional R8/ProGuard rules. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public val proguardRuleFiles: ConfigurableFileCollection

  /**
   * The collective ProGuard configuration output by R8.
   *
   * Defaults to `build/shadowJar/r8/configuration.txt`.
   */
  @get:OutputFile public val configurationFile: RegularFileProperty

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
