package com.github.jengelman.gradle.plugins.shadow

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.fail
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import java.net.URLClassLoader
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.opentest4j.AssertionFailedError

class RelocationTest : BasePluginTest() {
  @ParameterizedTest
  @MethodSource("prefixProvider")
  fun defaultEnableRelocation(relocationPrefix: String) {
    writeMainClass()
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
    val junitEntries = arrayOf(
      "junit/textui/ResultPrinter.class",
      "junit/textui/TestRunner.class",
      "junit/framework/Assert.class",
      "junit/framework/AssertionFailedError.class",
      "junit/framework/ComparisonCompactor.class",
      "junit/framework/ComparisonFailure.class",
      "junit/framework/Protectable.class",
      "junit/framework/Test.class",
      "junit/framework/TestCase.class",
      "junit/framework/TestFailure.class",
      "junit/framework/TestListener.class",
      "junit/framework/TestResult.class",
      "junit/framework/TestSuite.class",
    )
    val entryPrefix = relocationPrefix.replace('.', '/')

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        "META-INF/MANIFEST.MF",
        "my/Main.class",
        *junitEntries.map { "$entryPrefix/$it" }.toTypedArray(),
      )
      doesNotContainEntries(
        "$entryPrefix/my/Main.class",
        *junitEntries,
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/58",
  )
  @Test
  fun relocateDependencyFiles() {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJar {
          relocate 'junit.textui', 'a'
          relocate 'junit.framework', 'b'
          manifest {
            attributes 'TEST-VALUE': 'FOO'
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        "META-INF/MANIFEST.MF",
        "a/ResultPrinter.class",
        "a/TestRunner.class",
        "b/Assert.class",
        "b/AssertionFailedError.class",
        "b/ComparisonCompactor.class",
        "b/ComparisonFailure.class",
        "b/Protectable.class",
        "b/Test.class",
        "b/TestCase.class",
        "b/TestFailure.class",
        "b/TestListener.class",
        "b/TestResult\$1.class",
        "b/TestResult.class",
        "b/TestSuite\$1.class",
        "b/TestSuite.class",
      )
      doesNotContainEntries(
        "junit/textui/ResultPrinter.class",
        "junit/textui/TestRunner.class",
        "junit/framework/Assert.class",
        "junit/framework/AssertionFailedError.class",
        "junit/framework/ComparisonCompactor.class",
        "junit/framework/ComparisonFailure.class",
        "junit/framework/Protectable.class",
        "junit/framework/Test.class",
        "junit/framework/TestCase.class",
        "junit/framework/TestFailure.class",
        "junit/framework/TestListener.class",
        "junit/framework/TestResult\$1.class",
        "junit/framework/TestResult.class",
        "junit/framework/TestSuite\$1.class",
        "junit/framework/TestSuite.class",
      )
      getMainAttr("TEST-VALUE").isEqualTo("FOO")
    }
  }

  @Test
  fun relocateDependencyFilesWithFiltering() {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJar {
          relocate('junit.textui', 'a') {
            exclude 'junit.textui.TestRunner'
          }
          relocate('junit.framework', 'b') {
            include 'junit.framework.Test*'
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        "a/ResultPrinter.class",
        "b/Test.class",
        "b/TestCase.class",
        "b/TestFailure.class",
        "b/TestListener.class",
        "b/TestResult\$1.class",
        "b/TestResult.class",
        "b/TestSuite\$1.class",
        "b/TestSuite.class",
        "junit/textui/TestRunner.class",
        "junit/framework/Assert.class",
        "junit/framework/AssertionFailedError.class",
        "junit/framework/ComparisonCompactor.class",
        "junit/framework/ComparisonFailure.class",
        "junit/framework/Protectable.class",
      )
      doesNotContainEntries(
        "a/TestRunner.class",
        "b/Assert.class",
        "b/AssertionFailedError.class",
        "b/ComparisonCompactor.class",
        "b/ComparisonFailure.class",
        "b/Protectable.class",
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
        "shadow/junit/Test.class",
        "shadow/junit",
      )
      doesNotContainEntries(
        "junit/framework",
        "junit/framework/Test.class",
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

    settingsScriptPath.appendText(
      """
        include 'core', 'app'
      """.trimIndent(),
    )

    run(":app:$SHADOW_JAR_TASK_NAME")

    assertThat(jarPath("app/build/libs/app-all.jar")).useAll {
      containsEntries(
        "TEST",
        "APP-TEST",
        "test.properties",
        "app/core/Core.class",
        "app/App.class",
        "app/junit/framework/Test.class",
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
    // No exception should be thrown
  }

  private companion object {
    @JvmStatic
    fun prefixProvider() = listOf(
      // The default values.
      arrayOf(ShadowBasePlugin.SHADOW),
      arrayOf("new.pkg"),
      arrayOf("new/path"),
    )
  }
}
