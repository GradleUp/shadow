package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.exists
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE

class ShadowPluginTest : BasePluginTest() {

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
    assertThat(project.tasks.findByName(shadowJarTask)).isNull()

    project.plugins.apply(JavaPlugin::class.java)
    val shadowTask = project.tasks.getByName(shadowJarTask) as ShadowJar
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
  @EnabledForJreRange(
    max = JRE.JAVA_21,
    disabledReason = "Gradle 8.3 doesn't support Java 21.",
  )
  fun compatibleWithMinGradleVersion() {
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

    assertThat(outputShadowJar).exists()
  }

  @Test
  fun incompatibleWithLowerMinGradleVersion() {
    runWithFailure(shadowJarTask) {
      it.withGradleVersion("8.2")
    }
  }

  @Test
  fun shadowCopy() {
    val artifactJar = requireNotNull(this::class.java.classLoader.getResource("test-artifact-1.0-SNAPSHOT.jar"))
      .toURI().toPath()
    val projectJar = requireNotNull(this::class.java.classLoader.getResource("test-project-1.0-SNAPSHOT.jar"))
      .toURI().toPath()

    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(artifactJar, projectJar)}
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()
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
    val outputShadowJar = jarPath("build/libs/shadow.jar")

    assertThat(outputShadowJar).exists()
    outputShadowJar.assertContains(
      listOf("shadow/Passed.class", "junit/framework/Test.class"),
    )
    outputShadowJar.assertDoesNotContain(
      listOf("/"),
    )
  }

  @Test
  fun includeProjectDependencies() {
    writeClientAndServerModules()

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).exists()
    outputServerShadowJar.assertContains(
      listOf("client/Client.class", "server/Server.class", "junit/framework/Test.class"),
    )
  }

  /**
   * 'Server' depends on 'Client'. 'junit' is independent.
   * The minimize shall remove 'junit'.
   */
  @Test
  fun minimizeByKeepingOnlyTransitiveDependencies() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize()
      """.trimIndent(),
    )
    path("server/src/main/java/server/Server.java").writeText(
      """
        package server;
        import client.Client;
        public class Server {
          // This is to make sure that 'Client' is not removed.
          private final String client = Client.class.getName();
        }
      """.trimIndent(),
    )

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).exists()
    outputServerShadowJar.assertContains(
      listOf("client/Client.class", "server/Server.class"),
    )
    outputServerShadowJar.assertDoesNotContain(
      listOf("junit/framework/Test.class"),
    )
  }

  /**
   * 'Client', 'Server' and 'junit' are independent.
   * 'junit' is excluded from the minimize step.
   * The minimize step shall remove 'Client' but not 'junit'.
   */
  @Test
  fun excludeDependencyFromMinimize() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize {
          exclude(dependency('junit:junit:.*'))
        }
      """.trimIndent(),
    )

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).exists()
    outputServerShadowJar.assertContains(
      listOf("server/Server.class", "junit/framework/Test.class"),
    )
    outputServerShadowJar.assertDoesNotContain(
      listOf("client/Client.class"),
    )
  }

  /**
   * 'Client', 'Server' and 'junit' are independent.
   * Unused classes of 'client' and theirs dependencies shouldn't be removed.
   */
  @Test
  fun excludeProjectFromMinimize() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize {
          exclude(project(':client'))
        }
      """.trimIndent(),
    )

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).exists()
    outputServerShadowJar.assertContains(
      listOf("client/Client.class", "server/Server.class"),
    )
  }

  /**
   * 'Client', 'Server' and 'junit' are independent.
   * Unused classes of 'client' and theirs dependencies shouldn't be removed.
   */
  @Test
  fun excludeProjectFromMinimizeShallNotExcludeTransitiveDependenciesThatAreUsedInSubproject() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize {
          exclude(project(':client'))
        }
      """.trimIndent(),
    )
    path("client/src/main/java/client/Client.java").writeText(
      """
        package client;
        import junit.framework.TestCase;
        public class Client extends TestCase {
          public static void main(String[] args) {}
        }
      """.trimIndent(),
    )

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).exists()
    outputServerShadowJar.assertContains(
      listOf("client/Client.class", "server/Server.class", "junit/framework/TestCase.class"),
    )

    path("client/src/main/java/client/Client.java").writeText(
      """
        package client;
        public class Client {}
      """.trimIndent(),
    )
    run(serverShadowJarTask)
    assertThat(outputServerShadowJar).exists()
    // TODO: I don't think junit classes should be in the output jar, but it's the test case
    //  from https://github.com/GradleUp/shadow/pull/420, need to investigate more...
    outputServerShadowJar.assertContains(
      listOf("client/Client.class", "server/Server.class", "junit/framework/TestCase.class"),
    )
  }

  /**
   * 'api' used as api for 'impl', and depended on 'lib'. 'junit' is independent.
   * The minimize step shall remove 'junit', but not 'api'.
   * Unused classes of 'api' and theirs dependencies also shouldn't be removed.
   */
  @Test
  fun useMinimizeWithDependenciesWithApiScope() {
    writeApiLibAndImplModules()

    run(":impl:$shadowJarTask")
    val implOutput = jarPath("impl/build/libs/impl-all.jar")

    assertThat(implOutput).exists()
    implOutput.assertContains(
      listOf("impl/SimpleEntity.class", "api/Entity.class", "api/UnusedEntity.class", "lib/LibEntity.class"),
    )
    implOutput.assertDoesNotContain(
      listOf("junit/framework/Test.class", "lib/UnusedLibEntity.class"),
    )
  }

  /**
   * 'api' used as api for 'impl', and 'lib' used as api for 'api'.
   * Unused classes of 'api' and 'lib' shouldn't be removed.
   */
  @Test
  fun useMinimizeWithTransitiveDependenciesWithApiScope() {
    writeApiLibAndImplModules()
    path("api/build.gradle").writeText(
      """
        plugins {
          id 'java-library'
        }
        dependencies {
          api project(':lib')
        }
      """.trimIndent(),
    )

    run(":impl:$shadowJarTask")
    val implOutput = jarPath("impl/build/libs/impl-all.jar")

    assertThat(implOutput).exists()
    implOutput.assertContains(
      listOf(
        "impl/SimpleEntity.class",
        "api/Entity.class",
        "api/UnusedEntity.class",
        "lib/LibEntity.class",
        "lib/UnusedLibEntity.class",
      ),
    )
    implOutput.assertDoesNotContain(
      listOf("junit/framework/Test.class"),
    )
  }

  @Test
  fun dependOnProjectShadowJar() {
    writeShadowedClientAndServerModules()

    run(":server:jar")
    val serverOutput = jarPath("server/build/libs/server-1.0.jar")
    val clientOutput = jarPath("client/build/libs/client-all.jar")

    assertThat(serverOutput).exists()
    serverOutput.assertContains(
      listOf("server/Server.class"),
    )
    serverOutput.assertDoesNotContain(
      listOf("client/Client.class", "junit/framework/Test.class", "client/junit/framework/Test.class"),
    )
    assertThat(clientOutput).exists()
    clientOutput.assertContains(
      listOf("client/Client.class", "client/junit/framework/Test.class"),
    )
  }

  @Test
  fun shadowProjectShadowJar() {
    writeShadowedClientAndServerModules()

    run(serverShadowJarTask)
    val clientOutput = jarPath("client/build/libs/client-all.jar")

    assertThat(outputServerShadowJar).exists()
    outputServerShadowJar.assertContains(
      listOf("client/Client.class", "client/junit/framework/Test.class", "server/Server.class"),
    )
    outputServerShadowJar.assertDoesNotContain(
      listOf("junit/framework/Test.class"),
    )
    assertThat(clientOutput).exists()
    clientOutput.assertContains(
      listOf("client/Client.class", "client/junit/framework/Test.class"),
    )
  }

  @Test
  fun excludeSomeMetaInfFilesByDefault() {
    repo.module("shadow", "a", "1.0")
      .insertFile("a.properties", "a")
      .insertFile("META-INF/INDEX.LIST", "JarIndex-Version: 1.0")
      .insertFile("META-INF/a.SF", "Signature File")
      .insertFile("META-INF/a.DSA", "DSA Signature Block")
      .insertFile("META-INF/a.RSA", "RSA Signature Block")
      .insertFile("META-INF/a.properties", "key=value")
      .insertFile("module-info.class", "module myModuleName {}")
      .publish()

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

    outputShadowJar.assertContains(
      listOf("shadow/Passed.class", "a.properties", "META-INF/a.properties"),
    )
    outputShadowJar.assertDoesNotContain(
      listOf("META-INF/INDEX.LIST", "META-INF/a.SF", "META-INF/a.DSA", "META-INF/a.RSA", "module-info.class"),
    )
  }

  @Test
  fun includeRuntimeConfigurationByDefault() {
    publishArtifactA()
    publishArtifactB()

    projectScriptPath.appendText(
      """
        dependencies {
          runtimeOnly 'shadow:a:1.0'
          shadow 'shadow:b:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    outputShadowJar.assertContains(
      listOf("a.properties", "a2.properties"),
    )
    outputShadowJar.assertDoesNotContain(
      listOf("b.properties"),
    )
  }

  @Test
  fun includeJavaLibraryConfigurationsByDefault() {
    repo.module("shadow", "api", "1.0")
      .insertFile("api.properties", "api")
      .publish()
    repo.module("shadow", "implementation-dep", "1.0")
      .insertFile("implementation-dep.properties", "implementation-dep")
      .publish()
    repo.module("shadow", "implementation", "1.0")
      .insertFile("implementation.properties", "implementation")
      .dependsOn("implementation-dep")
      .publish()
    repo.module("shadow", "runtimeOnly", "1.0")
      .insertFile("runtimeOnly.properties", "runtimeOnly")
      .publish()

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

    outputShadowJar.assertContains(
      listOf("api.properties", "implementation.properties", "runtimeOnly.properties", "implementation-dep.properties"),
    )
  }

  @Test
  fun doesNotIncludeCompileOnlyConfigurationByDefault() {
    publishArtifactA()
    publishArtifactB()

    projectScriptPath.appendText(
      """
        dependencies {
          runtimeOnly 'shadow:a:1.0'
          compileOnly 'shadow:b:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    outputShadowJar.assertContains(
      listOf("a.properties", "a2.properties"),
    )
    outputShadowJar.assertDoesNotContain(
      listOf("b.properties"),
    )
  }

  @Test
  fun defaultCopyingStrategy() {
    repo.module("shadow", "a", "1.0")
      .insertFile("META-INF/MANIFEST.MF", "MANIFEST A")
      .publish()
    repo.module("shadow", "b", "1.0")
      .insertFile("META-INF/MANIFEST.MF", "MANIFEST B")
      .publish()

    projectScriptPath.appendText(
      """
        dependencies {
          runtimeOnly 'shadow:a:1.0'
          runtimeOnly 'shadow:b:1.0'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()
    assertThat(outputShadowJar.entries().toList().size).isEqualTo(2)
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

    assertThat(outputShadowJar).exists()
    val attributes = outputShadowJar.manifest.mainAttributes
    assertThat(attributes.getValue("Class-Path")).isNull()
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
            attributes 'Class-Path': '/libs/a.jar'
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()
    val attributes = outputShadowJar.manifest.mainAttributes
    assertThat(attributes.getValue("Class-Path")).isEqualTo("/libs/a.jar junit-3.8.2.jar")
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

    assertThat(outputShadowJar).exists()
    assertThat(outputShadowJar.manifest.mainAttributes.getValue("Class-Path"))
      .isEqualTo("junit-3.8.2.jar")
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

    assertThat(outputShadowJar).exists()
  }

  /**
   * This spec requires > 15 minutes and > 8GB of disk space to run
   */
  @Issue(
    "https://github.com/GradleUp/shadow/issues/143",
  )
  @Disabled
  @Test
  fun checkLargeZipFilesWithZip64Enabled() {
    publishArtifactA()

    path("src/main/java/myapp/Main.java").writeText(
      """
        package myapp;
        public class Main {
          public static void main(String[] args) {
            System.out.println("TestApp: Hello World! (" + args[0] + ")");
          }
        }
      """.trimIndent(),
    )

    settingsScriptPath.appendText("rootProject.name = 'myapp'")
    projectScriptPath.appendText(
      """
        apply plugin: 'application'

        application {
          mainClass = 'myapp.Main'
        }
        dependencies {
          implementation 'shadow:a:1.0'
        }
        def generatedResourcesDir = new File(project.layout.buildDirectory.asFile.get(), "generated-resources")
        def generateResources = tasks.register('generateResources') {
          doLast {
            def rnd = new Random()
            def buf = new byte[128 * 1024]
            for (x in 0..255) {
              def dir = new File(generatedResourcesDir, x.toString())
              dir.mkdirs()
              for (y in 0..255) {
                def file = new File(dir, y.toString())
                rnd.nextBytes(buf)
                file.bytes = buf
              }
            }
          }
        }
        sourceSets {
          main {
            output.dir(generatedResourcesDir, builtBy: generateResources)
          }
        }
        $shadowJar {
          zip64 = true
        }
        $runShadow {
          args 'foo'
        }
      """.trimIndent(),
    )

    val result = run(runShadowTask)

    assertThat(result.output).contains("TestApp: Hello World! (foo)")
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/609",
  )
  @Test
  fun doesNotErrorWhenUsingApplicationMainClassProperty() {
    projectScriptPath.appendText(
      """
        apply plugin: 'application'

        application {
          mainClass = 'myapp.Main'
        }
        $runShadow {
          args 'foo'
        }
      """.trimIndent(),
    )

    path("src/main/java/myapp/Main.java").writeText(
      """
        package myapp;
        public class Main {
          public static void main(String[] args) {
            System.out.println("TestApp: Hello World! (" + args[0] + ")");
          }
        }
      """.trimIndent(),
    )

    val result = run(runShadowTask)

    assertThat(result.output).contains("TestApp: Hello World! (foo)")
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/459",
  )
  @Test
  fun excludeGradleApiByDefault() {
    projectScriptPath.writeText(
      getDefaultProjectBuildScript("java-gradle-plugin", withGroup = true, withVersion = true),
    )

    path("src/main/java/my/plugin/MyPlugin.java").writeText(
      """
        package my.plugin;
        import org.gradle.api.Plugin;
        import org.gradle.api.Project;
        public class MyPlugin implements Plugin<Project> {
          public void apply(Project project) {
            System.out.println("MyPlugin: Hello World!");
          }
        }
      """.trimIndent(),
    )
    path("src/main/resources/META-INF/gradle-plugins/my.plugin.properties").writeText(
      """
        implementation-class=my.plugin.MyPlugin
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).exists()

    val entries = outputShadowJar.entries().toList()
    assertThat(entries.count { it.name.endsWith(".class") }).isEqualTo(1)
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/1070",
  )
  @Test
  fun canRegisterCustomShadowJarTask() {
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
        }
      """.trimIndent(),
    )

    val result = run(testShadowJarTask)
    val testJar = jarPath("build/libs/shadow-1.0-tests.jar")

    assertThat(result.task(":$testShadowJarTask")).isNotNull()
      .transform { it.outcome }.isEqualTo(TaskOutcome.SUCCESS)

    assertThat(testJar).exists()
    assertThat(testJar.getEntry("junit")).isNotNull()
  }

  private fun writeShadowedClientAndServerModules() {
    writeClientAndServerModules()
    path("client/build.gradle").appendText(
      """
        $shadowJar {
          relocate 'junit.framework', 'client.junit.framework'
        }
      """.trimIndent(),
    )
    path("server/src/main/java/server/Server.java").writeText(
      """
        package server;
        import client.Client;
        import client.junit.framework.Test;
        public class Server {}
      """.trimIndent(),
    )
    val replaced = path("server/build.gradle").readText()
      .replace("project(':client')", "project(path: ':client', configuration: 'shadow')")
    path("server/build.gradle").writeText(replaced)
  }
}
