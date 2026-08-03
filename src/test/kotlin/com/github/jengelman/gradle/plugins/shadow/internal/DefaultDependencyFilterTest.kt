package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.util.noOpDelegate
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class DefaultDependencyFilterTest {
  private val project = ProjectBuilder.builder().build()
  private val filter = DefaultDependencyFilter(project)

  @ParameterizedTest
  @MethodSource("dependencyNotationProvider")
  fun matchesDependencyNotation(
    notation: String,
    group: String,
    name: String,
    version: String,
    expected: Boolean,
  ) {
    val spec = filter.dependency(notation)
    val dep = TestResolvedDependency(group, name, version)

    assertThat(spec.isSatisfiedBy(dep)).isEqualTo(expected)
  }

  @Test
  fun matchesProviderDependencyNotation() {
    val provider = project.provider { "foo:bar:1.0" }
    val spec = filter.dependency(provider)
    val dep = TestResolvedDependency("foo", "bar", "1.0")

    assertThat(spec.isSatisfiedBy(dep)).isTrue()
  }

  @Test
  fun matchesMapDependencyNotation() {
    val mapNotation = mapOf("group" to "foo", "name" to "bar", "version" to "1.0")
    val spec = filter.dependency(mapNotation)
    val dep = TestResolvedDependency("foo", "bar", "1.0")

    assertThat(spec.isSatisfiedBy(dep)).isTrue()
  }

  @Test
  fun matchesProjectNotation() {
    val subproject = ProjectBuilder.builder().withName("subproject").build()
    val projectDep = project.dependencies.project(mapOf("path" to subproject.path))
    val spec = filter.project(subproject.path)
    val dep =
      TestResolvedDependency(
        group = projectDep.group ?: "",
        name = projectDep.name,
        version = projectDep.version ?: "unspecified",
      )

    assertThat(spec.isSatisfiedBy(dep)).isTrue()
  }

  private class TestResolvedDependency(
    private val group: String,
    private val name: String,
    private val version: String,
  ) : ResolvedDependency by noOpDelegate() {
    override fun getName(): String = "$group:$name:$version"

    override fun getModuleGroup(): String = group

    override fun getModuleName(): String = name

    override fun getModuleVersion(): String = version

    override fun getConfiguration(): String = "default"
  }

  private companion object {
    @JvmStatic
    fun dependencyNotationProvider() =
      listOf(
        Arguments.of("foo:bar", "foo", "bar", "1.0", true),
        Arguments.of("f.*:bar", "foo", "bar", "1.0", true),
        Arguments.of("foo:bar:.*", "foo", "bar", "1.0", true),
        Arguments.of("f.*:bar:.*", "foo", "bar", "1.0", true),
        Arguments.of("f.*:bar.*:.*", "foo", "bar", "1.0", true),
        Arguments.of(".*:bar:.*", "foo", "bar", "1.0", true),
        Arguments.of("foo:bar:2.1.0", "foo", "bar", "2.1.0", true),
        Arguments.of("foo:bar:2.1.0", "foo", "baz", "2.1.0", false),
        Arguments.of("foo:bar:2.1.0", "bar", "bar", "2.1.0", false),
        Arguments.of("foo:bar:1.0.0+1", "foo", "bar", "1.0.0+1", true),
        Arguments.of("foo:bar:1.0.0+1", "foo", "bar", "1.0.0+2", false),
        Arguments.of("foo:bar:1\\.0\\..*", "foo", "bar", "1.0.5", true),
        Arguments.of("foo:bar:1\\.0\\..*", "foo", "bar", "2.0.0", false),
        Arguments.of("foo:bar:1.0", "baz", "bar", "1.0", false),
        Arguments.of("foo:bar:1.0", "foo", "bar", "2.0", false),
        Arguments.of("f.*:bar", "zoo", "bar", "1.0", false),
      )
  }
}
