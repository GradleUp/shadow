package com.github.jengelman.gradle.plugins.shadow

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.fail
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction.Companion.CONSTANT_TIME_FOR_ZIP_ENTRIES
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import java.net.URLClassLoader
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.opentest4j.AssertionFailedError

class RelocationTest : BasePluginTest() {
  @ParameterizedTest
  @MethodSource("prefixProvider")
  fun autoRelocation(relocationPrefix: String) {
    val mainClass = writeMainClass()
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJar {
          enableRelocation = true
          relocationPrefix = '$relocationPrefix'
        }
      """.trimIndent(),
    )
    val entryPrefix = relocationPrefix.replace('.', '/')

    val result = run(shadowJarTask, "--info")

    assertThat(outputShadowJar).useAll {
      containsEntries(
        mainClass,
        *junitEntries.map { "$entryPrefix/$it" }.toTypedArray(),
      )
      doesNotContainEntries(
        "$entryPrefix/$mainClass",
        *junitEntries,
      )
    }
    // Make sure the relocator count is aligned with the number of unique packages in junit jar.
    assertThat(result.output).contains(
      "Relocator count: 6.",
    )
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/58",
  )
  @Test
  fun relocateDependencyFiles() {
    val mainClass = writeMainClass()
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJar {
          relocate 'junit.runner', 'a'
          relocate 'junit.framework', 'b'
        }
      """.trimIndent(),
    )
    val runnerFilter = { it: String -> it.startsWith("junit/runner/") }
    val frameworkFilter = { it: String -> it.startsWith("junit/framework/") }
    val runnerEntries = junitEntries
      .filter(runnerFilter)
      .map { it.replace("junit/runner/", "a/") }.toTypedArray()
    val frameworkEntries = junitEntries
      .filter(frameworkFilter)
      .map { it.replace("junit/framework/", "b/") }.toTypedArray()
    val otherJunitEntries = junitEntries.filterNot { runnerFilter(it) || frameworkFilter(it) }.toTypedArray()

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        mainClass,
        *runnerEntries,
        *frameworkEntries,
        *otherJunitEntries,
      )
      doesNotContainEntries(
        *junitEntries.filter { it !in otherJunitEntries }.toTypedArray(),
        *otherJunitEntries.map { "a/$it" }.toTypedArray(),
        *otherJunitEntries.map { "b/$it" }.toTypedArray(),
      )
    }
  }

  @Test
  fun relocateDependencyFilesWithFiltering() {
    val mainClass = writeMainClass()
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJar {
          relocate('junit.runner', 'a') {
            exclude 'junit.runner.BaseTestRunner'
          }
          relocate('junit.framework', 'b') {
            include 'junit.framework.Test*'
          }
        }
      """.trimIndent(),
    )
    val runnerFilter = { it: String -> it.startsWith("junit/runner/") && it != "junit/runner/BaseTestRunner.class" }
    val frameworkFilter = { it: String -> it.startsWith("junit/framework/Test") }
    val runnerEntries = junitEntries
      .filter(runnerFilter)
      .map { it.replace("junit/runner/", "a/") }.toTypedArray()
    val frameworkEntries = junitEntries
      .filter(frameworkFilter)
      .map { it.replace("junit/framework/", "b/") }.toTypedArray()
    val otherJunitEntries = junitEntries.filterNot { runnerFilter(it) || frameworkFilter(it) }.toTypedArray()

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        mainClass,
        *runnerEntries,
        *frameworkEntries,
        *otherJunitEntries,
      )
      doesNotContainEntries(
        *otherJunitEntries.map { "a/$it" }.toTypedArray(),
        *otherJunitEntries.map { "b/$it" }.toTypedArray(),
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/53",
    "https://github.com/GradleUp/shadow/issues/55",
  )
  @Test
  fun remapClassNamesForRelocatedFilesInProjectSource() {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJar {
          relocate 'junit.framework', 'shadow.junit'
        }
      """.trimIndent(),
    )
    val shadowedEntries = junitEntries
      .map { it.replace("junit/framework/", "shadow/junit/") }.toTypedArray()

    path("src/main/java/my/MyTest.java").writeText(
      """
        package my;
        import junit.framework.Test;
        import junit.framework.TestResult;
        public class MyTest implements Test {
          public int countTestCases() { return 0; }
          public void run(TestResult result) { }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        "my/MyTest.class",
        *shadowedEntries,
      )
      doesNotContainEntries(
        *junitEntries.filter { it.startsWith("junit/framework/") }.toTypedArray(),
      )
    }

    val url = outputShadowJar.use { it.toUri().toURL() }
    URLClassLoader(arrayOf(url), ClassLoader.getSystemClassLoader().parent).use { classLoader ->
      assertFailure {
        // check that the class can be loaded. If the file was not relocated properly, we should get a NoDefClassFound
        // Isolated class loader with only the JVM system jars and the output jar from the test project
        classLoader.loadClass("my.MyTest")
        fail("Should not reach here.")
      }.isInstanceOf(AssertionFailedError::class)
    }
  }

  @Test
  fun relocateDoesNotDropDependencyResources() {
    settingsScriptPath.appendText(
      """
        include 'core', 'app'
      """.trimIndent(),
    )
    path("core/build.gradle").writeText(
      """
        plugins {
          id 'java-library'
        }
        dependencies {
          api 'junit:junit:3.8.2'
        }
      """.trimIndent(),
    )

    path("core/src/main/resources/TEST").writeText("TEST RESOURCE")
    path("core/src/main/resources/test.properties").writeText("name=test")
    path("core/src/main/java/core/Core.java").writeText(
      """
        package core;
        import junit.framework.Test;
        public class Core {}
      """.trimIndent(),
    )

    path("app/build.gradle").writeText(
      """
        ${getDefaultProjectBuildScript()}
        dependencies {
          implementation project(':core')
        }
        $shadowJar {
          relocate 'core', 'app.core'
          relocate 'junit.framework', 'app.junit.framework'
        }
      """.trimIndent(),
    )
    path("app/src/main/resources/APP-TEST").writeText("APP TEST RESOURCE")
    path("app/src/main/java/app/App.java").writeText(
      """
        package app;
        import core.Core;
        import junit.framework.Test;
        public class App {}
      """.trimIndent(),
    )
    val shadowedEntries = junitEntries
      .map { it.replace("junit/framework/", "app/junit/framework/") }.toTypedArray()

    run(":app:$SHADOW_JAR_TASK_NAME")

    assertThat(jarPath("app/build/libs/app-all.jar")).useAll {
      containsEntries(
        "TEST",
        "APP-TEST",
        "test.properties",
        "app/core/Core.class",
        "app/App.class",
        *shadowedEntries,
      )
      doesNotContainEntries(
        *junitEntries.filter { it.startsWith("junit/framework/") }.toTypedArray(),
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/93",
    "https://github.com/GradleUp/shadow/issues/114",
  )
  @Test
  fun relocateResourceFiles() {
    localRepo.module("my", "dep", "1.0") {
      buildJar {
        insert("foo/dep.properties", "c")
      }
    }.publish()
    path("src/main/java/foo/Foo.java").writeText(
      """
        package foo;
        class Foo {}
      """.trimIndent(),
    )
    path("src/main/resources/foo/foo.properties").writeText("name=foo")

    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'my:dep:1.0'
        }
        $shadowJar {
          relocate 'foo', 'bar'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        "bar/Foo.class",
        "bar/foo.properties",
        "bar/dep.properties",
      )
      doesNotContainEntries(
        "foo/Foo.class",
        "foo/foo.properties",
        "foo/dep.properties",
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/294",
  )
  @Test
  fun doesNotErrorOnRelocatingJava9Classes() {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'org.slf4j:slf4j-api:1.7.21'
          implementation group: 'io.netty', name: 'netty-all', version: '4.0.23.Final'
          implementation group: 'com.google.protobuf', name: 'protobuf-java', version: '2.5.0'
          implementation group: 'org.apache.zookeeper', name: 'zookeeper', version: '3.4.6'
        }
        $shadowJar {
          zip64 = true
          relocate 'com.google.protobuf', 'shaded.com.google.protobuf'
          relocate 'io.netty', 'shaded.io.netty'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val entries = outputShadowJar.use { it.entries().toList() }
    val included = entries.filter { entry ->
      entry.name.startsWith("shaded/com/google/protobuf") || entry.name.startsWith("shaded/io/netty")
    }
    val excluded = entries.filter { entry ->
      entry.name.startsWith("com/google/protobuf") || entry.name.startsWith("io/netty")
    }
    assertThat(included).isNotEmpty()
    assertThat(excluded).isEmpty()
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun preserveLastModifiedCorrectly(preserveFileTimestamps: Boolean) {
    // Minus 1 minute to avoid the time difference between the file system and the JVM.
    val currentTimeMillis = System.currentTimeMillis() - 1.minutes.inWholeMilliseconds
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJar {
          enableRelocation = true
          preserveFileTimestamps = $preserveFileTimestamps
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val (relocatedEntries, otherEntries) = outputShadowJar.use {
      it.entries().toList().partition { entry -> entry.name.startsWith("shadow/") }
    }
    assertThat(relocatedEntries).isNotEmpty()
    assertThat(otherEntries).isNotEmpty()
    val (relocatedDirs, relocatedClasses) = relocatedEntries.partition { it.isDirectory }
    assertThat(relocatedDirs).isNotEmpty()
    assertThat(relocatedClasses).isNotEmpty()

    if (preserveFileTimestamps) {
      relocatedClasses.forEach { entry ->
        // Relocated classes should preserve the last modified time of the original classes, about in 2006.
        if (entry.time > currentTimeMillis || entry.time <= CONSTANT_TIME_FOR_ZIP_ENTRIES) {
          fail("Relocated file ${entry.name} has an invalid last modified time: ${entry.time}")
        }
      }
      relocatedDirs.forEach { entry ->
        // Relocated directories are newly created, defaults to CONSTANT_TIME_FOR_ZIP_ENTRIES.
        if (entry.time != CONSTANT_TIME_FOR_ZIP_ENTRIES) {
          fail("Relocated directory ${entry.name} has an invalid last modified time: ${entry.time}")
        }
      }
      otherEntries.forEach { entry ->
        // Other entries are newly created, so they should be in now time.
        if (entry.time < currentTimeMillis) {
          fail("Entry ${entry.name} has an invalid last modified time: ${entry.time}")
        }
      }
    } else {
      (relocatedEntries + otherEntries).forEach { entry ->
        // All entries should be newly modified, defaults to CONSTANT_TIME_FOR_ZIP_ENTRIES.
        if (entry.time != CONSTANT_TIME_FOR_ZIP_ENTRIES) {
          fail("Entry ${entry.name} has an invalid last modified time: ${entry.time}")
        }
      }
    }
  }

  private companion object {
    @JvmStatic
    fun prefixProvider() = listOf(
      // The default values.
      Arguments.of(ShadowBasePlugin.SHADOW),
      Arguments.of("new.pkg"),
      Arguments.of("new/path"),
    )
  }
}
