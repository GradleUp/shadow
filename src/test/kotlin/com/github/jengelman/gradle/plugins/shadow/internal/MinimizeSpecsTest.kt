package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.tasks.MinimizeTool
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class MinimizeSpecsTest {
  private val project = ProjectBuilder.builder().build()

  @Test
  fun defaultMinimizeSpecUsesDependencyAnalyzer() =
    with(project.objects.newInstance(DefaultMinimizeSpec::class.java, project)) {
      assertThat(tool.get()).isEqualTo(MinimizeTool.DEPENDENCY_ANALYZER)
      assertThat(r8SpecForInputs).isNull()
    }

  @Test
  fun r8ConfiguresToolAndExposesSameSpecAsInput() =
    with(project.objects.newInstance(DefaultMinimizeSpec::class.java, project)) {
      lateinit var configured: Any
      r8 { configured = it }

      assertThat(tool.get()).isEqualTo(MinimizeTool.R8)
      assertThat(r8SpecForInputs).isSameInstanceAs(configured)
      assertThat(r8Spec).isSameInstanceAs(configured)
    }

  @Test
  fun defaultR8SpecIsShrinkOnly() =
    with(project.objects.newInstance(DefaultR8Spec::class.java)) {
      assertThat(args.get()).containsExactly(DefaultR8Spec.NO_MINIFICATION_ARG)
      assertThat(obfuscationEnabled.get()).isFalse()
      assertThat(optimizationEnabled.get()).isFalse()
      assertThat(keepRules.get()).isEmpty()
      assertThat(keepRuleFiles.files).isEmpty()
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
