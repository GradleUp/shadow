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
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import de.infix.testBalloon.framework.core.testSuite
import org.gradle.testfixtures.ProjectBuilder

val MinimizeSpecsTests by testSuite {
  runTests(::MinimizeSpecsTest)
}

private class MinimizeSpecsTest {
  private val project = ProjectBuilder.builder().build()

  fun defaultMinimizeSpecUsesDependencyAnalyzer() =
    with(project.objects.newInstance(DefaultMinimizeSpec::class.java, project)) {
      assertThat(tool.get()).isEqualTo(MinimizeTool.DEPENDENCY_ANALYZER)
      assertThat(r8SpecForInputs).isNull()
    }

  fun r8ConfiguresToolAndExposesSameSpecAsInput() =
    with(project.objects.newInstance(DefaultMinimizeSpec::class.java, project)) {
      lateinit var configured: Any
      r8 { configured = it }

      assertThat(tool.get()).isEqualTo(MinimizeTool.R8)
      assertThat(r8SpecForInputs).isSameInstanceAs(configured)
      assertThat(r8Spec).isSameInstanceAs(configured)
    }

  fun defaultR8SpecIsShrinkOnly() =
    with(project.objects.newInstance(DefaultR8Spec::class.java)) {
      assertThat(args.get()).containsExactly(DefaultR8Spec.NO_MINIFICATION_ARG)
      assertThat(obfuscationEnabled.get()).isFalse()
      assertThat(optimizationEnabled.get()).isFalse()
      assertThat(proguardRules.get()).isEmpty()
      assertThat(proguardRuleFiles.files).isEmpty()
      assertThat(configurationFile.get().asFile)
        .isEqualTo(
          project.layout.buildDirectory.file("shadowJar/r8/configuration.txt").get().asFile
        )
    }

  fun enablingObfuscationRemovesDefaultArgument() =
    with(project.objects.newInstance(DefaultR8Spec::class.java)) {
      enableObfuscation()

      assertThat(args.get()).isEmpty()
      assertThat(obfuscationEnabled.get()).isTrue()
      assertThat(optimizationEnabled.get()).isFalse()
    }

  fun enablingOptimizationOnlyChangesOptimizationFlag() =
    with(project.objects.newInstance(DefaultR8Spec::class.java)) {
      enableOptimization()

      assertThat(args.get()).containsExactly(DefaultR8Spec.NO_MINIFICATION_ARG)
      assertThat(obfuscationEnabled.get()).isFalse()
      assertThat(optimizationEnabled.get()).isTrue()
    }

  fun explicitArgumentsTakePrecedenceOverChangedDefaults() =
    with(project.objects.newInstance(DefaultR8Spec::class.java)) {
      args.set(listOf("--debug"))
      enableObfuscation()

      assertThat(args.get()).containsExactly("--debug")
    }
}
