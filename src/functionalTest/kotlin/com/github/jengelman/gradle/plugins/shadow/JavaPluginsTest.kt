package com.github.jengelman.gradle.plugins.shadow

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsMatch
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.single
import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin.Companion.ENABLE_DEVELOCITY_INTEGRATION_PROPERTY
import com.github.jengelman.gradle.plugins.shadow.internal.classPathAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.multiReleaseAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.runtimeConfiguration
import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.util.containsNone
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import com.github.jengelman.gradle.plugins.shadow.util.getStream
import com.github.jengelman.gradle.plugins.shadow.util.runProcess
import kotlin.io.path.appendText
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.API_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class JavaPluginsTest : BasePluginTest() {
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

    with(project.extensions.getByType(ShadowExtension::class.java)) {
      assertThat(addOptionalJavaVariant.get()).isTrue()
    }

    project.plugins.apply(JavaPlugin::class.java)
    val shadowTask = project.tasks.getByName(SHADOW_JAR_TASK_NAME) as ShadowJar
    val shadowConfig = project.configurations.getByName(ShadowBasePlugin.CONFIGURATION_NAME)
    val assembleTask = project.tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)
    assertThat(assembleTask.dependsOn).contains(shadowTask)

    // Check extended properties.
    with(shadowTask as Jar) {
      assertThat(duplicatesStrategy).isEqualTo(DuplicatesStrategy.EXCLUDE)
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
      assertThat(group).isEqualTo(LifecycleBasePlugin.BUILD_GROUP)
      assertThat(description).isEqualTo("Create a combined JAR of project and runtime dependencies")
      assertThat(minimizeJar.get()).isFalse()
      assertThat(enableAutoRelocation.get()).isFalse()
      assertThat(relocationPrefix.get()).isEqualTo(ShadowBasePlugin.SHADOW)
      assertThat(configurations.get()).all {
        isNotEmpty()
        containsOnly(project.runtimeConfiguration)
      }
      assertThat(failOnDuplicateEntries.get()).isFalse()
    }

    assertThat(shadowConfig.artifacts.files).contains(shadowTask.archiveFile.get().asFile)
  }

  @Test
  fun shadowJarCliOptions() {
    val result = run("help", "--task", shadowJarPath)

    assertThat(result.output).contains(
      "--enable-auto-relocation     Enables auto relocation of packages in the dependencies.",
      "--no-enable-auto-relocation     Disables option --enable-auto-relocation.",
      "--fail-on-duplicate-entries     Fails build if the ZIP entries in the shadowed JAR are duplicate.",
      "--no-fail-on-duplicate-entries     Disables option --fail-on-duplicate-entries",
      "--minimize-jar     Minimizes the jar by removing unused classes.",
      "--no-minimize-jar     Disables option --minimize-jar.",
      "--relocation-prefix     Prefix used for auto relocation of packages in the dependencies.",
    )
  }

  @Test
  fun includeProjectDependencies() {
    writeClientAndServerModules()

    run(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
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

    run(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
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
    "https://github.com/GradleUp/shadow/issues/1606",
  )
  @Test
  fun shadowExposedCustomSourceSetOutput() {
    writeClientAndServerModules()
    path("client/build.gradle").appendText(
      """
        sourceSets {
          custom
        }
        dependencies {
          implementation sourceSets.custom.output
        }
      """.trimIndent(),
    )
    path("client/src/custom/java/client/Custom1.java").writeText(
      """
        package client;
        public class Custom1 {}
      """.trimIndent(),
    )
    path("client/src/custom/java/client/Custom2.java").writeText(
      """
        package client;
        public class Custom2 {}
      """.trimIndent(),
    )
    path("client/src/custom/resources/Foo.bar").writeText("Foo=Bar")

    run(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsOnly(
        "Foo.bar",
        "client/",
        "server/",
        "client/Client.class",
        "client/Custom1.class",
        "client/Custom2.class",
        "server/Server.class",
        *junitEntries,
        *manifestEntries,
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
        $jarTask {
          manifest {
            attributes '$multiReleaseAttributeKey': 'true'
          }
        }
      """.trimIndent() + lineSeparator,
    )

    run(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      transform { it.mainAttrSize }.isGreaterThan(1)
      getMainAttr(multiReleaseAttributeKey).isEqualTo("true")
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/352",
    "https://github.com/GradleUp/shadow/issues/729",
  )
  @Test
  fun excludeSomeResourcesByDefault() {
    val resJar = buildJar("meta-inf.jar") {
      insert("META-INF/INDEX.LIST", "JarIndex-Version: 1.0")
      insert("META-INF/a.SF", "Signature File")
      insert("META-INF/a.DSA", "DSA Signature Block")
      insert("META-INF/a.RSA", "RSA Signature Block")
      insert("META-INF/a.properties", "key=value")
      insert("META-INF/versions/9/module-info.class", "module myModuleName {}")
      insert("META-INF/versions/16/module-info.class", "module myModuleName {}")
      insert("module-info.class", "module myModuleName {}")
    }

    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(resJar)}
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "META-INF/a.properties",
        *manifestEntries,
      )
    }
  }

  @Test
  fun includeRuntimeConfigurationByDefault() {
    projectScript.appendText(
      """
        dependencies {
          runtimeOnly 'my:a:1.0'
          shadow 'my:b:1.0'
          compileOnly 'my:b:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        *entriesInA,
        *manifestEntries,
      )
    }
  }

  @Test
  fun includeJavaLibraryConfigurationsByDefault() {
    localRepo.apply {
      jarModule("my", "api", "1.0") {
        buildJar {
          insert("api.properties", "api")
        }
      }
      jarModule("my", "implementation", "1.0") {
        buildJar {
          insert("implementation.properties", "implementation")
        }
        addDependency("my:b:1.0")
      }
      jarModule("my", "runtime-only", "1.0") {
        buildJar {
          insert("runtime-only.properties", "runtime-only")
        }
      }
    }.publish()

    projectScript.writeText(
      """
        ${getDefaultProjectBuildScript("java-library", withGroup = true, withVersion = true)}
        dependencies {
          api 'my:api:1.0'
          implementation 'my:implementation:1.0'
          runtimeOnly 'my:runtime-only:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "api.properties",
        "implementation.properties",
        "runtime-only.properties",
        *entriesInB,
        *manifestEntries,
      )
    }
  }

  @Test
  fun classPathInManifestNotAddedIfEmpty() {
    projectScript.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      transform { it.mainAttrSize }.isEqualTo(1)
      getMainAttr(classPathAttributeKey).isNull()
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/65",
  )
  @ParameterizedTest
  @ValueSource(strings = [ShadowBasePlugin.CONFIGURATION_NAME, JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME])
  fun addShadowConfigurationToClassPathInManifest(configuration: String) {
    projectScript.appendText(
      """
        dependencies {
          $configuration 'junit:junit:3.8.2'
        }
        $jarTask {
          manifest {
            attributes '$classPathAttributeKey': '/libs/foo.jar'
          }
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    val actual = outputShadowedJar.use { it.getMainAttr(classPathAttributeKey) }
    val expected = when (configuration) {
      ShadowBasePlugin.CONFIGURATION_NAME -> "/libs/foo.jar junit-3.8.2.jar"
      else -> "/libs/foo.jar"
    }
    assertThat(actual).isEqualTo(expected)
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/92",
  )
  @Test
  fun doNotIncludeNullValueInClassPathWhenJarFileDoesNotContainClassPath() {
    projectScript.appendText(
      """
        dependencies {
          shadow 'junit:junit:3.8.2'
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    val value = outputShadowedJar.use { it.getMainAttr(classPathAttributeKey) }
    assertThat(value).isEqualTo("junit-3.8.2.jar")
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/203",
  )
  @ParameterizedTest
  @EnumSource(ZipEntryCompression::class)
  fun supportZipCompressions(method: ZipEntryCompression) {
    projectScript.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJarTask {
          zip64 = true
          entryCompression = ${ZipEntryCompression::class.java.canonicalName}.$method
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        *junitEntries,
        *manifestEntries,
      )
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
    projectScript.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
          compileOnly 'my:b:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
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
    "https://github.com/GradleUp/shadow/issues/1422",
  )
  @Test
  fun movesLocalGradleApiToCompileOnly() {
    projectScript.writeText(
      """
        ${getDefaultProjectBuildScript("java-gradle-plugin")}
      """.trimIndent() + lineSeparator,
    )

    val outputCompileOnly = dependencies(COMPILE_ONLY_CONFIGURATION_NAME)
    val outputApi = dependencies(API_CONFIGURATION_NAME)

    // "unspecified" is the local Gradle API.
    assertThat(outputCompileOnly).contains("unspecified")
    assertThat(outputApi).doesNotContain("unspecified")
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/1422",
  )
  @ParameterizedTest
  @ValueSource(strings = [COMPILE_ONLY_CONFIGURATION_NAME, API_CONFIGURATION_NAME])
  fun doNotReAddSuppressedGradleApi(configuration: String) {
    projectScript.writeText(
      """
        ${getDefaultProjectBuildScript("java-gradle-plugin")}
      """.trimIndent() + lineSeparator,
    )

    val output = dependencies(
      configuration = configuration,
      // Internal flag added in 8.14 to experiment with suppressing local Gradle API.
      "-Dorg.gradle.unsafe.suppress-gradle-api=true",
    )

    // "unspecified" is the local Gradle API.
    assertThat(output).doesNotContain("unspecified")
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/1070",
  )
  @Test
  fun registerCustomShadowJarTask() {
    val mainClassEntry = writeClass(sourceSet = "test", withImports = true)
    val testShadowJarTask = "testShadowJar"
    projectScript.appendText(
      """
        dependencies {
          testImplementation 'junit:junit:3.8.2'
        }
        def $testShadowJarTask = tasks.register('$testShadowJarTask', ${ShadowJar::class.java.name}) {
          description = 'Create a combined JAR of project and test dependencies'
          archiveClassifier = 'test'
          from sourceSets.named('test').map { it.output }
          configurations = project.configurations.named('testRuntimeClasspath').map { [it] }
          manifest {
            attributes '$mainClassAttributeKey': 'my.Main'
          }
        }
      """.trimIndent(),
    )

    run(testShadowJarTask)

    assertThat(jarPath("build/libs/my-1.0-test.jar")).useAll {
      containsOnly(
        "my/",
        mainClassEntry,
        *junitEntries,
        *manifestEntries,
      )
      getMainAttr(mainClassAttributeKey).isNotNull()
    }

    val pathString = path("build/libs/my-1.0-test.jar").toString()
    val runningOutput = runProcess("java", "-jar", pathString, "foo")
    assertThat(runningOutput).contains(
      "Hello, World! (foo) from Main",
      "Refs: junit.framework.Test",
    )
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/443",
  )
  @Test
  fun registerCustomShadowJarTaskThatContainsDependenciesOnly() {
    val mainClassEntry = writeClass()
    val dependencyShadowJar = "dependencyShadowJar"

    projectScript.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        def $dependencyShadowJar = tasks.register('$dependencyShadowJar', ${ShadowJar::class.java.name}) {
          description = 'Create a shadow JAR of all dependencies'
          archiveClassifier = 'dep'
          configurations = project.configurations.named('runtimeClasspath').map { [it] }
        }
      """.trimIndent(),
    )

    run("jar", dependencyShadowJar)

    assertThat(jarPath("build/libs/my-1.0.jar")).useAll {
      containsOnly(
        "my/",
        mainClassEntry,
        *manifestEntries,
      )
      transform { it.mainAttrSize }.isEqualTo(1)
    }
    assertThat(jarPath("build/libs/my-1.0-dep.jar")).useAll {
      containsOnly(
        *junitEntries,
        *manifestEntries,
      )
      transform { it.mainAttrSize }.isEqualTo(1)
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/915",
  )
  @Test
  fun failBuildIfProcessingBadJar() {
    val badJarPath = path("bad.jar").apply {
      writeText("A bad jar.")
    }

    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(badJarPath)}
        }
      """.trimIndent(),
    )

    val result = runWithFailure(shadowJarPath)

    assertThat(result.output).containsMatch("Cannot expand ZIP '.*bad\\.jar'".toRegex())
  }

  @Test
  fun failBuildIfProcessingAar() {
    val fooAarPath = path("foo.aar")

    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(fooAarPath)}
        }
      """.trimIndent(),
    )

    val result = runWithFailure(shadowJarPath)

    assertThat(result.output).contains(
      "Shadowing AAR file is not supported.",
      "Please exclude dependency artifact:",
    )
  }

  @Test
  fun inheritFromOtherManifest() {
    projectScript.appendText(
      """
        $jarTask {
          manifest {
            attributes 'Foo-Attr': 'Foo-Value'
          }
        }
        def testJar = tasks.register('testJar', Jar) {
          manifest {
            attributes 'Bar-Attr': 'Bar-Value'
          }
        }
        $shadowJarTask {
          manifest.inheritFrom(testJar.get().manifest)
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      transform { it.mainAttrSize }.isGreaterThan(2)
      getMainAttr("Foo-Attr").isEqualTo("Foo-Value")
      getMainAttr("Bar-Attr").isEqualTo("Bar-Value")
    }
  }

  @Test
  fun addExtraFilesViaFrom() {
    val mainClassEntry = writeClass()
    path("Foo").writeText("Foo")
    projectScript.appendText(
      """
        $shadowJarTask {
          from(file('${artifactAJar.invariantSeparatorsPathString}')) { // Without unzipping.
            into('META-INF')
          }
          from(zipTree(file('${artifactBJar.invariantSeparatorsPathString}'))) { // With unzipping.
            into('META-INF')
          }
          from('Foo') {
            into('Bar')
          }
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "my/",
        "Bar/",
        "Bar/Foo",
        "META-INF/a-1.0.jar",
        "META-INF/b.properties",
        mainClassEntry,
        *manifestEntries,
      )
      getContent("Bar/Foo").isEqualTo("Foo")
      getContent("META-INF/b.properties").isEqualTo("b")
    }
    val unzipped = path("unzipped")
    outputShadowedJar.use {
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
    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(fooJar)}
        }
        $shadowJarTask {
          excludes.remove(
            'module-info.class'
          )
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
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
    projectScript.appendText(
      """
      def createJars = tasks.register('createJars') {
        def artifactAJar = file('${artifactAJar.invariantSeparatorsPathString}')
        def artifactBJar = file('${artifactBJar.invariantSeparatorsPathString}')
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
      $shadowJarTask {
        includedDependencies.from(files(createJars).asFileTree)
      }
      """.trimIndent(),
    )

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(*entriesInAB, *manifestEntries)
    }
  }

  @Test
  fun integrateWithDevelocityBuildScan() {
    writeClientAndServerModules()
    settingsScript.writeText(
      """
        plugins {
          id 'com.gradle.develocity'
        }
        ${settingsScript.readText()}
      """.trimIndent(),
    )

    val result = run(
      serverShadowJarPath,
      ipArgument,
      "-P${ENABLE_DEVELOCITY_INTEGRATION_PROPERTY}=true",
      "-Dscan.dump", // Using scan.dump avoids actually publishing a Build Scan, writing it to a file instead.
      infoArgument,
    )

    assertThat(result.output).all {
      contains(
        "Enabling Develocity integration for Shadow plugin.",
        "Build scan written",
      )
      doesNotContain("Configuration cache problems")
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun failBuildIfDuplicateEntries(enable: Boolean) {
    path("src/main/resources/a.properties").writeText("invalid a")
    projectScript.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
        }
        $shadowJarTask {
          duplicatesStrategy = DuplicatesStrategy.INCLUDE
          failOnDuplicateEntries = $enable
        }
      """.trimIndent(),
    )

    val result = if (enable) {
      runWithFailure(shadowJarPath)
    } else {
      run(shadowJarPath, infoArgument)
    }

    assertThat(result.output).contains(
      "Duplicate entries found in the shadowed JAR:",
      "a.properties (2 times)",
    )
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun failBuildIfDuplicateEntriesByCliOption(enable: Boolean) {
    path("src/main/resources/a.properties").writeText("project a")
    projectScript.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
        }
        $shadowJarTask {
          duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
      """.trimIndent(),
    )

    val result = if (enable) {
      runWithFailure(shadowJarPath, "--fail-on-duplicate-entries")
    } else {
      run(shadowJarPath, infoArgument)
    }

    assertThat(result.output).contains(
      "Duplicate entries found in the shadowed JAR:",
      "a.properties (2 times)",
    )
  }

  private fun dependencies(configuration: String, vararg flags: String): String {
    return run("dependencies", "--configuration", configuration, *flags).output
  }
}
