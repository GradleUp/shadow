package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import java.lang.reflect.Proxy
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.specs.Spec
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class DependencyFiltersTest {
  private val project = ProjectBuilder.builder().build()

  @Test
  fun defaultFilterTraversesIncludedAndExcludedBranches() {
    val includedChild = dependency("included-child")
    val excludedChild = dependency("excluded-child")
    val includedRoot = dependency("included-root", children = setOf(includedChild))
    val excludedRoot = dependency("excluded-root", children = setOf(excludedChild))
    val filter = DefaultDependencyFilter(project)
    filter.exclude(named("excluded-root"))
    val included = mutableSetOf<ResolvedDependency>()
    val excluded = mutableSetOf<ResolvedDependency>()

    filter.resolve(setOf(includedRoot, excludedRoot), included, excluded)

    assertThat(included).containsOnly(includedRoot, includedChild, excludedChild)
    assertThat(excluded).containsOnly(excludedRoot)
  }

  @Test
  fun includeRulesExcludeNonMatchingDependencies() {
    val includedRoot = dependency("included")
    val otherRoot = dependency("other")
    val filter = DefaultDependencyFilter(project)
    filter.include(named("included"))
    val included = mutableSetOf<ResolvedDependency>()
    val excluded = mutableSetOf<ResolvedDependency>()

    filter.resolve(setOf(includedRoot, otherRoot), included, excluded)

    assertThat(included).containsOnly(includedRoot)
    assertThat(excluded).containsOnly(otherRoot)
  }

  @Test
  fun excludeWinsWhenDependencyMatchesIncludeAndExclude() {
    val root = dependency("root")
    val filter = DefaultDependencyFilter(project)
    filter.include(named("root"))
    filter.exclude(named("root"))
    val included = mutableSetOf<ResolvedDependency>()
    val excluded = mutableSetOf<ResolvedDependency>()

    filter.resolve(setOf(root), included, excluded)

    assertThat(included).isEmpty()
    assertThat(excluded).containsOnly(root)
  }

  @Test
  fun minimizeFilterExcludesChildrenOfExcludedParent() {
    lateinit var excludedRoot: ResolvedDependency
    val child = dependency("child", parents = { setOf(excludedRoot) })
    excludedRoot = dependency("excluded-root", children = setOf(child))
    val filter = MinimizeDependencyFilter(project)
    filter.exclude(named("excluded-root"))
    val included = mutableSetOf<ResolvedDependency>()
    val excluded = mutableSetOf<ResolvedDependency>()

    filter.resolve(setOf(excludedRoot), included, excluded)

    assertThat(included).isEmpty()
    assertThat(excluded).containsOnly(excludedRoot, child)
  }

  @Test
  fun diamondGraphIsVisitedOnceAndIncluded() {
    val leaf = dependency("leaf")
    val left = dependency("left", children = setOf(leaf))
    val right = dependency("right", children = setOf(leaf))
    val filter = DefaultDependencyFilter(project)
    val included = mutableSetOf<ResolvedDependency>()
    val excluded = mutableSetOf<ResolvedDependency>()

    filter.resolve(setOf(left, right), included, excluded)

    assertThat(included).containsOnly(left, right, leaf)
    assertThat(excluded).isEmpty()
  }

  private fun named(name: String) = Spec<ResolvedDependency> { it.moduleName == name }

  private fun dependency(
    name: String,
    children: Set<ResolvedDependency> = emptySet(),
    parents: () -> Set<ResolvedDependency> = { emptySet() },
  ): ResolvedDependency {
    lateinit var proxy: ResolvedDependency
    proxy =
      Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(ResolvedDependency::class.java),
      ) { _, method, args ->
        when (method.name) {
          "getModuleGroup" -> "test"
          "getModuleName" -> name
          "getModuleVersion" -> "1.0"
          "getChildren" -> children
          "getParents" -> parents()
          "hashCode" -> System.identityHashCode(proxy)
          "equals" -> proxy === args?.firstOrNull()
          "toString" -> name
          else -> null
        }
      } as ResolvedDependency
    return proxy
  }
}
