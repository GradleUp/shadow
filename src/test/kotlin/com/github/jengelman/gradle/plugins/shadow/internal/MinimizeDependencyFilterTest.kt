package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import com.github.jengelman.gradle.plugins.shadow.util.noOpDelegate
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MinimizeDependencyFilterTest {
  private val filter = TestMinimizeDependencyFilter(ProjectBuilder.builder().build())

  @Test
  fun includesDependencyGraph() {
    val grandchild = dependency("grandchild")
    val child = dependency("child").dependsOn(grandchild)
    val parent = dependency("parent").dependsOn(child)

    val result = filter.resolve(parent)

    assertThat(result.included).containsOnly(parent, child, grandchild)
    assertThat(result.excluded).isEmpty()
  }

  @Test
  fun excludesTransitiveDependenciesOfExcludedDependency() {
    val grandchild = dependency("grandchild")
    val child = dependency("child").dependsOn(grandchild)
    val excludedParent = dependency("excluded-parent").dependsOn(child)
    val includedParent = dependency("included-parent")
    filter.exclude(filter.dependency("test:excluded-parent"))

    val result = filter.resolve(excludedParent, includedParent)

    assertThat(result.included).containsOnly(includedParent)
    assertThat(result.excluded).containsOnly(excludedParent, child, grandchild)
  }

  @Test
  fun excludesTransitiveDependenciesOfExplicitlyExcludedChild() {
    val grandchild = dependency("grandchild")
    val excludedChild = dependency("excluded-child").dependsOn(grandchild)
    val parent = dependency("parent").dependsOn(excludedChild)
    filter.exclude(filter.dependency("test:excluded-child"))

    val result = filter.resolve(parent)

    assertThat(result.included).containsOnly(parent)
    assertThat(result.excluded).containsOnly(excludedChild, grandchild)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun excludesSharedDependencyWhenAnyParentIsExcluded(excludedParentFirst: Boolean) {
    val shared = dependency("shared")
    val excludedParent = dependency("excluded-parent").dependsOn(shared)
    val includedParent = dependency("included-parent").dependsOn(shared)
    filter.exclude(filter.dependency("test:excluded-parent"))
    val parents =
      if (excludedParentFirst) {
        arrayOf(excludedParent, includedParent)
      } else {
        arrayOf(includedParent, excludedParent)
      }

    val result = filter.resolve(*parents)

    assertThat(result.included).contains(includedParent)
    assertThat(result.excluded).containsOnly(excludedParent, shared)
  }

  @Test // #1610
  fun excludesCircularDependencies() {
    val first = dependency("first")
    val second = dependency("second")
    first.dependsOn(second)
    second.dependsOn(first)
    filter.exclude(filter.dependency("test:first"))

    val result = filter.resolve(first)

    assertThat(result.included).isEmpty()
    assertThat(result.excluded).containsOnly(first, second)
  }

  private companion object {
    fun dependency(name: String) = GraphResolvedDependency("test", name, "1.0")
  }
}

private class TestMinimizeDependencyFilter(project: Project) : MinimizeDependencyFilter(project) {
  fun resolve(vararg dependencies: ResolvedDependency): Resolution {
    val included = linkedSetOf<ResolvedDependency>()
    val excluded = linkedSetOf<ResolvedDependency>()
    resolve(dependencies.toSet(), included, excluded)
    return Resolution(included, excluded)
  }

  data class Resolution(
    val included: Set<ResolvedDependency>,
    val excluded: Set<ResolvedDependency>,
  )
}

private class GraphResolvedDependency(
  private val group: String,
  private val name: String,
  private val version: String,
) : ResolvedDependency by noOpDelegate() {
  private val dependencyChildren = linkedSetOf<ResolvedDependency>()
  private val dependencyParents = linkedSetOf<ResolvedDependency>()

  fun dependsOn(vararg dependencies: GraphResolvedDependency) = apply {
    dependencies.forEach { dependency ->
      dependencyChildren.add(dependency)
      dependency.dependencyParents.add(this)
    }
  }

  override fun getName(): String = "$group:$name:$version"

  override fun getModuleGroup(): String = group

  override fun getModuleName(): String = name

  override fun getModuleVersion(): String = version

  override fun getChildren(): Set<ResolvedDependency> = dependencyChildren

  override fun getParents(): Set<ResolvedDependency> = dependencyParents

  override fun getConfiguration(): String = "default"
}
