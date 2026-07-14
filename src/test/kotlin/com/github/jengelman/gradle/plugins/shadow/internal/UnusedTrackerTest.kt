package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import java.io.File
import java.nio.file.Path
import javax.tools.ToolProvider
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class UnusedTrackerTest {
  @TempDir lateinit var tempDir: Path

  @Test
  fun getApiJarsIsEmptyWithoutApiConfiguration() {
    val project = ProjectBuilder.builder().build()

    assertThat(project.getApiJars().get()).isEmpty()
  }

  @Test
  fun getApiJarsCreatesAndReusesResolvableApiView() {
    val project = ProjectBuilder.builder().build()
    project.plugins.apply(JavaLibraryPlugin::class.java)

    project.getApiJars().get()
    val configuration = project.configurations.getByName("shadowMinimizeApi")
    val second = project.getApiJars()

    assertThat(configuration.isCanBeResolved).isTrue()
    assertThat(configuration.isCanBeConsumed).isFalse()
    assertThat(configuration.extendsFrom)
      .containsOnly(project.configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME))
    assertThat(configuration.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.name)
      .isEqualTo(Usage.JAVA_API)
    assertThat(configuration.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)?.name)
      .isEqualTo(Category.LIBRARY)
    assertThat(
        configuration.attributes.getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE)?.name
      )
      .isEqualTo(LibraryElements.JAR)
    assertThat(project.configurations.getByName("shadowMinimizeApi"))
      .isSameInstanceAs(configuration)
    // Creating the second provider must not register another configuration.
    assertThat(second.isPresent).isTrue()
  }

  @Test
  fun tracksUnusedClassesAndOnlyAddsSelectedDependencies() {
    val dependencyClasses = tempDir.resolve("dependency-classes").toFile()
    compile(
      dependencyClasses,
      "dep.Used" to "package dep; public class Used {}",
      "dep.Unused" to "package dep; public class Unused {}",
    )
    val projectClasses = tempDir.resolve("project-classes").toFile()
    compile(
      projectClasses,
      "app.Main" to "package app; public class Main { dep.Used used; }",
      classpath = dependencyClasses,
    )
    val project = ProjectBuilder.builder().build()
    val tracker =
      UnusedTracker(
        sourceSetsClassesDirs = listOf(projectClasses),
        classJars = project.files(),
        toMinimize = project.files(dependencyClasses),
      )

    tracker.addDependency(tempDir.resolve("not-selected").toFile())
    tracker.addDependency(dependencyClasses)

    assertThat(tracker.findUnused()).containsOnly("dep.Unused")
  }

  private fun compile(
    output: File,
    vararg sources: Pair<String, String>,
    classpath: File? = null,
  ) {
    output.mkdirs()
    val sourceFiles = sources.map { (className, source) ->
      tempDir.resolve("sources/${className.replace('.', '/')}.java").toFile().apply {
        parentFile.mkdirs()
        writeText(source)
      }
    }
    val arguments = buildList {
      addAll(listOf("-d", output.absolutePath))
      if (classpath != null) addAll(listOf("-classpath", classpath.absolutePath))
      addAll(sourceFiles.map(File::getAbsolutePath))
    }
    val compiler = ToolProvider.getSystemJavaCompiler()
    org.junit.jupiter.api.Assumptions.assumeTrue(
      compiler != null,
      "JDK (javax.tools.JavaCompiler) is required to compile test sources"
    )
    val result = compiler!!.run(null, null, null, *arguments.toTypedArray())
    assertThat(result).isEqualTo(0)
}
