package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.util.noOpDelegate
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DefaultDependencyFilterTest {
  private val project = ProjectBuilder.builder().build()
  private val filter = DefaultDependencyFilter(project)

  @ParameterizedTest
  @ValueSource(
    strings = ["my:d", "m.*:d", "my:d:.*", "m.*:d:.*", "m.*:d.*:.*", ".*:d:.*", "my:d:1.0"]
  )
  fun matchesWildcardNotation(notation: String) {
    val spec = filter.dependency(notation)
    val dep = testDependency("my", "d", "1.0")

    assertThat(spec.isSatisfiedBy(dep)).isTrue()
  }

  @Test
  fun matchesExactDependency() {
    val spec = filter.dependency("com.acme:foo:2.1.0")

    assertThat(spec.isSatisfiedBy(testDependency("com.acme", "foo", "2.1.0"))).isTrue()
    assertThat(spec.isSatisfiedBy(testDependency("com.acme", "bar", "2.1.0"))).isFalse()
    assertThat(spec.isSatisfiedBy(testDependency("org.other", "foo", "2.1.0"))).isFalse()
  }

  @Test
  fun matchesVersionWithPlusSpecialChar() {
    val spec = filter.dependency("org.foo:bar:1.0.0+1")

    assertThat(spec.isSatisfiedBy(testDependency("org.foo", "bar", "1.0.0+1"))).isTrue()
    assertThat(spec.isSatisfiedBy(testDependency("org.foo", "bar", "1.0.0+2"))).isFalse()
  }

  @Test
  fun excludeFilterMatchesDependency() {
    val excludeSpec = filter.dependency("my:b")

    assertThat(excludeSpec.isSatisfiedBy(testDependency("my", "a", "1.0"))).isFalse()
    assertThat(excludeSpec.isSatisfiedBy(testDependency("my", "b", "1.0"))).isTrue()
  }

  private companion object {
    fun testDependency(
      group: String,
      name: String,
      version: String,
    ): ResolvedDependency = TestResolvedDependency(group, name, version)
  }

  private class TestResolvedDependency(
    private val group: String,
    private val name: String,
    private val version: String,
  ) : ResolvedDependency {
    override fun getName(): String = "$group:$name:$version"

    override fun getModuleGroup(): String = group

    override fun getModuleName(): String = name

    override fun getModuleVersion(): String = version

    override fun getModule(): ResolvedModuleVersion = noOpDelegate()

    override fun getChildren(): Set<ResolvedDependency> = emptySet()

    override fun getParents(): Set<ResolvedDependency> = emptySet()

    override fun getModuleArtifacts(): Set<ResolvedArtifact> = emptySet()

    override fun getAllModuleArtifacts(): Set<ResolvedArtifact> = emptySet()

    override fun getParentArtifacts(parent: ResolvedDependency): Set<ResolvedArtifact> = emptySet()

    override fun getArtifacts(parent: ResolvedDependency): Set<ResolvedArtifact> = emptySet()

    override fun getAllArtifacts(parent: ResolvedDependency): Set<ResolvedArtifact> = emptySet()

    override fun getConfiguration(): String = "default"
  }
}
