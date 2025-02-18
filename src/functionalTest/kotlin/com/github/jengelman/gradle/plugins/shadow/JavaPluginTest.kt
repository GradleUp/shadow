package com.github.jengelman.gradle.plugins.shadow

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.single
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.internal.classPathAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.multiReleaseAttributeKey
import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.util.BooleanParameterizedTest
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import com.github.jengelman.gradle.plugins.shadow.util.runProcess
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledForJreRange
import org.junit.jupiter.api.condition.JRE

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

    assertThat(shadowTask.archiveBaseName.get()).isEqualTo(projectName)
    assertThat(shadowTask.destinationDirectory.get().asFile)
      .isEqualTo(project.layout.buildDirectory.dir("libs").get().asFile)
    assertThat(shadowTask.archiveVersion.get()).isEqualTo(version)
    assertThat(shadowTask.archiveClassifier.get()).isEqualTo("all")
    assertThat(shadowTask.archiveExtension.get()).isEqualTo("jar")
    assertThat(shadowConfig.artifacts.files).contains(shadowTask.archiveFile.get().asFile)
  }

  @Test
  @DisabledForJreRange(
    min = JRE.JAVA_21,
    disabledReason = "Gradle 8.3 doesn't support Java 21.",
  )
  fun compatibleWithMinGradleVersion() {
    writeMainClass(withImports = true)
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
      containsEntries(
        "shadow/Main.class",
        "junit/framework/Test.class",
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
  fun includeProjectSources() {
    path("src/main/java/shadow/Passed.java").writeText(
      """
        package shadow;
        public class Passed {}
      """.trimIndent(),
    )

    projectScriptPath.appendText(
      """
        dependencies {
         implementation 'junit:junit:3.8.2'
        }
        $shadowJar {
          archiveBaseName = 'shadow'
          archiveClassifier = ''
          archiveVersion = ''
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(jarPath("build/libs/shadow.jar")).useAll {
      containsEntries(
        "shadow/Passed.class",
        "junit/framework/Test.class",
      )
      doesNotContainEntries("/")
    }
  }

  @Test
  fun includeProjectDependencies() {
    writeClientAndServerModules()

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).useAll {
      containsEntries(
        "client/Client.class",
        "server/Server.class",
        "junit/framework/Test.class",
      )
    }
  }

  @Test
  fun dependOnProjectShadowJar() {
    writeClientAndServerModules(clientShadowed = true)

    run(":server:jar")

    assertThat(jarPath("server/build/libs/server-1.0.jar")).useAll {
      containsEntries(
        "server/Server.class",
      )
      doesNotContainEntries(
        "client/Client.class",
        "junit/framework/Test.class",
        "client/junit/framework/Test.class",
      )
    }
    assertThat(jarPath("client/build/libs/client-1.0-all.jar")).useAll {
      containsEntries(
        "client/Client.class",
        "client/junit/framework/Test.class",
      )
    }
  }

  @Test
  fun shadowProjectShadowJar() {
    writeClientAndServerModules(clientShadowed = true)

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).useAll {
      containsEntries(
        "client/Client.class",
        "client/junit/framework/Test.class",
        "server/Server.class",
      )
      doesNotContainEntries(
        "junit/framework/Test.class",
      )
    }
    assertThat(jarPath("client/build/libs/client-1.0-all.jar")).useAll {
      containsEntries(
        "client/Client.class",
        "client/junit/framework/Test.class",
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
      transform { it.manifest.mainAttributes }.isNotEmpty()
      getMainAttr(multiReleaseAttributeKey).isEqualTo("true")
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/352",
    "https://github.com/GradleUp/shadow/issues/729",
  )
  @Test
  fun excludeSomeMetaInfFilesByDefault() {
    localRepo.module("shadow", "a", "1.0") {
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

    path("src/main/java/shadow/Passed.java").writeText(
      """
        package shadow;
        public class Passed {}
      """.trimIndent(),
    )
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'shadow:a:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        "shadow/Passed.class",
        "a.properties",
        "META-INF/a.properties",
      )
      doesNotContainEntries(
        "META-INF/INDEX.LIST",
        "META-INF/a.SF",
        "META-INF/a.DSA",
        "META-INF/a.RSA",
        "META-INF/versions/9/module-info.class",
        "META-INF/versions/16/module-info.class",
        "module-info.class",
      )
    }
  }

  @Test
  fun includeRuntimeConfigurationByDefault() {
    projectScriptPath.appendText(
      """
        dependencies {
          runtimeOnly 'shadow:a:1.0'
          shadow 'shadow:b:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(*entriesInA)
      doesNotContainEntries(*entriesInB)
    }
  }

  @Test
  fun includeJavaLibraryConfigurationsByDefault() {
    localRepo.module("shadow", "api", "1.0") {
      buildJar {
        insert("api.properties", "api")
      }
    }.module("shadow", "implementation-dep", "1.0") {
      buildJar {
        insert("implementation-dep.properties", "implementation-dep")
      }
    }.module("shadow", "implementation", "1.0") {
      buildJar {
        insert("implementation.properties", "implementation")
      }
      addDependency("shadow", "implementation-dep", "1.0")
    }.module("shadow", "runtimeOnly", "1.0") {
      buildJar {
        insert("runtimeOnly.properties", "runtimeOnly")
      }
    }.publish()

    projectScriptPath.writeText(
      """
        ${getDefaultProjectBuildScript("java-library", withGroup = true, withVersion = true)}
        dependencies {
          api 'shadow:api:1.0'
          implementation 'shadow:implementation:1.0'
          runtimeOnly 'shadow:runtimeOnly:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        "api.properties",
        "implementation.properties",
        "runtimeOnly.properties",
        "implementation-dep.properties",
      )
    }
  }

  @Test
  fun doesNotIncludeCompileOnlyConfigurationByDefault() {
    projectScriptPath.appendText(
      """
        dependencies {
          runtimeOnly 'shadow:a:1.0'
          compileOnly 'shadow:b:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(*entriesInA)
      doesNotContainEntries(*entriesInB)
    }
  }

  @Test
  fun defaultCopyingStrategy() {
    localRepo.module("shadow", "a", "1.0") {
      buildJar {
        insert("META-INF/MANIFEST.MF", "MANIFEST A")
      }
    }.module("shadow", "b", "1.0") {
      buildJar {
        insert("META-INF/MANIFEST.MF", "MANIFEST B")
      }
    }.publish()

    projectScriptPath.appendText(
      """
        dependencies {
          runtimeOnly 'shadow:a:1.0'
          runtimeOnly 'shadow:b:1.0'
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
  @BooleanParameterizedTest
  fun excludeGradleApiByDefault(legacy: Boolean) {
    writeGradlePluginModule(legacy)
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'shadow:a:1.0'
          compileOnly 'shadow:b:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      transform { actual -> actual.entries().toList().map { it.name }.filter { it.endsWith(".class") } }
        .single().isEqualTo("my/plugin/MyPlugin.class")
      transform { it.manifest.mainAttributes }.isNotEmpty()
      // Doesn't contain Gradle classes.
      getMainAttr(classPathAttributeKey).isNull()

      containsEntries(*entriesInA)
      doesNotContainEntries(*entriesInB)
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/1070",
  )
  @Test
  fun canRegisterCustomShadowJarTask() {
    writeMainClass(sourceSet = "test", withImports = true)
    val testShadowJarTask = "testShadowJar"
    projectScriptPath.appendText(
      """
        dependencies {
          testImplementation 'junit:junit:3.8.2'
        }
        def $testShadowJarTask = tasks.register('$testShadowJarTask', ${ShadowJar::class.java.name}) {
          group = com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.GROUP_NAME
          description = "Create a combined JAR of project and test dependencies"
          archiveClassifier = "tests"
          from sourceSets.test.output
          configurations = [project.configurations.testRuntimeClasspath]
          manifest {
            attributes '$mainClassAttributeKey': 'shadow.Main'
          }
        }
      """.trimIndent(),
    )

    val result = run(testShadowJarTask)

    assertThat(result).taskOutcomeEquals(":$testShadowJarTask", SUCCESS)
    assertThat(jarPath("build/libs/shadow-1.0-tests.jar")).useAll {
      containsEntries(
        "junit/framework/Test.class",
        "shadow/Main.class",
      )
      getMainAttr(mainClassAttributeKey).isNotNull()
    }

    val pathString = path("build/libs/shadow-1.0-tests.jar").toString()
    val runningOutput = runProcess("java", "-jar", pathString, "foo")
    assertThat(runningOutput).contains(
      "Hello, World! (foo) from Main",
      "Refs: junit.framework.Test",
    )
  }

  @Test
  fun configurationCachingOfConfigurationsIsUpToDate() {
    settingsScriptPath.appendText(
      """
        include 'lib'
      """.trimIndent(),
    )
    projectScriptPath.writeText("")

    path("lib/src/main/java/lib/Lib.java").writeText(
      """
        package lib;
        public class Lib {}
      """.trimIndent(),
    )
    path("lib/build.gradle").writeText(
      """
        ${getDefaultProjectBuildScript()}
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJar {
          configurations = [project.configurations.compileClasspath]
        }
      """.trimIndent(),
    )

    val libShadowJarTask = ":lib:$SHADOW_JAR_TASK_NAME"
    run(libShadowJarTask)
    val result = run(libShadowJarTask)

    assertThat(result).taskOutcomeEquals(libShadowJarTask, UP_TO_DATE)
    assertThat(result.output).contains("Reusing configuration cache.")
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/915",
  )
  @Test
  fun failBuildIfProcessingBadJar() {
    val badJarPath = path("bad.jar").apply {
      writeText("A bad jar.")
    }.toUri().toURL().path

    projectScriptPath.appendText(
      """
        dependencies {
          implementation files('$badJarPath')
        }
      """.trimIndent(),
    )

    val result = runWithFailure(shadowJarTask)

    assertThat(result).all {
      taskOutcomeEquals(shadowJarTask, FAILED)
      transform { it.output }.contains(
        "java.util.zip.ZipException: archive is not a ZIP archive",
      )
    }
  }

  @Test
  fun worksWithArchiveFileName() {
    writeMainClass()
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
      containsEntries(
        "shadow/Main.class",
        "junit/framework/Test.class",
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
      transform { it.manifest.mainAttributes }.isNotEmpty()
      getMainAttr("Foo-Attr").isEqualTo("Foo-Value")
      getMainAttr("Bar-Attr").isEqualTo("Bar-Value")
    }
  }
}
