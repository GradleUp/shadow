package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import com.github.jengelman.gradle.plugins.shadow.testkit.Arguments
import com.github.jengelman.gradle.plugins.shadow.testkit.runTest
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import com.github.jengelman.gradle.plugins.shadow.util.noOpDelegate
import de.infix.testBalloon.framework.core.testSuite
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.testfixtures.ProjectBuilder

val DefaultDependencyFilterTests by testSuite {
  runTests(::DefaultDependencyFilterTest)

  for ((notation: Any, group: String, name: String, version: String, expected: Boolean) in
    DefaultDependencyFilterTest.dependencyNotationProvider) {
    runTest(
      "matchesDependencyNotation_${notation}_${group}_${name}_$version",
      ::DefaultDependencyFilterTest,
    ) {
      matchesDependencyNotation(notation, group, name, version, expected)
    }
  }

  for (notation in DefaultDependencyFilterTest.projectNotationProvider) {
    runTest("matchesProjectNotation_${notation}", ::DefaultDependencyFilterTest) {
      matchesProjectNotation(notation)
    }
  }
}

private class DefaultDependencyFilterTest {
  private val filter = DefaultDependencyFilter(project)

  fun matchesDependencyNotation(
    notation: Any,
    group: String,
    name: String,
    version: String,
    expected: Boolean,
  ) {
    val spec = filter.dependency(notation)
    val dep = TestResolvedDependency(group, name, version)

    assertThat(spec.isSatisfiedBy(dep)).isEqualTo(expected)
  }

  fun matchesProjectNotation(notation: Any) {
    val spec = filter.project(notation)
    val dep =
      TestResolvedDependency(
        group = projectDependency.group.orEmpty(),
        name = projectDependency.name,
        version = projectDependency.version ?: "unspecified",
      )

    assertThat(spec.isSatisfiedBy(dep)).isTrue()
  }

  fun rejectsUnsupportedProjectNotation() {
    assertFailure { filter.project(42) }
      .isInstanceOf<IllegalArgumentException>()
      .messageContains("Unsupported notation type: class java.lang.Integer")
  }

  companion object {
    val project = ProjectBuilder.builder().build()
    val subproject = ProjectBuilder.builder().withName("subproject").withParent(project).build()
    val projectDependency = project.dependencies.project(mapOf("path" to subproject.path))

    val stringNotations =
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

    val providerNotations =
      listOf(Arguments.of(project.provider { "foo:bar:1.0" }, "foo", "bar", "1.0", true))

    val mapNotations =
      listOf(
        Arguments.of(
          mapOf("group" to "foo", "name" to "bar", "version" to "1.0"),
          "foo",
          "bar",
          "1.0",
          true,
        ),
        Arguments.of(mapOf("name" to "bar"), "any.group", "bar", "1.0", true),
      )

    val dependencyNotationProvider = stringNotations + providerNotations + mapNotations

    val projectNotationProvider =
      listOf(
        subproject.path,
        project.provider { subproject.path },
        mapOf("path" to subproject.path),
        projectDependency,
      )
  }
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
