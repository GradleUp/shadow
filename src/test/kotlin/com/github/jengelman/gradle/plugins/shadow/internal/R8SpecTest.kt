package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class R8SpecTest {
  private val project = ProjectBuilder.builder().build()

  @Test
  fun defaultR8SpecIsShrinkOnly() =
    with(project.objects.newInstance(DefaultR8Spec::class.java)) {
      assertThat(args.get()).containsExactly(DefaultR8Spec.NO_MINIFICATION_ARG)
      assertThat(obfuscationEnabled.get()).isFalse()
      assertThat(optimizationEnabled.get()).isFalse()
      assertThat(proguardRules.get()).isEmpty()
      assertThat(proguardRuleFiles.files).isEmpty()
    }

  @Test
  fun enablingObfuscationRemovesDefaultArgument() =
    with(project.objects.newInstance(DefaultR8Spec::class.java)) {
      enableObfuscation()

      assertThat(args.get()).isEmpty()
      assertThat(obfuscationEnabled.get()).isTrue()
      assertThat(optimizationEnabled.get()).isFalse()
    }

  @Test
  fun enablingOptimizationOnlyChangesOptimizationFlag() =
    with(project.objects.newInstance(DefaultR8Spec::class.java)) {
      enableOptimization()

      assertThat(args.get()).containsExactly(DefaultR8Spec.NO_MINIFICATION_ARG)
      assertThat(obfuscationEnabled.get()).isFalse()
      assertThat(optimizationEnabled.get()).isTrue()
    }

  @Test
  fun explicitArgumentsTakePrecedenceOverChangedDefaults() =
    with(project.objects.newInstance(DefaultR8Spec::class.java)) {
      args.set(listOf("--debug"))
      enableObfuscation()

      assertThat(args.get()).containsExactly("--debug")
    }
}
