package com.github.jengelman.gradle.plugins.shadow

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAtLeast
import assertk.assertions.containsMatch
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.single
import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin.Companion.ENABLE_DEVELOCITY_INTEGRATION_PROPERTY
import com.github.jengelman.gradle.plugins.shadow.internal.classPathAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.multiReleaseAttributeKey
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.testkit.classLoader
import com.github.jengelman.gradle.plugins.shadow.testkit.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getBytes
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.getMainAttr
import com.github.jengelman.gradle.plugins.shadow.testkit.getStream
import com.github.jengelman.gradle.plugins.shadow.testkit.runMain
import com.github.jengelman.gradle.plugins.shadow.util.prependText
import kotlin.io.path.appendText
import kotlin.io.path.deleteExisting
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPlugin.API_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME
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
  @Test // #1766
  fun makeAssembleDependOnShadowJarEvenIfAddedLater() {
    val kFunction =
      ShadowJar.Companion::class.declaredFunctions.single { it.name == "registerShadowJarCommon" }
    val jvmName = checkNotNull(kFunction.javaMethod).name

    projectScript.writeText(
      """
      |plugins {
      |  id '$shadowPluginId'
      |}
      |
      |def testJar = tasks.register('testJar', Jar)
      |// Must use `@Companion` to access the companion object instance instead of the class.
      |def companion = ${ShadowJar::class.qualifiedName}.@Companion
      |companion.$jvmName(project, testJar) {
      |  it.archiveFile.set(project.layout.buildDirectory.file('libs/test-all.jar'))
      |}
      |
      |afterEvaluate {
      |  tasks.register('$ASSEMBLE_TASK_NAME') {
      |  def taskDependencies = provider { dependsOn.collect { it.name }.join(', ') }
      |    doFirst {
      |      logger.lifecycle('task dependencies: ' + taskDependencies.get())
      |    }
      |  }
      |}
      """
        .trimMargin()
    )

    val result = runWithSuccess(ASSEMBLE_TASK_NAME)

    assertThat(result).taskOutcomeEquals(":$ASSEMBLE_TASK_NAME", SUCCESS)
    assertThat(result).taskOutcomeEquals(shadowJarPath, SUCCESS)
    assertThat(result.output).contains("task dependencies: $SHADOW_JAR_TASK_NAME")
  }

  @Test // #1908
  fun shadowJarNotAddedToAssembleWhenDisabled() {
    projectScript.appendText(
      """
      |shadow {
      |  addShadowJarToAssembleLifecycle = false
      |}
      """
        .trimMargin()
    )

    val result = runWithSuccess(ASSEMBLE_TASK_NAME)

    assertThat(result).taskOutcomeEquals(":$ASSEMBLE_TASK_NAME", SUCCESS)
    assertThat(result.task(shadowJarPath)).isNull()
  }

  @Test
  fun shadowJarCliOptions() {
    val options =
      runWithSuccess("help", "--task", shadowJarPath)
        .output
        .substringAfter("Options")
        .substringBefore("Description")
        .lines()
        .filter(CharSequence::isNotBlank)
        .joinToString(separator = "\n")

    assertThat(options)
      .isEqualTo(
        // If the expected options are modified, also update docs/getting-started/README.md.
        """
        |     --add-multi-release-attribute     Adds the multi-release attribute to the manifest if any dependencies contain it.
        |     --no-add-multi-release-attribute     Disables option --add-multi-release-attribute.
        |     --enable-auto-relocation     Enables auto relocation of packages in the dependencies.
        |     --no-enable-auto-relocation     Disables option --enable-auto-relocation.
        |     --enable-kotlin-module-remapping     Enables remapping of Kotlin module metadata files.
        |     --no-enable-kotlin-module-remapping     Disables option --enable-kotlin-module-remapping.
        |     --fail-on-duplicate-entries     Fails build if the ZIP entries in the shadowed JAR are duplicate.
        |     --no-fail-on-duplicate-entries     Disables option --fail-on-duplicate-entries.
        |     --generate-sources-jar     Generates a companion shadowed sources JAR containing project and dependency sources.
        |     --no-generate-sources-jar     Disables option --generate-sources-jar.
        |     --main-class     Main class attribute to add to manifest.
        |     --minimize-jar     Minimizes the jar by removing unused classes.
        |     --no-minimize-jar     Disables option --minimize-jar.
        |     --relocation-prefix     Prefix used for auto relocation of packages in the dependencies.
        |     --rerun     Causes the task to be re-run even if up-to-date.
        """
          .trimMargin()
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
    assertThat(outputServerShadowedSourcesJar).useAll {
      containsOnly(
        "client/",
        "server/",
        "client/Client.java",
        "server/Server.java",
        *manifestEntries,
      )
    }
  }

  @Test
  fun dependOnProjectShadowJar() {
    writeClientAndServerModules(clientShadowed = true)
    val relocatedEntries =
      junitEntries.map { it.replace("junit/framework/", "client/junit/framework/") }.toTypedArray()

    runWithSuccess(":server:jar")

    assertThat(jarPath("server/build/libs/server-1.0.jar")).useAll {
      containsOnly("server/", "server/Server.class", *manifestEntries)
    }
    assertThat(jarPath("client/build/libs/client-1.0-all.jar")).useAll {
      containsOnly(
        "client/",
        "client/junit/",
        "client/Client.class",
        *relocatedEntries,
        *manifestEntries,
      )
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
      containsOnly(
        "client/",
        "client/junit/",
        "client/Client.class",
        *relocatedEntries,
        *manifestEntries,
      )
    }
  }

  @Test // #1893
  fun consumeShadowedProjectViaApiElementsAndRuntimeElements() {
    settingsScript.appendText(
      """
      |include 'client', 'server'
      """
        .trimMargin()
    )
    projectScript.deleteExisting()

    path("client/src/main/java/client/Client.java")
      .writeText(
        """
        |package client;
        |public class Client {}
        """
          .trimMargin()
      )
    path("client/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java-library")}
        |dependencies {
        |  api 'junit:junit:3.8.2'
        |}
        |$shadowJarTask {
        |  relocate 'junit.framework', 'client.junit.framework'
        |}
        |configurations {
        |  apiElements {
        |    outgoing.artifacts.clear()
        |    outgoing.variants.clear()
        |    outgoing.artifact($shadowJarTask)
        |  }
        |  runtimeElements {
        |    outgoing.artifacts.clear()
        |    outgoing.variants.clear()
        |    outgoing.artifact($shadowJarTask)
        |  }
        |}
        |
        """
          .trimMargin()
      )

    path("server/src/main/java/server/Server.java")
      .writeText(
        """
        |package server;
        |import client.Client;
        |import client.junit.framework.Test;
        |public class Server {}
        """
          .trimMargin()
      )
    path("server/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java", applyShadowPlugin = false)}
        |dependencies {
        |  // No `configuration: "shadow"` needed!
        |  implementation project(':client')
        |}
        |
        """
          .trimMargin()
      )

    // Running server:jar to ensure it compiles against the shadowed client
    runWithSuccess(":server:jar")

    // The fact that server compiled successfully against `client.junit.framework.Test`
    // means it consumed the shadowed artifact during compilation.
    assertThat(jarPath("server/build/libs/server-1.0.jar")).useAll {
      containsAtLeast("server/Server.class")
    }
  }

  @Test // #1893
  fun excludeRulesPreventBundledDepsOnConsumerClasspath() {
    settingsScript.appendText("include 'foo', 'consumer'\n")
    projectScript.deleteExisting()

    path("foo/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java-library")}
        |dependencies {
        |  implementation 'my:a:1.0'
        |}
        |configurations {
        |  named('apiElements') {
        |    outgoing.artifacts.clear()
        |    outgoing.variants.clear()
        |    outgoing.artifact(tasks.named('shadowJar'))
        |    exclude(group: 'my', module: 'a')
        |  }
        |  named('runtimeElements') {
        |    outgoing.artifacts.clear()
        |    outgoing.variants.clear()
        |    outgoing.artifact(tasks.named('shadowJar'))
        |    exclude(group: 'my', module: 'a')
        |  }
        |}
        |
        """
          .trimMargin()
      )

    path("consumer/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java", applyShadowPlugin = false)}
        |dependencies {
        |  implementation project(':foo')
        |}
        |tasks.register('printClasspathFiles') {
        |  def cp = configurations.runtimeClasspath
        |  doLast {
        |    cp.files.each { logger.lifecycle(it.name) }
        |  }
        |}
        |
        """
          .trimMargin()
      )

    val result = runWithSuccess(":consumer:printClasspathFiles")
    assertThat(result.output).all {
      contains("foo-1.0-all.jar")
      doesNotContain("a-1.0.jar")
    }
  }

  @Test // #1606
  fun shadowExposedCustomSourceSetOutput() {
    writeClientAndServerModules()
    path("client/build.gradle")
      .appendText(
        """
        |sourceSets {
        |  create('custom')
        |}
        |dependencies {
        |  implementation sourceSets.custom.output
        |}
        """
          .trimMargin()
      )
    path("client/src/custom/java/client/Custom1.java")
      .writeText(
        """
        |package client;
        |public class Custom1 {}
        """
          .trimMargin()
      )
    path("client/src/custom/java/client/Custom2.java")
      .writeText(
        """
        |package client;
        |public class Custom2 {}
        """
          .trimMargin()
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

  @ParameterizedTest // #449
  @ValueSource(booleans = [false, true])
  fun containsMultiReleaseAttrIfAnyDependencyContainsIt(addAttribute: Boolean) {
    writeClientAndServerModules()
    path("client/build.gradle")
      .appendText(
        """
        |$jarTask {
        |  manifest {
        |    attributes '$multiReleaseAttributeKey': 'true'
        |  }
        |}
        |
        """
          .trimMargin()
      )
    path("server/build.gradle")
      .appendText(
        """
        |$shadowJarTask {
        |  addMultiReleaseAttribute = $addAttribute
        |}
        """
          .trimMargin()
      )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar.use { it.getMainAttr(multiReleaseAttributeKey) })
      .isEqualTo(if (addAttribute) "true" else null)
  }

  @Test // #352, #729
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
      |dependencies {
      |  ${implementationFiles(resJar)}
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsOnly("META-INF/a.properties", *manifestEntries) }
  }

  @Test
  fun includeRuntimeConfigurationByDefault() {
    projectScript.appendText(
      """
      |dependencies {
      |  runtimeOnly 'my:a:1.0'
      |  shadow 'my:b:1.0'
      |  compileOnly 'my:b:1.0'
      |}
      """
        .trimMargin()
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
      |${getDefaultProjectBuildScript("java-library")}
      |dependencies {
      |  api 'my:api:1.0'
      |  implementation 'my:implementation:1.0'
      |  runtimeOnly 'my:runtime-only:1.0'
      |}
      """
        .trimMargin()
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
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      transform { it.mainAttrSize }.isEqualTo(1)
      getMainAttr(classPathAttributeKey).isNull()
    }
  }

  @ParameterizedTest // #65
  @ValueSource(strings = [ShadowBasePlugin.CONFIGURATION_NAME, IMPLEMENTATION_CONFIGURATION_NAME])
  fun addShadowConfigurationToClassPathInManifest(configuration: String) {
    projectScript.appendText(
      """
      |dependencies {
      |  $configuration 'junit:junit:3.8.2'
      |}
      |$jarTask {
      |  manifest {
      |    attributes '$classPathAttributeKey': '/libs/foo.jar'
      |  }
      |}
      """
        .trimMargin()
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

  @Test // #92
  fun doNotIncludeNullValueInClassPathWhenJarFileDoesNotContainClassPath() {
    projectScript.appendText(
      """
      |dependencies {
      |  shadow 'junit:junit:3.8.2'
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    val value = outputShadowedJar.use { it.getMainAttr(classPathAttributeKey) }
    assertThat(value).isEqualTo("junit-3.8.2.jar")
  }

  @ParameterizedTest // #203
  @EnumSource(ZipEntryCompression::class)
  fun supportZipCompressions(method: ZipEntryCompression) {
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |$shadowJarTask {
      |  zip64 = true
      |  entryCompression = ${ZipEntryCompression::class.java.canonicalName}.$method
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsOnly(*junitEntries, *manifestEntries) }
  }

  @Test // #459, #852
  fun excludeGradleApiByDefault() {
    writeGradlePluginModule()
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'my:a:1.0'
      |  compileOnly 'my:b:1.0'
      |}
      """
        .trimMargin()
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

  @Test // #1422
  fun moveLocalGradleApiToCompileOnly() {
    projectScript.writeText(getDefaultProjectBuildScript("java-gradle-plugin"))

    val outputCompileOnlyApi = dependencies(COMPILE_ONLY_API_CONFIGURATION_NAME)

    // "unspecified" is the local Gradle API.
    assertThat(outputCompileOnlyApi).contains("unspecified")
  }

  @ParameterizedTest // #1422
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

  @Test // #1070
  fun registerCustomShadowJarTask() {
    val mainClassEntry = writeClass(sourceSet = "test", withImports = true)
    val testShadowJarTask = "testShadowJar"
    projectScript.appendText(
      """
      |dependencies {
      |  testImplementation 'junit:junit:3.8.2'
      |}
      |def $testShadowJarTask = tasks.register('$testShadowJarTask', ${ShadowJar::class.java.name}) {
      |  description = 'Create a combined JAR of project and test dependencies'
      |  archiveClassifier = 'test'
      |  from sourceSets.named('test').map { it.output }
      |  configurations = project.configurations.named('testRuntimeClasspath').map { [it] }
      |  manifest {
      |    attributes '$mainClassAttributeKey': 'my.Main'
      |  }
      |}
      """
        .trimMargin()
    )

    runWithSuccess(testShadowJarTask)

    assertThat(jarPath("build/libs/my-1.0-test.jar")).useAll {
      containsOnly("my/", mainClassEntry, *junitEntries, *manifestEntries)
      getMainAttr(mainClassAttributeKey).isEqualTo("my.Main")
      classLoader {
        runMain("my.Main", "foo")
          .isEqualTo(
            """
            |Hello, World! (foo) from Main
            |Refs: junit.framework.Test
            |"""
              .trimMargin()
          )
      }
    }
  }

  @Test // #1784
  fun registerShadowJarTaskWithoutShadowPluginApplied() {
    val mainClassEntry = writeClass(sourceSet = "test", withImports = true)
    val testShadowJarTask = "testShadowJar"
    projectScript.writeText(
      """
      |${getDefaultProjectBuildScript(applyShadowPlugin = false)}
      |dependencies {
      |  testImplementation 'junit:junit:3.8.2'
      |}
      |def $testShadowJarTask = tasks.register('$testShadowJarTask', ${ShadowJar::class.java.name}) {
      |  description = 'Create a combined JAR of project and test dependencies'
      |  archiveClassifier = 'test'
      |  from sourceSets.named('test').map { it.output }
      |  configurations = project.configurations.named('testRuntimeClasspath').map { [it] }
      |  manifest {
      |    attributes '$mainClassAttributeKey': 'my.Main'
      |  }
      |}
      |afterEvaluate {
      |  def hasShadowPlugin = plugins.hasPlugin('${ShadowPlugin::class.qualifiedName}')
      |  def hasShadowBasePlugin = plugins.hasPlugin('${ShadowBasePlugin::class.qualifiedName}')
      |  logger.lifecycle("Has ShadowPlugin: " + hasShadowPlugin)
      |  logger.lifecycle("Has ShadowBasePlugin: " + hasShadowBasePlugin)
      |}
      """
        .trimMargin()
    )

    val result = runWithSuccess(testShadowJarTask)

    assertThat(result.output).contains("Has ShadowPlugin: false", "Has ShadowBasePlugin: false")

    assertThat(jarPath("build/libs/my-1.0-test.jar")).useAll {
      containsOnly("my/", mainClassEntry, *junitEntries, *manifestEntries)
      getMainAttr(mainClassAttributeKey).isEqualTo("my.Main")
      classLoader {
        runMain("my.Main", "foo")
          .isEqualTo(
            """
            |Hello, World! (foo) from Main
            |Refs: junit.framework.Test
            |"""
              .trimMargin()
          )
      }
    }
  }

  @Test // #443
  fun registerCustomShadowJarThatContainsDependenciesOnly() {
    val mainClassEntry = writeClass()
    val dependencyShadowJar = "dependencyShadowJar"

    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |def $dependencyShadowJar = tasks.register('$dependencyShadowJar', ${ShadowJar::class.java.name}) {
      |  description = 'Create a shadow JAR of all dependencies'
      |  archiveClassifier = 'dep'
      |  configurations = project.configurations.named('runtimeClasspath').map { [it] }
      |}
      """
        .trimMargin()
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

  @Test
  fun registerCustomShadowJarWithoutShadowR8Configuration() {
    val customShadowJar = "customShadowJar"
    projectScript.writeText(
      """
      |${getDefaultProjectBuildScript(applyShadowPlugin = false)}
      |def $customShadowJar = tasks.register('$customShadowJar', ${ShadowJar::class.java.name}) {
      |  minimize {
      |    r8 {}
      |  }
      |}
      """
        .trimMargin()
    )

    val result = runWithFailure(customShadowJar)

    assertThat(result.output)
      .contains(
        "R8 minimization requires a non-empty R8 classpath. Apply the Shadow plugin or configure the shadowR8 configuration."
      )
  }

  @Test // #1975
  fun skipNonExistentDependencyDirectory() {
    val nonExistentDir = projectRoot.resolve("non-existent-dir")

    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(nonExistentDir)}
      |}
      """
        .trimMargin()
    )

    val result = runWithSuccess(shadowJarPath)
    assertThat(result).taskOutcomeEquals(shadowJarPath, SUCCESS)
  }

  @Test // #915
  fun failBuildIfProcessingBadJar() {
    val badJarPath = path("bad.jar").apply { writeText("A bad jar.") }

    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(badJarPath)}
      |}
      """
        .trimMargin()
    )

    val result = runWithFailure(shadowJarPath)

    assertThat(result.output).containsMatch("Cannot expand ZIP '.*bad\\.jar'".toRegex())
  }

  @Test
  fun failBuildIfProcessingAar() {
    val fooAarPath = buildJar("foo.aar") { insert("AndroidManifest.xml", "<manifest/>") }

    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(fooAarPath)}
      |}
      """
        .trimMargin()
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
      |$shadowJarTask {
      |  from(file('${artifactAJar.invariantSeparatorsPathString}')) { // Without unzipping.
      |    into('META-INF')
      |  }
      |  from(zipTree(file('${artifactBJar.invariantSeparatorsPathString}'))) { // With unzipping.
      |    into('META-INF')
      |  }
      |  from('Foo') {
      |    into('Bar')
      |  }
      |}
      """
        .trimMargin()
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
      |def nonJar = configurations.create('nonJar')
      |dependencies {
      |  add('nonJar', 'my:a:1.0')
      |  add('nonJar', 'my:b:1.0')
      |}
      |$shadowJarTask {
      |  from(nonJar)
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("a-1.0.jar", "b-1.0.jar", *manifestEntries)
    }
  }

  @Test // #520
  fun onlyKeepFilesFromProjectWhenDuplicatesStrategyIsExclude() {
    val fooJar = buildJar("foo.jar") { insert("module-info.class", "module myModuleName {}") }
    val mainClassEntry = writeClass()
    writeClass(className = "module-info") { "module myModuleName {}" }
    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(fooJar)}
      |}
      |$shadowJarTask {
      |  excludes.remove(
      |    'module-info.class'
      |  )
      |}
      """
        .trimMargin()
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

  @Test // #1441
  fun includeFilesInTaskOutputDirectory() {
    // Create a build that has a task with jars in the output directory
    projectScript.appendText(
      $$"""
      |def createJars = tasks.register('createJars') {
      |  def artifactAJar = file('$${artifactAJar.invariantSeparatorsPathString}')
      |  def artifactBJar = file('$${artifactBJar.invariantSeparatorsPathString}')
      |  inputs.files(artifactAJar, artifactBJar)
      |  def outputDir = file('${buildDir}/jars')
      |  outputs.dir(outputDir)
      |  doLast {
      |    artifactAJar.withInputStream { input ->
      |        new File(outputDir, 'jarA.jar').withOutputStream { output ->
      |            output << input
      |        }
      |    }
      |    artifactBJar.withInputStream { input ->
      |        new File(outputDir, 'jarB.jar').withOutputStream { output ->
      |            output << input
      |        }
      |    }
      |  }
      |}
      |$$shadowJarTask {
      |  includedDependencies.from(files(createJars).asFileTree)
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsOnly(*entriesInAB, *manifestEntries) }
  }

  @Test
  fun integrateWithDevelocityBuildScan() {
    writeClientAndServerModules()
    settingsScript.prependText(
      """
      |plugins {
      |  id 'com.gradle.develocity'
      |}
      |"""
        .trimMargin()
    )

    val result =
      runWithSuccess(
        serverShadowJarPath,
        infoArgument,
        "-P${ENABLE_DEVELOCITY_INTEGRATION_PROPERTY}=true",
        // Using scan.dump avoids actually publishing a Build Scan, writing it to a file instead.
        "-Dscan.dump",
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
      |dependencies {
      |  implementation 'my:a:1.0'
      |}
      |$shadowJarTask {
      |  duplicatesStrategy = DuplicatesStrategy.INCLUDE
      |  failOnDuplicateEntries = $enable
      |}
      """
        .trimMargin()
    )

    val result =
      if (enable) {
        runWithFailure(shadowJarPath)
      } else {
        runWithSuccess(shadowJarPath)
      }

    assertThat(result.output)
      .contains("Duplicate entries found in the shadowed JAR:", "a.properties (2 times)")
  }

  @ParameterizedTest
  @MethodSource("fallbackMainClassProvider")
  fun fallbackMainClassByProperty(input: String, expected: String?, message: String) {
    projectScript.appendText(
      """
      |$shadowJarTask {
      |  mainClass = '$input'
      |}
      """
        .trimMargin()
    )

    val result = runWithSuccess(shadowJarPath, infoArgument)

    assertThat(result.output).contains(message)
    assertThat(outputShadowedJar).useAll { getMainAttr(mainClassAttributeKey).isEqualTo(expected) }
  }

  @Test // #882
  fun compatGradleArtifactTransform() {
    settingsScript.writeText("include('app', 'lib')\n")
    path("lib/build.gradle")
      .writeText(
        """
        |plugins {
        |  id 'java-library'
        |}
        """
          .trimMargin()
      )
    path("lib/src/main/java/com/company/Utils.java")
      .writeText(
        """
        |package com.company;
        |
        |public class Utils {
        |  public static void foo() {
        |    System.out.println("bar");
        |  }
        |}
        """
          .trimMargin()
      )
    path("app/build.gradle")
      .writeText(
        """
        |import org.gradle.api.artifacts.transform.TransformParameters
        |import org.gradle.api.artifacts.transform.TransformAction
        |import org.gradle.api.artifacts.transform.TransformOutputs
        |import org.gradle.api.artifacts.transform.InputArtifact
        |import org.gradle.api.file.FileSystemLocation
        |import org.gradle.api.provider.Provider
        |
        |plugins {
        |  id 'application'
        |  id '$shadowPluginId'
        |}
        |
        |application {
        |  mainClass = 'com.company.Main'
        |}
        |
        |dependencies {
        |  implementation project(':lib')
        |}
        |
        |def transformedAttribute = Attribute.of('custom-transformed', Boolean)
        |
        |dependencies {
        |  attributesSchema {
        |    attribute(transformedAttribute)
        |  }
        |  artifactTypes.maybeCreate('jar').attributes.attribute(transformedAttribute, false)
        |}
        |
        |dependencies {
        |  registerTransform(CustomTransformAction) {
        |    from.attribute(Attribute.of('artifactType', String), 'jar').attribute(transformedAttribute, false)
        |    to.attribute(Attribute.of('artifactType', String), 'jar').attribute(transformedAttribute, true)
        |  }
        |}
        |
        |$shadowJarTask {
        |  configurations = [project.configurations.runtimeClasspath]
        |}
        |
        |configurations.runtimeClasspath {
        |  attributes.attribute(transformedAttribute, true)
        |}
        |
        |abstract class CustomTransformAction implements TransformAction<TransformParameters.None> {
        |  @InputArtifact abstract Provider<FileSystemLocation> getInputArtifact()
        |
        |  @Override
        |  void transform(TransformOutputs outputs) {
        |    outputs.file(inputArtifact.get().asFile)
        |  }
        |}
        """
          .trimMargin()
      )
    path("app/src/main/java/com/company/Main.java")
      .writeText(
        """
        |package com.company;
        |
        |public class Main {
        |  public static void main(String[] args) {
        |    Utils.foo();
        |  }
        |}
        """
          .trimMargin()
      )

    runWithSuccess(":app:$SHADOW_JAR_TASK_NAME")

    assertThat(jarPath("app/build/libs/app-all.jar")).useAll {
      containsAtLeast("com/company/Main.class", "com/company/Utils.class", manifestEntry)
    }
  }

  @Test // #2086
  fun useToolchainWithoutTargetCompatibilityInKts() {
    projectScript.deleteExisting()
    path("build.gradle.kts")
      .appendText(
        """
        |plugins {
        |  kotlin("jvm")
        |  id("$shadowPluginId")
        |}
        |java {
        |  toolchain.languageVersion = JavaLanguageVersion.of(${JavaVersion.current().majorVersion})
        |}
        """
          .trimMargin()
      )

    val result = runWithSuccess(shadowJarPath)
    assertThat(result).taskOutcomeEquals(shadowJarPath, SUCCESS)
  }

  @Test // #2099
  fun doNotResolveR8WhenLockingAllConfigurations() {
    projectScript.appendText(
      """
      |dependencyLocking {
      |  lockAllConfigurations()
      |}
      |
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |
      |tasks.register('resolveAndLockAll') {
      |  notCompatibleWithConfigurationCache('Filters configurations at execution time')
      |  doFirst {
      |    assert gradle.startParameter.writeDependencyLocks
      |  }
      |  doLast {
      |    configurations.matching { it.canBeResolved }.each { it.resolve() }
      |  }
      |}
      """
        .trimMargin()
    )

    runWithSuccess("resolveAndLockAll", "--write-locks")

    assertThat(path("gradle.lockfile").toFile().readText()).all {
      contains("junit:junit:3.8.2")
      doesNotContain("com.android.tools:r8")
    }
  }

  @Test
  fun handleCaseSensitiveEntriesAcrossMultipleJars() {
    val one =
      buildJar("one.jar") {
        insert("foo.txt", "lower")
        insert("Bar.class", createEmptyClassBytes("Bar"))
      }
    val two =
      buildJar("two.jar") {
        insert("Foo.txt", "upper")
        insert("bar.class", createEmptyClassBytes("bar"))
      }

    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(one, two)}
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "foo.txt",
        "Foo.txt",
        "bar.class",
        "Bar.class",
        *manifestEntries,
      )
      getBytes("Bar.class").isEqualTo(createEmptyClassBytes("Bar"))
      getBytes("bar.class").isEqualTo(createEmptyClassBytes("bar"))
      getContent("foo.txt").isEqualTo("lower")
      getContent("Foo.txt").isEqualTo("upper")
    }
  }

  @Test
  fun generateJavadocFromShadowedSourcesJar() {
    path("src/main/java/my/Main.java")
      .writeText(
        """
        |package my;
        |/** Main class doc */
        |public class Main {
        |  public static void main(String[] args) {}
        |}
        """
          .trimMargin()
      )
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'my:g:1.0'
      |}
      |$shadowJarTask {
      |  generateSourcesJar = true
      |  relocate 'g', 'shadow.g'
      |}
      |tasks.named('javadoc', Javadoc) {
      |  classpath = files($shadowJarTask.flatMap { it.archiveFile })
      |  source = zipTree($shadowJarTask.flatMap { it.archiveSourcesFile }).matching { include('**/*.java') }
      |}
      """
        .trimMargin()
    )

    runWithSuccess("javadoc")

    val javadocDir = projectRoot.resolve("build/docs/javadoc")
    val javadocFiles =
      javadocDir.walk().map { it.relativeTo(javadocDir).invariantSeparatorsPathString }
    assertThat(javadocFiles)
      .containsAtLeast(
        "index.html",
        "my/Main.html",
        "shadow/g/G.html",
      )
  }

  @Test
  fun sourcesJarPreservesResourceRelativePath() {
    writeClass()
    path("src/main/resources/config/sub/app.properties").writeText("key=value")

    projectScript.appendText(
      """
      |$shadowJarTask {
      |  generateSourcesJar = true
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedSourcesJar).useAll {
      containsAtLeast(
        "my/Main.java",
        "config/sub/app.properties",
      )
    }
  }

  @Test
  fun sourcesJarHandlesOverlappingSourceDirectoryPrefixes() {
    writeClass()
    path("src/main/res/a.properties").writeText("a=1")
    path("src/main/resources/b.properties").writeText("b=2")

    projectScript.appendText(
      """
      |sourceSets {
      |  main {
      |    resources {
      |      srcDir 'src/main/res'
      |    }
      |  }
      |}
      |$shadowJarTask {
      |  generateSourcesJar = true
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedSourcesJar).useAll {
      containsAtLeast(
        "my/Main.java",
        "a.properties",
        "b.properties",
      )
    }
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
