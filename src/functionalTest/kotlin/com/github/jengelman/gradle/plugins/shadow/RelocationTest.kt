package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.fail
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.CONSTANT_TIME_FOR_ZIP_ENTRIES
import com.github.jengelman.gradle.plugins.shadow.testkit.classLoader
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getBytes
import com.github.jengelman.gradle.plugins.shadow.testkit.isAssignableFrom
import com.github.jengelman.gradle.plugins.shadow.testkit.loadClass
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import com.github.jengelman.gradle.plugins.shadow.util.runProcess
import kotlin.io.path.appendText
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class RelocationTest : BasePluginTest() {
  @ParameterizedTest
  @ValueSource(strings = ["foo", "new.pkg", "new/path"])
  fun autoRelocation(relocationPrefix: String) {
    val mainClassEntry = writeClass()
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |$shadowJarTask {
      |  enableAutoRelocation = true
      |  relocationPrefix = '$relocationPrefix'
      |}
      """
        .trimMargin()
    )
    val entryPrefix = relocationPrefix.replace('.', '/')
    val relocatedEntries = buildSet {
      addAll(
        junitEntries
          .map { "$entryPrefix/$it" }
          .filterNot { it.startsWith("$entryPrefix/META-INF/") }
      )
      var parent = entryPrefix
      while (parent.isNotEmpty()) {
        add("$parent/")
        parent = parent.substringBeforeLast('/', "")
      }
    }
      .toTypedArray()

    val result = runWithSuccess(shadowJarPath, infoArgument)

    assertThat(outputShadowedJar).useAll {
      containsOnly("my/", mainClassEntry, *relocatedEntries, *manifestEntries)
      classLoader {
        val pkg = relocationPrefix.replace('/', '.')
        loadClass("my.Main")
        loadClass("$pkg.junit.framework.Test")
      }
    }
    // Make sure the relocator count is aligned with the number of unique packages in junit jar.
    assertThat(result.output).contains("Relocator count: 6.")
  }

  @ParameterizedTest
  @MethodSource("relocationCliOptionProvider")
  fun enableAutoRelocationByCliOption(enable: Boolean, relocationPrefix: String) {
    val mainClassEntry = writeClass()
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      """
        .trimMargin()
    )
    val relocatedEntries =
      junitEntries
        .map { "$relocationPrefix/$it" }
        .filterNot { it.startsWith("$relocationPrefix/META-INF/") }
        .toTypedArray()

    if (enable) {
      runWithSuccess(
        shadowJarPath,
        "--enable-auto-relocation",
        "--relocation-prefix=$relocationPrefix",
      )
    } else {
      runWithSuccess(
        shadowJarPath,
        "--no-enable-auto-relocation",
        "--relocation-prefix=$relocationPrefix",
      )
    }

    val commonEntries = arrayOf("my/", mainClassEntry, *manifestEntries)
    assertThat(outputShadowedJar).useAll {
      if (enable) {
        containsOnly("$relocationPrefix/", *relocatedEntries, *commonEntries)
      } else {
        containsOnly(*junitEntries, *commonEntries)
      }
      classLoader {
        val testClassName =
          if (enable) "$relocationPrefix.junit.framework.Test" else "junit.framework.Test"
        loadClass(testClassName)
      }
    }
  }

  @Test // #58
  fun relocateDependencyFiles() {
    val mainClassEntry = writeClass()
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |$shadowJarTask {
      |  relocate 'junit.runner', 'a'
      |  relocate 'junit.framework', 'b'
      |}
      """
        .trimMargin()
    )
    val runnerFilter = { it: String -> it.startsWith("junit/runner/") }
    val frameworkFilter = { it: String -> it.startsWith("junit/framework/") }
    val runnerEntries =
      junitEntries.filter(runnerFilter).map { it.replace("junit/runner/", "a/") }.toTypedArray()
    val frameworkEntries =
      junitEntries
        .filter(frameworkFilter)
        .map { it.replace("junit/framework/", "b/") }
        .toTypedArray()
    val otherJunitEntries =
      junitEntries.filterNot { runnerFilter(it) || frameworkFilter(it) }.toTypedArray()

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "my/",
        mainClassEntry,
        *runnerEntries,
        *frameworkEntries,
        *otherJunitEntries,
        *manifestEntries,
      )
      classLoader {
        loadClass("a.BaseTestRunner")
        loadClass("b.Test")
      }
    }
  }

  @Test
  fun relocateDependencyFilesWithFiltering() {
    val mainClassEntry = writeClass()
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |$shadowJarTask {
      |  relocate('junit.runner', 'a') {
      |    exclude 'junit.runner.BaseTestRunner'
      |  }
      |  relocate('junit.framework', 'b') {
      |    include 'junit.framework.Test*'
      |  }
      |}
      """
        .trimMargin()
    )
    val runnerFilter = { it: String ->
      it.startsWith("junit/runner/") && it != "junit/runner/BaseTestRunner.class"
    }
    val frameworkFilter = { it: String -> it.startsWith("junit/framework/Test") }
    val runnerEntries =
      junitEntries.filter(runnerFilter).map { it.replace("junit/runner/", "a/") }.toTypedArray()
    val frameworkEntries =
      junitEntries
        .filter(frameworkFilter)
        .map { it.replace("junit/framework/", "b/") }
        .toTypedArray()
    val otherJunitEntries =
      junitEntries.filterNot { runnerFilter(it) || frameworkFilter(it) }.toTypedArray()

    runWithSuccess(shadowJarPath)

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
      classLoader {
        loadClass("junit.runner.BaseTestRunner")
        loadClass("a.StandardTestSuiteLoader")
        loadClass("b.Test")
        loadClass("junit.framework.Assert")
      }
    }
  }

  @Test // #53, #55
  fun remapClassNamesForRelocatedFilesInProjectSource() {
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |$shadowJarTask {
      |  relocate 'junit.framework', 'shadow.junit'
      |}
      """
        .trimMargin()
    )
    val relocatedEntries =
      junitEntries.map { it.replace("junit/framework/", "shadow/junit/") }.toTypedArray()

    path("src/main/java/my/MyTest.java")
      .writeText(
        """
        |package my;
        |import junit.framework.Test;
        |import junit.framework.TestResult;
        |public class MyTest implements Test {
        |  public int countTestCases() { return 0; }
        |  public void run(TestResult result) { }
        |}
        """
          .trimMargin()
      )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("my/", "shadow/", "my/MyTest.class", *relocatedEntries, *manifestEntries)
      classLoader {
        val myTest = loadClass("my.MyTest")
        val test = loadClass("shadow.junit.Test")
        test.isAssignableFrom(myTest)
      }
    }
  }

  @Test // #93, #114
  fun relocateResourceFiles() {
    val depJar = buildJar("foo.jar") { insert("foo/dep.properties", "c") }
    writeClass(packageName = "foo", className = "Foo")
    path("src/main/resources/foo/foo.properties").writeText("name=foo")

    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(depJar)}
      |}
      |$shadowJarTask {
      |  relocate 'foo', 'bar'
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "bar/",
        "bar/Foo.class",
        "bar/foo.properties",
        "bar/dep.properties",
        *manifestEntries,
      )
      classLoader {
        loadClass("bar.Foo")
      }
    }
  }

  @ParameterizedTest
  @MethodSource("preserveLastModifiedProvider")
  fun preserveLastModifiedCorrectly(
    enableAutoRelocation: Boolean,
    preserveFileTimestamps: Boolean,
  ) {
    // Minus 3 sec to avoid the time difference between the file system and the JVM.
    val currentTimeMillis = System.currentTimeMillis() - 3.seconds.inWholeMilliseconds
    val junitEntryTimeRange = junitRawEntries.map { it.time }.let { it.min()..it.max() }
    writeClass(withImports = true)
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |$shadowJarTask {
      |  enableAutoRelocation = $enableAutoRelocation
      |  preserveFileTimestamps = $preserveFileTimestamps
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    if (enableAutoRelocation) {
      val (relocatedEntries, otherEntries) =
        outputShadowedJar.use {
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
          // Relocated directories and other entries are newly created, so they should be in now
          // time.
          if (entry.time < currentTimeMillis) {
            fail(
              "Relocated directory ${entry.name} has an invalid last modified time: ${entry.time}"
            )
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
      val (shadowedEntries, otherEntries) =
        outputShadowedJar.use {
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

  @Test // #295, #562, #884
  fun excludeKotlinBuiltinsFromRelocation() {
    val kotlinJar =
      buildJar("kotlin.jar") {
        insert("kotlin/kotlin.kotlin_builtins", "This is a Kotlin builtins file.")
      }
    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(kotlinJar)}
      |}
      |$shadowJarTask {
      |  relocate('kotlin.', 'foo.kotlin.') {
      |    exclude('kotlin/kotlin.kotlin_builtins')
      |  }
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("kotlin/", "kotlin/kotlin.kotlin_builtins", *manifestEntries)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun relocateAllPackagesButCertainOne(exclude: Boolean) {
    val relocateConfig =
      if (exclude) {
        """
        |exclude 'junit/**'
        |exclude 'META-INF/**'
        """
          .trimMargin()
      } else {
        ""
      }
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |$shadowJarTask {
      |  relocate('', 'foo/') {
      |    $relocateConfig
      |  }
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      if (exclude) {
        containsOnly(*junitEntries, *manifestEntries)
      } else {
        containsOnly("foo/", "foo/$manifestEntry", *junitEntries.map { "foo/$it" }.toTypedArray())
      }
    }
  }

  @Test
  fun relocateProjectResourcesOnly() {
    val mainClassEntry = writeClass()
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |$shadowJarTask {
      |  configurations = []
      |  relocate('', 'foo/')
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("foo/", "foo/my/", "foo/META-INF/", "foo/$mainClassEntry", "foo/$manifestEntry")
    }
  }

  @Test
  fun relocateStringConstantsByDefault() {
    writeClassWithStringRef()
    projectScript.appendText(
      """
      |$shadowJarTask {
      |  manifest {
      |    attributes '$mainClassAttributeKey': 'my.Main'
      |  }
      |  relocate('foo', 'shadow.foo')
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)
    val result = runProcess("java", "-jar", outputShadowedJar.use { it.toString() })

    assertThat(result.trim().lines()).containsExactly("shadow.foo.Foo", "shadow.foo.Bar")
  }

  @ParameterizedTest // #232, #606
  @ValueSource(booleans = [false, true])
  fun disableStringConstantsRelocation(skipStringConstants: Boolean) {
    writeClassWithStringRef()
    projectScript.appendText(
      """
      |$shadowJarTask {
      |  manifest {
      |    attributes '$mainClassAttributeKey': 'my.Main'
      |  }
      |  relocate('foo', 'shadow.foo') {
      |    skipStringConstants = $skipStringConstants
      |  }
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)
    val result = runProcess("java", "-jar", outputShadowedJar.use { it.toString() })

    if (skipStringConstants) {
      assertThat(result.trim().lines()).containsExactly("foo.Foo", "foo.Bar")
    } else {
      assertThat(result.trim().lines()).containsExactly("shadow.foo.Foo", "shadow.foo.Bar")
    }
  }

  @Test // #1403
  fun relocateMultiClassSignatureStringConstants() {
    writeClass {
      """
      |package my;
      |public class Main {
      |  public static void main(String[] args) {
      |    System.out.println("Lorg/package/ClassA;Lorg/package/ClassB;");
      |    System.out.println("(Lorg/package/ClassC;Lorg/package/ClassD;)");
      |    System.out.println("()Lorg/package/ClassE;Lorg/package/ClassF;");
      |  }
      |}
      """
        .trimMargin()
    }
    projectScript.appendText(
      """
      |$shadowJarTask {
      |  manifest {
      |    attributes '$mainClassAttributeKey': 'my.Main'
      |  }
      |  relocate('org.package', 'shadow.org.package')
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)
    val result = runProcess("java", "-jar", outputShadowedJar.use { it.toString() })

    // Just check that the jar can be executed without NoClassDefFoundError.
    assertThat(result.trim().lines())
      .containsExactly(
        "Lshadow/org/package/ClassA;Lshadow/org/package/ClassB;",
        "(Lshadow/org/package/ClassC;Lshadow/org/package/ClassD;)",
        "()Lshadow/org/package/ClassE;Lshadow/org/package/ClassF;",
      )
  }

  @Test
  fun classBytesUnchangedIfPossible() {
    val mainClassEntry = writeClass()
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |$shadowJarTask {
      |  enableAutoRelocation = true
      |}
      """
        .trimMargin()
    )

    runWithSuccess(":jar", shadowJarPath)

    val originalBytes = outputJar.use { it.getBytes(mainClassEntry) }
    val relocatedBytes = outputShadowedJar.use { it.getBytes(mainClassEntry) }
    assertThat(relocatedBytes).isEqualTo(originalBytes)
  }

  @ParameterizedTest // #843
  @ValueSource(booleans = [false, true])
  fun relocateKotlinModuleFiles(enableKotlinModuleRemapping: Boolean) {
    val originalModuleFilePath = "META-INF/kotlin-stdlib.kotlin_module"
    val originalModuleFileBytes = requireResourceAsPath(originalModuleFilePath).readBytes()
    val stdlibJar =
      buildJar("stdlib.jar") { insert(originalModuleFilePath, originalModuleFileBytes) }
    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(stdlibJar)}
      |}
      |$shadowJarTask {
      |  relocate('kotlin', 'my.kotlin')
      |  enableKotlinModuleRemapping = $enableKotlinModuleRemapping
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    val relocatedModuleFilePath = "META-INF/kotlin-stdlib.shadow.kotlin_module"

    if (enableKotlinModuleRemapping) {
      assertThat(outputShadowedJar).useAll {
        containsOnly(relocatedModuleFilePath, *manifestEntries)
        getBytes(relocatedModuleFilePath).isNotEqualTo(originalModuleFileBytes)
      }
    } else {
      assertThat(outputShadowedJar).useAll {
        containsOnly(originalModuleFilePath, *manifestEntries)
        getBytes(originalModuleFilePath).isEqualTo(originalModuleFileBytes)
      }
      return
    }
  }

  @Test
  fun relocateWithR8() {
    writeClass(packageName = "my", withImports = false) {
      """
      |package my;
      |import foo.Foo;
      |public class Main {
      |  public static void main(String[] args) {
      |    System.out.println(Foo.class);
      |  }
      |}
      """
        .trimMargin()
    }
    val fooJar =
      buildJar("foo.jar") {
        insert("foo/Foo.class", createEmptyClassBytes("foo/Foo"))
      }

    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(fooJar)}
      |}
      |$shadowJarTask {
      |  minimize {
      |    r8 {
      |      proguardRules.addAll(
      |        "-repackageclasses 'relocated'",
      |      )
      |    }
      |  }
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "my/Main.class",
        "relocated/foo/Foo.class",
        manifestEntry,
      )
      classLoader {
        loadClass("my.Main")
        loadClass("relocated.foo.Foo")
      }
    }
  }

  @Test // #1534
  fun relocateCaseSensitiveAndInsensitiveClassesInJar() {
    val fooJar =
      buildJar("foo.jar") {
        insert("foo/Bar.class", createEmptyClassBytes("foo/Bar"))
        insert("foo/bar.class", createEmptyClassBytes("foo/bar"))
      }
    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(fooJar)}
      |}
      |$shadowJarTask {
      |  relocate 'foo', 'shadow.foo'
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "shadow/",
        "shadow/foo/",
        "shadow/foo/Bar.class",
        "shadow/foo/bar.class",
        *manifestEntries,
      )

      val upperBytes = getBytes("shadow/foo/Bar.class")
      val lowerBytes = getBytes("shadow/foo/bar.class")
      assertThat(upperBytes).isNotEqualTo(lowerBytes)

      classLoader {
        loadClass("shadow.foo.Bar")
        loadClass("shadow.foo.bar")
      }
    }
  }

  private fun writeClassWithStringRef() {
    writeClass {
      """
      |package my;
      |public class Main {
      |  public static void main(String[] args) {
      |    switch (1) {
      |      default:
      |        System.out.println("foo.Foo"); // Test case for string constants used in switch statements.
      |        break;
      |    }
      |    System.out.println("foo.Bar");
      |  }
      |}
      """
        .trimMargin()
    }
  }

  private companion object {
    @JvmStatic
    fun preserveLastModifiedProvider() =
      listOf(
        Arguments.of(false, false),
        Arguments.of(true, false),
        Arguments.of(false, true),
        Arguments.of(true, true),
      )

    @JvmStatic
    fun relocationCliOptionProvider() =
      listOf(
        Arguments.of(false, "foo"),
        Arguments.of(false, "bar"),
        Arguments.of(true, "foo"),
        Arguments.of(true, "bar"),
      )
  }
}
