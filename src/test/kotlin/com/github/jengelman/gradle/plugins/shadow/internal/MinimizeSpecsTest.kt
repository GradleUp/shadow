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
  fun defaultMinimizeSpecUsesDependencyAnalyzer() {
    val spec = project.objects.newInstance(DefaultMinimizeSpec::class.java, project)

    assertThat(spec.tool.get()).isEqualTo(MinimizeTool.DEPENDENCY_ANALYZER)
    assertThat(spec.r8SpecForInputs).isNull()
  }

  @Test
  fun r8ConfiguresToolAndExposesSameSpecAsInput() {
    val spec = project.objects.newInstance(DefaultMinimizeSpec::class.java, project)
    lateinit var configured: Any

    spec.r8 { configured = it }

    assertThat(spec.tool.get()).isEqualTo(MinimizeTool.R8)
    assertThat(spec.r8SpecForInputs).isSameInstanceAs(configured)
    assertThat(spec.r8Spec).isSameInstanceAs(configured)
  }

  @Test
  fun defaultR8SpecIsShrinkOnly() {
    val spec = project.objects.newInstance(DefaultR8Spec::class.java)

    assertThat(spec.args.get()).containsExactly(DefaultR8Spec.NO_MINIFICATION_ARG)
    assertThat(spec.obfuscationEnabled.get()).isFalse()
    assertThat(spec.optimizationEnabled.get()).isFalse()
    assertThat(spec.keepRules.get()).isEmpty()
    assertThat(spec.keepRuleFiles.files).isEmpty()
  }

  @Test
  fun enablingObfuscationRemovesDefaultArgument() {
    val spec = project.objects.newInstance(DefaultR8Spec::class.java)

    spec.enableObfuscation()

    assertThat(spec.args.get()).isEmpty()
    assertThat(spec.obfuscationEnabled.get()).isTrue()
    assertThat(spec.optimizationEnabled.get()).isFalse()
  }

  @Test
  fun enablingOptimizationOnlyChangesOptimizationFlag() {
    val spec = project.objects.newInstance(DefaultR8Spec::class.java)

    spec.enableOptimization()

    assertThat(spec.args.get()).containsExactly(DefaultR8Spec.NO_MINIFICATION_ARG)
    assertThat(spec.obfuscationEnabled.get()).isFalse()
    assertThat(spec.optimizationEnabled.get()).isTrue()
  }

  @Test
  fun explicitArgumentsTakePrecedenceOverChangedDefaults() {
    val spec = project.objects.newInstance(DefaultR8Spec::class.java)
    spec.args.set(listOf("--debug"))

    spec.enableObfuscation()

    assertThat(spec.args.get()).containsExactly("--debug")
  }
}
