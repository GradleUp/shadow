package com.github.jengelman.gradle.plugins.shadow

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsMatch
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.single
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.internal.classPathAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.multiReleaseAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.runtimeConfiguration
import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.util.containsNone
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import com.github.jengelman.gradle.plugins.shadow.util.getStream
import com.github.jengelman.gradle.plugins.shadow.util.runProcess
import kotlin.io.path.appendText
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledForJreRange
import org.junit.jupiter.api.condition.JRE
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class JavaPluginTest : BasePluginTest() {
  @Test
  fun applyPlugin() {
    val projectName = "my-shadow"
    val version = "1.0.0"

    val project = ProjectBuilder.builder().withName(projectName).build().also {
      it.version = version
    }
    project.plugins.apply(ShadowPlugin::class.java)

    assertThat(project.plugins.hasPlugin(ShadowPlugin::class.java)).isTrue()
    assertThat(project.plugins.hasPlugin(LegacyShadowPlugin::class.java)).isTrue()
    assertThat(project.tasks.findByName(SHADOW_JAR_TASK_NAME)).isNull()

    project.plugins.apply(JavaPlugin::class.java)
    val shadowTask = project.tasks.getByName(SHADOW_JAR_TASK_NAME) as ShadowJar
    val shadowConfig = project.configurations.getByName(ShadowBasePlugin.CONFIGURATION_NAME)

    // Check extended properties.
    with(shadowTask as Jar) {
      assertThat(archiveAppendix.orNull).isNull()
      assertThat(archiveBaseName.get()).isEqualTo(projectName)
      assertThat(archiveClassifier.get()).isEqualTo("all")
      assertThat(archiveExtension.get()).isEqualTo("jar")
      assertThat(archiveFileName.get()).isEqualTo("my-shadow-1.0.0-all.jar")
      assertThat(archiveVersion.get()).isEqualTo(version)
      assertThat(archiveFile.get().asFile).all {
        isEqualTo(destinationDirectory.file(archiveFileName).get().asFile)
        isEqualTo(project.projectDir.resolve("build/libs/my-shadow-1.0.0-all.jar"))
      }
      assertThat(destinationDirectory.get().asFile)
        .isEqualTo(project.layout.buildDirectory.dir("libs").get().asFile)
    }

    // Check self properties.
    with(shadowTask) {
      assertThat(minimizeJar.get()).isFalse()
      assertThat(enableRelocation.get()).isFalse()
      assertThat(relocationPrefix.get()).isEqualTo(ShadowBasePlugin.SHADOW)
      assertThat(configurations.get()).all {
        isNotEmpty()
        containsOnly(project.runtimeConfiguration)
      }
    }

    assertThat(shadowConfig.artifacts.files).contains(shadowTask.archiveFile.get().asFile)
  }

  @Test
  @DisabledForJreRange(
    min = JRE.JAVA_21,
    disabledReason = "Gradle 8.3 doesn't support Java 21.",
  )
  fun compatibleWithMinGradleVersion() {
    val mainClassEntry = writeClass(withImports = true)
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent(),
    )

    run(shadowJarTask) {
      it.withGradleVersion("8.3")
    }

    assertThat(outputShadowJar).useAll {
      containsOnly(
        "my/",
        mainClassEntry,
        *junitEntries,
        *manifestEntries,
      )
    }
  }

  @Test
  fun incompatibleWithLowerMinGradleVersion() {
    runWithFailure(shadowJarTask) {
      it.withGradleVersion("8.2")
    }
  }

  @Test
  fun shadowJarCliOptions() {
    val result = run("help", "--task", shadowJarTask)

    assertThat(result.output).contains(
      "--enable-relocation     Enable relocation of packages in the jar",
      "--no-enable-relocation     Disables option --enable-relocation",
      "--minimize-jar     Minimize the jar by removing unused classes",
      " --no-minimize-jar     Disables option --minimize-jar",
      "--relocation-prefix     Prefix to use for relocated packages",
    )
  }

  @Test
  fun includeProjectDependencies() {
    writeClientAndServerModules()

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).useAll {
      containsOnly(
        "client/",
        "server/",
        "client/Client.class",
        "server/Server.class",
        *junitEntries,
        *manifestEntries,
      )
    }
  }

  @Test
  fun dependOnProjectShadowJar() {
    writeClientAndServerModules(clientShadowed = true)

    run(":server:jar")

    assertThat(jarPath("server/build/libs/server-1.0.jar")).useAll {
      containsOnly(
        "server/",
        "server/Server.class",
        *manifestEntries,
      )
    }
    assertThat(jarPath("client/build/libs/client-1.0-all.jar")).useAll {
      containsAtLeast(
        "client/",
        "client/Client.class",
        "client/junit/framework/Test.class",
      )
      containsNone(
        "server/Server.class",
      )
    }
  }

  @Test
  fun shadowProjectShadowJar() {
    writeClientAndServerModules(clientShadowed = true)
    val relocatedEntries = junitEntries
      .map { it.replace("junit/framework/", "client/junit/framework/") }.toTypedArray()

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).useAll {
      containsOnly(
        "client/",
        "server/",
        "client/junit/",
        "client/Client.class",
        "server/Server.class",
        *relocatedEntries,
        *manifestEntries,
      )
    }
    assertThat(jarPath("client/build/libs/client-1.0-all.jar")).useAll {
      containsAtLeast(
        "client/Client.class",
        "client/junit/framework/Test.class",
      )
      containsNone(
        "server/Server.class",
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/449",
  )
  @Test
  fun containsMultiReleaseAttrIfAnyDependencyContainsIt() {
    writeClientAndServerModules()
    path("client/build.gradle").appendText(
      """
        jar {
          manifest {
            attributes '$multiReleaseAttributeKey': 'true'
          }
        }
      """.trimIndent() + System.lineSeparator(),
    )

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).useAll {
      transform { it.mainAttrSize }.isGreaterThan(1)
      getMainAttr(multiReleaseAttributeKey).isEqualTo("true")
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/352",
    "https://github.com/GradleUp/shadow/issues/729",
  )
  @Test
  fun excludeSomeMetaInfFilesByDefault() {
    localRepo.module("my", "a", "1.0") {
      buildJar {
        insert("a.properties", "a")
        insert("META-INF/INDEX.LIST", "JarIndex-Version: 1.0")
        insert("META-INF/a.SF", "Signature File")
        insert("META-INF/a.DSA", "DSA Signature Block")
        insert("META-INF/a.RSA", "RSA Signature Block")
        insert("META-INF/a.properties", "key=value")
        insert("META-INF/versions/9/module-info.class", "module myModuleName {}")
        insert("META-INF/versions/16/module-info.class", "module myModuleName {}")
        insert("module-info.class", "module myModuleName {}")
      }
    }.publish()

    path("src/main/java/my/Passed.java").writeText(
      """
        package my;
        public class Passed {}
      """.trimIndent(),
    )
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsOnly(
        "my/",
        "my/Passed.class",
        "a.properties",
        "META-INF/a.properties",
        *manifestEntries,
      )
    }
  }

  @Test
  fun includeRuntimeConfigurationByDefault() {
    projectScriptPath.appendText(
      """
        dependencies {
          runtimeOnly 'my:a:1.0'
          shadow 'my:b:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsOnly(
        *entriesInA,
        *manifestEntries,
      )
    }
  }

  @Test
  fun includeJavaLibraryConfigurationsByDefault() {
    localRepo.module("my", "api", "1.0") {
      buildJar {
        insert("api.properties", "api")
      }
    }.module("my", "implementation-dep", "1.0") {
      buildJar {
        insert("implementation-dep.properties", "implementation-dep")
      }
    }.module("my", "implementation", "1.0") {
      buildJar {
        insert("implementation.properties", "implementation")
      }
      addDependency("my", "implementation-dep", "1.0")
    }.module("my", "runtimeOnly", "1.0") {
      buildJar {
        insert("runtimeOnly.properties", "runtimeOnly")
      }
    }.publish()

    projectScriptPath.writeText(
      """
        ${getDefaultProjectBuildScript("java-library", withGroup = true, withVersion = true)}
        dependencies {
          api 'my:api:1.0'
          implementation 'my:implementation:1.0'
          runtimeOnly 'my:runtimeOnly:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsOnly(
        "api.properties",
        "implementation.properties",
        "runtimeOnly.properties",
        "implementation-dep.properties",
        *manifestEntries,
      )
    }
  }

  @Test
  fun doesNotIncludeCompileOnlyConfigurationByDefault() {
    projectScriptPath.appendText(
      """
        dependencies {
          runtimeOnly 'my:a:1.0'
          compileOnly 'my:b:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsOnly(
        *entriesInA,
        *manifestEntries,
      )
    }
  }

  @Test
  fun defaultCopyingStrategy() {
    localRepo.module("my", "a", "1.0") {
      buildJar {
        insert(manifestEntry, "MANIFEST A")
      }
    }.module("my", "b", "1.0") {
      buildJar {
        insert(manifestEntry, "MANIFEST B")
      }
    }.publish()

    projectScriptPath.appendText(
      """
        dependencies {
          runtimeOnly 'my:a:1.0'
          runtimeOnly 'my:b:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val entries = outputShadowJar.use { it.entries().toList() }
    assertThat(entries.size).isEqualTo(2)
  }

  @Test
  fun classPathInManifestNotAddedIfEmpty() {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val value = outputShadowJar.use { it.getMainAttr(classPathAttributeKey) }
    assertThat(value).isNull()
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/65",
  )
  @Test
  fun addShadowConfigurationToClassPathInManifest() {
    projectScriptPath.appendText(
      """
        dependencies {
          shadow 'junit:junit:3.8.2'
        }
        jar {
          manifest {
            attributes '$classPathAttributeKey': '/libs/a.jar'
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val value = outputShadowJar.use { it.getMainAttr(classPathAttributeKey) }
    assertThat(value).isEqualTo("/libs/a.jar junit-3.8.2.jar")
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/92",
  )
  @Test
  fun doNotIncludeNullValueInClassPathWhenJarFileDoesNotContainClassPath() {
    projectScriptPath.appendText(
      """
        dependencies {
          shadow 'junit:junit:3.8.2'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    val value = outputShadowJar.use { it.getMainAttr(classPathAttributeKey) }
    assertThat(value).isEqualTo("junit-3.8.2.jar")
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/203",
  )
  @Test
  fun supportZipCompressionStored() {
    projectScriptPath.appendText(
      """
        dependencies {
          shadow 'junit:junit:3.8.2'
        }
        $shadowJar {
          zip64 = true
          entryCompression = org.gradle.api.tasks.bundling.ZipEntryCompression.STORED
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      transform { it.entries().toList() }.isNotEmpty()
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/459",
    "https://github.com/GradleUp/shadow/issues/852",
  )
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun excludeGradleApiByDefault(legacy: Boolean) {
    writeGradlePluginModule(legacy)
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
          compileOnly 'my:b:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      transform { actual -> actual.entries().toList().map { it.name }.filter { it.endsWith(".class") } }
        .single().isEqualTo("my/plugin/MyPlugin.class")
      transform { it.mainAttrSize }.isGreaterThan(0)
      // Doesn't contain Gradle classes.
      getMainAttr(classPathAttributeKey).isNull()

      containsOnly(
        "my/",
        "my/plugin/",
        "my/plugin/MyPlugin.class",
        "META-INF/gradle-plugins/",
        "META-INF/gradle-plugins/my.plugin.properties",
        *entriesInA,
        *manifestEntries,
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/1070",
  )
  @Test
  fun canRegisterCustomShadowJarTask() {
    val mainClassEntry = writeClass(sourceSet = "test", withImports = true)
    val testShadowJarTask = "testShadowJar"
    projectScriptPath.appendText(
      """
        dependencies {
          testImplementation 'junit:junit:3.8.2'
        }
        def $testShadowJarTask = tasks.register('$testShadowJarTask', ${ShadowJar::class.java.name}) {
          group = LifecycleBasePlugin.BUILD_GROUP
          description = 'Create a combined JAR of project and test dependencies'
          archiveClassifier = 'tests'
          from sourceSets.named('test').map { it.output }
          configurations = project.configurations.named('testRuntimeClasspath').map { [it] }
          manifest {
            attributes '$mainClassAttributeKey': 'my.Main'
          }
        }
      """.trimIndent(),
    )

    run(testShadowJarTask)

    assertThat(jarPath("build/libs/my-1.0-tests.jar")).useAll {
      containsOnly(
        "my/",
        mainClassEntry,
        *junitEntries,
        *manifestEntries,
      )
      getMainAttr(mainClassAttributeKey).isNotNull()
    }

    val pathString = path("build/libs/my-1.0-tests.jar").toString()
    val runningOutput = runProcess("java", "-jar", pathString, "foo")
    assertThat(runningOutput).contains(
      "Hello, World! (foo) from Main",
      "Refs: junit.framework.Test",
    )
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/915",
  )
  @Test
  fun failBuildIfProcessingBadJar() {
    val badJarPath = path("bad.jar").apply {
      writeText("A bad jar.")
    }

    projectScriptPath.appendText(
      """
        dependencies {
          ${implementationFiles(badJarPath)}
        }
      """.trimIndent(),
    )

    val result = runWithFailure(shadowJarTask)

    assertThat(result.output).containsMatch("Cannot expand ZIP '.*bad\\.jar'".toRegex())
  }

  @Test
  fun worksWithArchiveFileName() {
    val mainClassEntry = writeClass()
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJar {
          archiveFileName = 'my-shadow.tar'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(jarPath("build/libs/my-shadow.tar")).useAll {
      containsOnly(
        "my/",
        mainClassEntry,
        *junitEntries,
        *manifestEntries,
      )
    }
  }

  @Test
  fun canInheritFromOtherManifest() {
    projectScriptPath.appendText(
      """
        jar {
          manifest {
            attributes 'Foo-Attr': 'Foo-Value'
          }
        }
        def testJar = tasks.register('testJar', Jar) {
          manifest {
            attributes 'Bar-Attr': 'Bar-Value'
          }
        }
        $shadowJar {
          manifest.inheritFrom(testJar.get().manifest)
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      transform { it.mainAttrSize }.isGreaterThan(2)
      getMainAttr("Foo-Attr").isEqualTo("Foo-Value")
      getMainAttr("Bar-Attr").isEqualTo("Bar-Value")
    }
  }

  @Test
  fun canAddExtraFilesIntoShadowJar() {
    val mainClassEntry = writeClass()
    path("Foo").writeText("Foo")
    projectScriptPath.appendText(
      """
        $shadowJar {
          from(file('${artifactAJar.toUri().toURL().path}')) {
            into('META-INF')
          }
          from('Foo') {
            into('Bar')
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsOnly(
        "my/",
        "Bar/",
        "Bar/Foo",
        "META-INF/a-1.0.jar",
        mainClassEntry,
        *manifestEntries,
      )
      getContent("Bar/Foo").isEqualTo("Foo")
    }
    val unzipped = path("unzipped")
    outputShadowJar.use {
      it.getStream("META-INF/a-1.0.jar").use { inputStream ->
        inputStream.copyTo(unzipped.outputStream())
      }
    }
    assertThat(jarPath(unzipped.name)).useAll {
      containsOnly(*entriesInA)
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/520",
  )
  @Test
  fun onlyKeepFilesFromProjectWhenDuplicatesStrategyIsExclude() {
    val fooJar = buildJar("foo.jar") {
      insert("module-info.class", "module myModuleName {}")
    }
    val mainClassEntry = writeClass()
    writeClass(className = "module-info") {
      "module myModuleName {}"
    }
    projectScriptPath.appendText(
      """
        dependencies {
          ${implementationFiles(fooJar)}
        }
        $shadowJar {
          duplicatesStrategy = DuplicatesStrategy.EXCLUDE
          excludes.remove(
            'module-info.class'
          )
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsOnly(
        "module-info.class",
        "my/",
        mainClassEntry,
        *manifestEntries,
      )
      getContent("module-info.class").all {
        isNotEmpty()
        // It's the compiled class instead of the original content.
        isNotEqualTo("module myModuleName {}")
      }
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/1441",
  )
  @Test
  fun includeFilesInTaskOutputDirectory() {
    // Create a build that has a task with jars in the output directory
    projectScriptPath.appendText(
      """
      def createJars = tasks.register('createJars') {
        def artifactAJar = file('${artifactAJar.toUri().toURL().path}')
        def artifactBJar = file('${artifactBJar.toUri().toURL().path}')
        inputs.files(artifactAJar, artifactBJar)
        def outputDir = file('${'$'}{buildDir}/jars')
        outputs.dir(outputDir)
        doLast {
          artifactAJar.withInputStream { input ->
              new File(outputDir, 'jarA.jar').withOutputStream { output ->
                  output << input
              }
          }
          artifactBJar.withInputStream { input ->
              new File(outputDir, 'jarB.jar').withOutputStream { output ->
                  output << input
              }
          }
        }
      }
      $shadowJar {
        includedDependencies.from(files(createJars).asFileTree)
      }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsOnly(*entriesInAB, *manifestEntries)
    }
  }
}
