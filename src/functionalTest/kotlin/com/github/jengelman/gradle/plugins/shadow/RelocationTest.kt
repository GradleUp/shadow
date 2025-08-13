package com.github.jengelman.gradle.plugins.shadow

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.fail
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction.Companion.CONSTANT_TIME_FOR_ZIP_ENTRIES
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import com.github.jengelman.gradle.plugins.shadow.util.runProcess
import java.net.URLClassLoader
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds
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
    val mainClassEntry = writeClass()
    projectScript.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJarTask {
          enableAutoRelocation = true
          relocationPrefix = '$relocationPrefix'
        }
      """.trimIndent(),
    )
    val entryPrefix = relocationPrefix.replace('.', '/')
    val relocatedEntries = buildSet {
      addAll(
        junitEntries.map { "$entryPrefix/$it" }
          .filterNot { it.startsWith("$entryPrefix/META-INF/") },
      )
      var parent = entryPrefix
      while (parent.isNotEmpty()) {
        add("$parent/")
        parent = parent.substringBeforeLast('/', "")
      }
    }.toTypedArray()

    val result = run(shadowJarPath, infoArgument)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "my/",
        mainClassEntry,
        *relocatedEntries,
        *manifestEntries,
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
    val mainClassEntry = writeClass()
    projectScript.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJarTask {
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

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "my/",
        mainClassEntry,
        *runnerEntries,
        *frameworkEntries,
        *otherJunitEntries,
        *manifestEntries,
      )
    }
  }

  @Test
  fun relocateDependencyFilesWithFiltering() {
    val mainClassEntry = writeClass()
    projectScript.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJarTask {
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

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "b/",
        "my/",
        "junit/runner/",
        mainClassEntry,
        *runnerEntries,
        *frameworkEntries,
        *otherJunitEntries,
        *manifestEntries,
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/53",
    "https://github.com/GradleUp/shadow/issues/55",
  )
  @Test
  fun remapClassNamesForRelocatedFilesInProjectSource() {
    projectScript.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJarTask {
          relocate 'junit.framework', 'shadow.junit'
        }
      """.trimIndent(),
    )
    val relocatedEntries = junitEntries
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

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "my/",
        "shadow/",
        "my/MyTest.class",
        *relocatedEntries,
        *manifestEntries,
      )
    }

    val url = outputShadowedJar.use { it.toUri().toURL() }
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
    settingsScript.appendText(
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
        $shadowJarTask {
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
    val relocatedEntries = junitEntries
      .map { it.replace("junit/framework/", "app/junit/framework/") }.toTypedArray()

    run(":app:$SHADOW_JAR_TASK_NAME")

    assertThat(jarPath("app/build/libs/app-all.jar")).useAll {
      containsOnly(
        "TEST",
        "APP-TEST",
        "test.properties",
        "app/",
        "app/core/",
        "app/junit/",
        "app/App.class",
        "app/core/Core.class",
        *relocatedEntries,
        *manifestEntries,
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/93",
    "https://github.com/GradleUp/shadow/issues/114",
  )
  @Test
  fun relocateResourceFiles() {
    val depJar = buildJar("foo.jar") {
      insert("foo/dep.properties", "c")
    }
    writeClass(packageName = "foo", className = "Foo")
    path("src/main/resources/foo/foo.properties").writeText("name=foo")

    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(depJar)}
        }
        $shadowJarTask {
          relocate 'foo', 'bar'
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "bar/",
        "bar/Foo.class",
        "bar/foo.properties",
        "bar/dep.properties",
        *manifestEntries,
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/294",
  )
  @Test
  fun doNotErrorOnRelocatingJava9Classes() {
    projectScript.appendText(
      """
        dependencies {
          implementation 'org.slf4j:slf4j-api:1.7.21'
          implementation group: 'io.netty', name: 'netty-all', version: '4.0.23.Final'
          implementation group: 'com.google.protobuf', name: 'protobuf-java', version: '2.5.0'
          implementation group: 'org.apache.zookeeper', name: 'zookeeper', version: '3.4.6'
        }
        $shadowJarTask {
          zip64 = true
          relocate 'com.google.protobuf', 'shaded.com.google.protobuf'
          relocate 'io.netty', 'shaded.io.netty'
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    val entries = outputShadowedJar.use { it.entries().toList() }
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
  @MethodSource("preserveLastModifiedProvider")
  fun preserveLastModifiedCorrectly(enableAutoRelocation: Boolean, preserveFileTimestamps: Boolean) {
    // Minus 3 sec to avoid the time difference between the file system and the JVM.
    val currentTimeMillis = System.currentTimeMillis() - 3.seconds.inWholeMilliseconds
    val junitEntryTimeRange = junitRawEntries.map { it.time }.let { it.min()..it.max() }
    writeClass(withImports = true)
    projectScript.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJarTask {
          enableAutoRelocation = $enableAutoRelocation
          preserveFileTimestamps = $preserveFileTimestamps
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    if (enableAutoRelocation) {
      val (relocatedEntries, otherEntries) = outputShadowedJar.use {
        it.entries().toList().partition { entry -> entry.name.startsWith("shadow/") }
      }
      assertThat(relocatedEntries).isNotEmpty()
      assertThat(otherEntries).isNotEmpty()
      val (relocatedDirs, relocatedClasses) = relocatedEntries.partition { it.isDirectory }
      assertThat(relocatedDirs).isNotEmpty()
      assertThat(relocatedClasses).isNotEmpty()

      if (preserveFileTimestamps) {
        relocatedClasses.forEach { entry ->
          // Relocated files should preserve the last modified time of the original files.
          if (entry.time !in junitEntryTimeRange) {
            fail("Relocated file ${entry.name} has an invalid last modified time: ${entry.time}")
          }
        }
        (relocatedDirs + otherEntries).forEach { entry ->
          // Relocated directories and other entries are newly created, so they should be in now time.
          if (entry.time < currentTimeMillis) {
            fail("Relocated directory ${entry.name} has an invalid last modified time: ${entry.time}")
          }
        }
      } else {
        (relocatedEntries + otherEntries).forEach { entry ->
          // All entries should be newly modified, that default to CONSTANT_TIME_FOR_ZIP_ENTRIES.
          if (entry.time != CONSTANT_TIME_FOR_ZIP_ENTRIES) {
            fail("Entry ${entry.name} has an invalid last modified time: ${entry.time}")
          }
        }
      }
    } else {
      val (shadowedEntries, otherEntries) = outputShadowedJar.use {
        it.entries().toList().partition { entry -> entry.name.startsWith("junit/") }
      }
      assertThat(shadowedEntries).isNotEmpty()
      assertThat(otherEntries).isNotEmpty()

      if (preserveFileTimestamps) {
        shadowedEntries.forEach { entry ->
          // Shadowed entries should preserve the last modified time of the original entries.
          if (entry.time !in junitEntryTimeRange) {
            fail("Shadowed entry ${entry.name} has an invalid last modified time: ${entry.time}")
          }
        }
        otherEntries.forEach { entry ->
          // Other entries are newly created, so they should be in now time.
          if (entry.time < currentTimeMillis) {
            fail("Entry ${entry.name} has an invalid last modified time: ${entry.time}")
          }
        }
      } else {
        (shadowedEntries + otherEntries).forEach { entry ->
          // All entries should be newly modified, defaults to CONSTANT_TIME_FOR_ZIP_ENTRIES.
          if (entry.time != CONSTANT_TIME_FOR_ZIP_ENTRIES) {
            fail("Entry ${entry.name} has an invalid last modified time: ${entry.time}")
          }
        }
      }
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/295",
    "https://github.com/GradleUp/shadow/issues/562",
    "https://github.com/GradleUp/shadow/issues/884",
  )
  @Test
  fun preserveKotlinBuiltins() {
    val kotlinJar = buildJar("kotlin.jar") {
      insert("kotlin/kotlin.kotlin_builtins", "This is a Kotlin builtins file.")
    }
    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(kotlinJar)}
        }
        $shadowJarTask {
          relocate('kotlin.', 'foo.kotlin.') {
            exclude('kotlin/kotlin.kotlin_builtins')
          }
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "kotlin/",
        "kotlin/kotlin.kotlin_builtins",
        *manifestEntries,
      )
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun relocateAllPackagesButCertainOne(exclude: Boolean) {
    val relocateConfig = if (exclude) {
      """
        exclude 'junit/**'
        exclude 'META-INF/**'
      """.trimIndent()
    } else {
      ""
    }
    projectScript.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJarTask {
          relocate('', 'foo/') {
            $relocateConfig
          }
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      if (exclude) {
        containsOnly(
          *junitEntries,
          *manifestEntries,
        )
      } else {
        containsOnly(
          "foo/",
          "foo/$manifestEntry",
          *junitEntries.map { "foo/$it" }.toTypedArray(),
        )
      }
    }
  }

  @Test
  fun relocateProjectResourcesOnly() {
    val mainClassEntry = writeClass()
    projectScript.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJarTask {
          configurations = []
          relocate('', 'foo/')
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "foo/",
        "foo/my/",
        "foo/META-INF/",
        "foo/$mainClassEntry",
        "foo/$manifestEntry",
      )
    }
  }

  @ParameterizedTest
  @MethodSource("relocationCliOptionProvider")
  fun enableAutoRelocationByCliOption(enable: Boolean, relocationPrefix: String) {
    val mainClassEntry = writeClass()
    projectScript.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent(),
    )
    val relocatedEntries = junitEntries.map { "$relocationPrefix/$it" }
      .filterNot { it.startsWith("$relocationPrefix/META-INF/") }
      .toTypedArray()

    if (enable) {
      run(shadowJarPath, "--enable-auto-relocation", "--relocation-prefix=$relocationPrefix")
    } else {
      run(shadowJarPath, "--relocation-prefix=$relocationPrefix")
    }

    assertThat(outputShadowedJar).useAll {
      if (enable) {
        containsOnly(
          "my/",
          "$relocationPrefix/",
          mainClassEntry,
          *relocatedEntries,
          *manifestEntries,
        )
      } else {
        containsOnly(
          "my/",
          mainClassEntry,
          *junitEntries,
          *manifestEntries,
        )
      }
    }
  }

  @Test
  fun relocateStringConstantsByDefault() {
    writeClassWithStringRef()
    projectScript.appendText(
      """
        $shadowJarTask {
          manifest {
            attributes '$mainClassAttributeKey': 'my.Main'
          }
          relocate('foo', 'shadow.foo')
        }
      """.trimIndent(),
    )

    run(shadowJarPath)
    val result = runProcess("java", "-jar", outputShadowedJar.use { it.toString() })

    assertThat(result).contains(
      "shadow.foo.Foo",
      "shadow.foo.Bar",
    )
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/232",
    "https://github.com/GradleUp/shadow/issues/606",
  )
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun disableRelocateStringConstants(skipStringConstants: Boolean) {
    writeClassWithStringRef()
    projectScript.appendText(
      """
        $shadowJarTask {
          manifest {
            attributes '$mainClassAttributeKey': 'my.Main'
          }
          relocate('foo', 'shadow.foo') {
            skipStringConstants = $skipStringConstants
          }
        }
      """.trimIndent(),
    )

    run(shadowJarPath)
    val result = runProcess("java", "-jar", outputShadowedJar.use { it.toString() })

    if (skipStringConstants) {
      assertThat(result).contains(
        "foo.Foo",
        "foo.Bar",
      )
    } else {
      assertThat(result).contains(
        "shadow.foo.Foo",
        "shadow.foo.Bar",
      )
    }
  }

  private fun writeClassWithStringRef() {
    writeClass {
      """
        package my;
        public class Main {
          public static void main(String[] args) {
            switch (1) {
              default:
                System.out.println("foo.Foo"); // Test case for string constants used in switch statements.
                break;
            }
            System.out.println("foo.Bar");
          }
        }
      """.trimIndent()
    }
  }

  private companion object {
    @JvmStatic
    fun prefixProvider() = listOf(
      Arguments.of("foo"),
      Arguments.of("new.pkg"),
      Arguments.of("new/path"),
    )

    @JvmStatic
    fun preserveLastModifiedProvider() = listOf(
      Arguments.of(false, false),
      Arguments.of(true, false),
      Arguments.of(false, true),
      Arguments.of(true, true),
    )

    @JvmStatic
    fun relocationCliOptionProvider() = listOf(
      Arguments.of(false, "foo"),
      Arguments.of(false, "bar"),
      Arguments.of(true, "foo"),
      Arguments.of(true, "bar"),
    )
  }
}
