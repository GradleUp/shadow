package com.github.jengelman.gradle.plugins.shadow

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsMatch
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.single
import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin.Companion.ENABLE_DEVELOCITY_INTEGRATION_PROPERTY
import com.github.jengelman.gradle.plugins.shadow.internal.classPathAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.multiReleaseAttributeKey
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.testkit.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.testkit.containsNone
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.getMainAttr
import com.github.jengelman.gradle.plugins.shadow.testkit.getStream
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.prependText
import com.github.jengelman.gradle.plugins.shadow.util.runProcess
import kotlin.io.path.appendText
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod
import org.gradle.api.plugins.JavaPlugin.API_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.language.base.plugins.LifecycleBasePlugin.ASSEMBLE_TASK_NAME
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class JavaPluginsTest : BasePluginTest() {
  @Issue("https://github.com/GradleUp/shadow/pull/1766")
  @Test
  fun makeAssembleDependOnShadowJarEvenIfAddedLater() {
    val kFunction =
      ShadowJar.Companion::class.declaredFunctions.single { it.name == "registerShadowJarCommon" }
    val jvmName = checkNotNull(kFunction.javaMethod).name

    projectScript.writeText(
      """
        plugins {
          id '$shadowPluginId'
        }

        def testJar = tasks.register('testJar', Jar)
        // Must use `@Companion` to access the companion object instance instead of the class.
        def companion = ${ShadowJar::class.qualifiedName}.@Companion
        companion.$jvmName(project, testJar) {
          it.archiveFile.set(project.layout.buildDirectory.file('libs/test-all.jar'))
        }

        afterEvaluate {
          tasks.register('$ASSEMBLE_TASK_NAME') {
          def taskDependencies = provider { dependsOn.collect { it.name }.join(', ') }
            doFirst {
              logger.lifecycle('task dependencies: ' + taskDependencies.get())
            }
          }
        }
      """
        .trimIndent()
    )

    val result = runWithSuccess(ASSEMBLE_TASK_NAME)

    assertThat(result.task(":$ASSEMBLE_TASK_NAME"))
      .isNotNull()
      .transform { it.outcome }
      .isEqualTo(SUCCESS)
    assertThat(result.task(shadowJarPath)).isNotNull().transform { it.outcome }.isEqualTo(SUCCESS)
    assertThat(result.output).contains("task dependencies: $SHADOW_JAR_TASK_NAME")
  }

  @Test
  fun shadowJarCliOptions() {
    val result = runWithSuccess("help", "--task", shadowJarPath)

    assertThat(result.output)
      .contains(
        "--add-multi-release-attribute     Adds the multi-release attribute to the manifest if any dependencies contain it.",
        "--no-add-multi-release-attribute     Disables option --add-multi-release-attribute.",
        "--enable-auto-relocation     Enables auto relocation of packages in the dependencies.",
        "--no-enable-auto-relocation     Disables option --enable-auto-relocation.",
        "--enable-kotlin-module-remapping     Enables remapping of Kotlin module metadata files.",
        "--no-enable-kotlin-module-remapping     Disables option --enable-kotlin-module-remapping.",
        "--fail-on-duplicate-entries     Fails build if the ZIP entries in the shadowed JAR are duplicate.",
        "--no-fail-on-duplicate-entries     Disables option --fail-on-duplicate-entries",
        "--main-class     Main class attribute to add to manifest.",
        "--minimize-jar     Minimizes the jar by removing unused classes.",
        "--no-minimize-jar     Disables option --minimize-jar.",
        "--relocation-prefix     Prefix used for auto relocation of packages in the dependencies.",
      )
  }

  @Test
  fun includeProjectDependencies() {
    writeClientAndServerModules()

    runWithSuccess(serverShadowJarPath)

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

    runWithSuccess(":server:jar")

    assertThat(jarPath("server/build/libs/server-1.0.jar")).useAll {
      containsOnly("server/", "server/Server.class", *manifestEntries)
    }
    assertThat(jarPath("client/build/libs/client-1.0-all.jar")).useAll {
      containsAtLeast("client/", "client/Client.class", "client/junit/framework/Test.class")
      containsNone("server/Server.class")
    }
  }

  @Test
  fun shadowProjectShadowJar() {
    writeClientAndServerModules(clientShadowed = true)
    val relocatedEntries =
      junitEntries.map { it.replace("junit/framework/", "client/junit/framework/") }.toTypedArray()

    runWithSuccess(serverShadowJarPath)

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
      containsAtLeast("client/Client.class", "client/junit/framework/Test.class")
      containsNone("server/Server.class")
    }
  }

  @Issue("https://github.com/GradleUp/shadow/issues/1606")
  @Test
  fun shadowExposedCustomSourceSetOutput() {
    writeClientAndServerModules()
    path("client/build.gradle")
      .appendText(
        """
        sourceSets {
          custom
        }
        dependencies {
          implementation sourceSets.custom.output
        }
        """
          .trimIndent()
      )
    path("client/src/custom/java/client/Custom1.java")
      .writeText(
        """
        package client;
        public class Custom1 {}
        """
          .trimIndent()
      )
    path("client/src/custom/java/client/Custom2.java")
      .writeText(
        """
        package client;
        public class Custom2 {}
        """
          .trimIndent()
      )
    path("client/src/custom/resources/Foo.bar").writeText("Foo=Bar")

    runWithSuccess(serverShadowJarPath)

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

  @Issue("https://github.com/GradleUp/shadow/issues/449")
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun containsMultiReleaseAttrIfAnyDependencyContainsIt(addAttribute: Boolean) {
    writeClientAndServerModules()
    path("client/build.gradle")
      .appendText(
        """
        $jarTask {
          manifest {
            attributes '$multiReleaseAttributeKey': 'true'
          }
        }
      """
          .trimIndent() + lineSeparator
      )
    path("server/build.gradle")
      .appendText(
        """
        $shadowJarTask {
          addMultiReleaseAttribute = $addAttribute
        }
      """
          .trimIndent()
      )

    val result = runWithSuccess(serverShadowJarPath, infoArgument)

    assertThat(result.output)
      .contains(
        if (addAttribute) {
          "Adding Multi-Release attribute to the manifest if any dependencies contain it."
        } else {
          "Skipping adding Multi-Release attribute to the manifest as it is disabled."
        }
      )
    assertThat(outputServerShadowedJar.use { it.getMainAttr(multiReleaseAttributeKey) })
      .isEqualTo(if (addAttribute) "true" else null)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun containsMultiReleaseAttrByCliOption(enable: Boolean) {
    writeClientAndServerModules()
    path("client/build.gradle")
      .appendText(
        """
        $jarTask {
          manifest {
            attributes '$multiReleaseAttributeKey': 'true'
          }
        }
      """
          .trimIndent() + lineSeparator
      )

    val arg = if (enable) "--add-multi-release-attribute" else "--no-add-multi-release-attribute"
    val result = runWithSuccess(serverShadowJarPath, infoArgument, arg)

    assertThat(result.output)
      .contains(
        if (enable) {
          "Adding Multi-Release attribute to the manifest if any dependencies contain it."
        } else {
          "Skipping adding Multi-Release attribute to the manifest as it is disabled."
        }
      )
    assertThat(outputServerShadowedJar.use { it.getMainAttr(multiReleaseAttributeKey) })
      .isEqualTo(if (enable) "true" else null)
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/352",
    "https://github.com/GradleUp/shadow/issues/729",
  )
  @Test
  fun excludeSomeResourcesByDefault() {
    val resJar =
      buildJar("meta-inf.jar") {
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
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsOnly("META-INF/a.properties", *manifestEntries) }
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
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsOnly(*entriesInA, *manifestEntries) }
  }

  @Test
  fun includeJavaLibraryConfigurationsByDefault() {
    localRepo
      .apply {
        jarModule("my", "api", "1.0") { buildJar { insert("api.properties", "api") } }
        jarModule("my", "implementation", "1.0") {
          buildJar { insert("implementation.properties", "implementation") }
          addDependency("my:b:1.0")
        }
        jarModule("my", "runtime-only", "1.0") {
          buildJar { insert("runtime-only.properties", "runtime-only") }
        }
      }
      .publish()

    projectScript.writeText(
      """
        ${getDefaultProjectBuildScript("java-library")}
        dependencies {
          api 'my:api:1.0'
          implementation 'my:implementation:1.0'
          runtimeOnly 'my:runtime-only:1.0'
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

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
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      transform { it.mainAttrSize }.isEqualTo(1)
      getMainAttr(classPathAttributeKey).isNull()
    }
  }

  @Issue("https://github.com/GradleUp/shadow/issues/65")
  @ParameterizedTest
  @ValueSource(strings = [ShadowBasePlugin.CONFIGURATION_NAME, IMPLEMENTATION_CONFIGURATION_NAME])
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
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    val actual = outputShadowedJar.use { it.getMainAttr(classPathAttributeKey) }
    val expected =
      when (configuration) {
        ShadowBasePlugin.CONFIGURATION_NAME -> "/libs/foo.jar junit-3.8.2.jar"
        else -> "/libs/foo.jar"
      }
    assertThat(actual).isEqualTo(expected)
  }

  @Issue("https://github.com/GradleUp/shadow/issues/92")
  @Test
  fun doNotIncludeNullValueInClassPathWhenJarFileDoesNotContainClassPath() {
    projectScript.appendText(
      """
      dependencies {
        shadow 'junit:junit:3.8.2'
      }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    val value = outputShadowedJar.use { it.getMainAttr(classPathAttributeKey) }
    assertThat(value).isEqualTo("junit-3.8.2.jar")
  }

  @Issue("https://github.com/GradleUp/shadow/issues/203")
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
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsOnly(*junitEntries, *manifestEntries) }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/459",
    "https://github.com/GradleUp/shadow/issues/852",
  )
  @Test
  fun excludeGradleApiByDefault() {
    writeGradlePluginModule()
    projectScript.appendText(
      """
      dependencies {
        implementation 'my:a:1.0'
        compileOnly 'my:b:1.0'
      }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      transform { actual ->
          actual.entries().toList().map { it.name }.filter { it.endsWith(".class") }
        }
        .single()
        .isEqualTo("my/plugin/MyPlugin.class")
      transform { it.mainAttrSize }.isEqualTo(1)
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

  @Issue("https://github.com/GradleUp/shadow/issues/1422")
  @Test
  fun moveLocalGradleApiToCompileOnly() {
    projectScript.writeText(getDefaultProjectBuildScript("java-gradle-plugin"))

    val outputCompileOnly = dependencies(COMPILE_ONLY_CONFIGURATION_NAME)
    val outputApi = dependencies(API_CONFIGURATION_NAME)

    // "unspecified" is the local Gradle API.
    assertThat(outputCompileOnly).contains("unspecified")
    assertThat(outputApi).doesNotContain("unspecified")
  }

  @Issue("https://github.com/GradleUp/shadow/issues/1422")
  @ParameterizedTest
  @ValueSource(strings = [COMPILE_ONLY_CONFIGURATION_NAME, API_CONFIGURATION_NAME])
  fun doNotReAddSuppressedGradleApi(configuration: String) {
    projectScript.writeText(getDefaultProjectBuildScript("java-gradle-plugin"))

    val output =
      dependencies(
        configuration = configuration,
        // Internal flag added in 8.14 to experiment with suppressing local Gradle API.
        "-Dorg.gradle.unsafe.suppress-gradle-api=true",
      )

    // "unspecified" is the local Gradle API.
    assertThat(output).doesNotContain("unspecified")
  }

  @Issue("https://github.com/GradleUp/shadow/issues/1070")
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
      """
        .trimIndent()
    )

    runWithSuccess(testShadowJarTask)

    assertThat(jarPath("build/libs/my-1.0-test.jar")).useAll {
      containsOnly("my/", mainClassEntry, *junitEntries, *manifestEntries)
      getMainAttr(mainClassAttributeKey).isNotNull()
    }

    val pathString = path("build/libs/my-1.0-test.jar").toString()
    val runningOutput = runProcess("java", "-jar", pathString, "foo")
    assertThat(runningOutput)
      .contains("Hello, World! (foo) from Main", "Refs: junit.framework.Test")
  }

  @Issue("https://github.com/GradleUp/shadow/issues/1784")
  @Test
  fun registerShadowJarTaskWithoutShadowPluginApplied() {
    val mainClassEntry = writeClass(sourceSet = "test", withImports = true)
    val testShadowJarTask = "testShadowJar"
    projectScript.writeText(
      """
        ${getDefaultProjectBuildScript(applyShadowPlugin = false)}
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
        afterEvaluate {
          def hasShadowPlugin = plugins.hasPlugin('${ShadowPlugin::class.qualifiedName}')
          def hasShadowBasePlugin = plugins.hasPlugin('${ShadowBasePlugin::class.qualifiedName}')
          logger.lifecycle("Has ShadowPlugin: " + hasShadowPlugin)
          logger.lifecycle("Has ShadowBasePlugin: " + hasShadowBasePlugin)
        }
      """
        .trimIndent()
    )

    val result = runWithSuccess(testShadowJarTask)

    assertThat(result.output).contains("Has ShadowPlugin: false", "Has ShadowBasePlugin: false")

    assertThat(jarPath("build/libs/my-1.0-test.jar")).useAll {
      containsOnly("my/", mainClassEntry, *junitEntries, *manifestEntries)
      getMainAttr(mainClassAttributeKey).isNotNull()
    }

    val pathString = path("build/libs/my-1.0-test.jar").toString()
    val runningOutput = runProcess("java", "-jar", pathString, "foo")
    assertThat(runningOutput)
      .contains("Hello, World! (foo) from Main", "Refs: junit.framework.Test")
  }

  @Issue("https://github.com/GradleUp/shadow/issues/443")
  @Test
  fun registerCustomShadowJarThatContainsDependenciesOnly() {
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
      """
        .trimIndent()
    )

    runWithSuccess("jar", dependencyShadowJar)

    assertThat(jarPath("build/libs/my-1.0.jar")).useAll {
      containsOnly("my/", mainClassEntry, *manifestEntries)
      transform { it.mainAttrSize }.isEqualTo(1)
    }
    assertThat(jarPath("build/libs/my-1.0-dep.jar")).useAll {
      containsOnly(*junitEntries, *manifestEntries)
      transform { it.mainAttrSize }.isEqualTo(1)
    }
  }

  @Issue("https://github.com/GradleUp/shadow/issues/915")
  @Test
  fun failBuildIfProcessingBadJar() {
    val badJarPath = path("bad.jar").apply { writeText("A bad jar.") }

    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(badJarPath)}
        }
      """
        .trimIndent()
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
      """
        .trimIndent()
    )

    val result = runWithFailure(shadowJarPath)

    assertThat(result.output)
      .contains("Shadowing AAR file is not supported.", "Please exclude dependency artifact:")
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
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

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
    assertThat(jarPath(unzipped.name)).useAll { containsOnly(*entriesInA) }
  }

  @Test
  fun addDependenciesViaCustomConfigurationWithoutUnzipping() {
    projectScript.appendText(
      """
        def nonJar = configurations.create('nonJar')
        dependencies {
          add('nonJar', 'my:a:1.0')
          add('nonJar', 'my:b:1.0')
        }
        $shadowJarTask {
          from(nonJar)
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("a-1.0.jar", "b-1.0.jar", *manifestEntries)
    }
  }

  @Issue("https://github.com/GradleUp/shadow/issues/520")
  @Test
  fun onlyKeepFilesFromProjectWhenDuplicatesStrategyIsExclude() {
    val fooJar = buildJar("foo.jar") { insert("module-info.class", "module myModuleName {}") }
    val mainClassEntry = writeClass()
    writeClass(className = "module-info") { "module myModuleName {}" }
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
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("module-info.class", "my/", mainClassEntry, *manifestEntries)
      getContent("module-info.class").all {
        isNotEmpty()
        // It's the compiled class instead of the original content.
        isNotEqualTo("module myModuleName {}")
      }
    }
  }

  @Issue("https://github.com/GradleUp/shadow/issues/1441")
  @Test
  fun includeFilesInTaskOutputDirectory() {
    // Create a build that has a task with jars in the output directory
    projectScript.appendText(
      $$"""
      def createJars = tasks.register('createJars') {
        def artifactAJar = file('$${artifactAJar.invariantSeparatorsPathString}')
        def artifactBJar = file('$${artifactBJar.invariantSeparatorsPathString}')
        inputs.files(artifactAJar, artifactBJar)
        def outputDir = file('${buildDir}/jars')
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
      $$shadowJarTask {
        includedDependencies.from(files(createJars).asFileTree)
      }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsOnly(*entriesInAB, *manifestEntries) }
  }

  @Test
  fun integrateWithDevelocityBuildScan() {
    writeClientAndServerModules()
    settingsScript.prependText(
      """
      plugins {
        id 'com.gradle.develocity'
      }
      """
        .trimIndent() + lineSeparator
    )

    val result =
      runWithSuccess(
        serverShadowJarPath,
        ipArgument,
        infoArgument,
        "-P${ENABLE_DEVELOCITY_INTEGRATION_PROPERTY}=true",
        "-Dscan.dump", // Using scan.dump avoids actually publishing a Build Scan, writing it to a
        // file instead.
      )

    assertThat(result.output).all {
      contains("Enabling Develocity integration for Shadow plugin.", "Build scan written")
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
      """
        .trimIndent()
    )

    val result =
      if (enable) {
        runWithFailure(shadowJarPath)
      } else {
        runWithSuccess(shadowJarPath, infoArgument)
      }

    assertThat(result.output)
      .contains("Duplicate entries found in the shadowed JAR:", "a.properties (2 times)")
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
      """
        .trimIndent()
    )

    val result =
      if (enable) {
        runWithFailure(shadowJarPath, "--fail-on-duplicate-entries")
      } else {
        runWithSuccess(shadowJarPath, infoArgument, "--no-fail-on-duplicate-entries")
      }

    assertThat(result.output)
      .contains("Duplicate entries found in the shadowed JAR:", "a.properties (2 times)")
  }

  @ParameterizedTest
  @MethodSource("fallbackMainClassProvider")
  fun fallbackMainClassByProperty(input: String, expected: String?, message: String) {
    projectScript.appendText(
      """
        $shadowJarTask {
          mainClass = '$input'
        }
      """
        .trimIndent()
    )

    val result = runWithSuccess(shadowJarPath, infoArgument)

    assertThat(result.output).contains(message)
    assertThat(outputShadowedJar).useAll { getMainAttr(mainClassAttributeKey).isEqualTo(expected) }
  }

  @ParameterizedTest
  @MethodSource("fallbackMainClassProvider")
  fun fallbackMainClassByCliOption(input: String, expected: String?) {
    if (input.isEmpty()) {
      runWithSuccess(shadowJarPath)
    } else {
      runWithSuccess(shadowJarPath, "--main-class", input)
    }

    assertThat(outputShadowedJar).useAll { getMainAttr(mainClassAttributeKey).isEqualTo(expected) }
  }

  private fun dependencies(configuration: String, vararg flags: String): String {
    return runWithSuccess("dependencies", "--configuration", configuration, *flags).output
  }

  private companion object {
    @JvmStatic
    fun fallbackMainClassProvider() =
      listOf(
        Arguments.of(
          "my.Main",
          "my.Main",
          "Adding $mainClassAttributeKey attribute to the manifest with value",
        ),
        Arguments.of(
          "",
          null,
          "Skipping adding $mainClassAttributeKey attribute to the manifest as it is empty.",
        ),
      )
  }
}
