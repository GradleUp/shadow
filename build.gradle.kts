@file:Suppress("UnstableApiUsage")

import org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.JAVADOC_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.SOURCES_ELEMENTS_CONFIGURATION_NAME
import org.jetbrains.kotlin.daemon.common.OSKind
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.android.lint)
  alias(libs.plugins.jetbrains.dokka)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.pluginPublish)
  alias(libs.plugins.spotless)
}

version = providers.gradleProperty("VERSION_NAME").get()
group = providers.gradleProperty("GROUP").get()
description = providers.gradleProperty("POM_DESCRIPTION").get()

dokka {
  dokkaPublications.html {
    outputDirectory = rootDir.resolve("docs/api")
  }
}

kotlin {
  explicitApi()
  @OptIn(ExperimentalAbiValidation::class)
  abiValidation {
    enabled = true
  }
  compilerOptions {
    allWarningsAsErrors = true
    // https://docs.gradle.org/current/userguide/compatibility.html#kotlin
    apiVersion = KotlinVersion.KOTLIN_2_0
    languageVersion = apiVersion
    jvmTarget = JvmTarget.JVM_11
    // Sync with `JavaCompile.options.release`.
    freeCompilerArgs.add("-Xjdk-release=11")
  }
}

lint {
  baseline = file("gradle/lint-baseline.xml")
  ignoreTestSources = true
  warningsAsErrors = true
}

spotless {
  kotlin {
    ktlint(libs.ktlint.get().version)
  }
  kotlinGradle {
    ktlint(libs.ktlint.get().version)
  }
}

val testPluginClasspath by configurations.registering {
  isCanBeResolved = true
  description = "Plugins used in integration tests could be resolved in classpath."
}

configurations.configureEach {
  when (name) {
    API_ELEMENTS_CONFIGURATION_NAME,
    RUNTIME_ELEMENTS_CONFIGURATION_NAME,
    JAVADOC_ELEMENTS_CONFIGURATION_NAME,
    SOURCES_ELEMENTS_CONFIGURATION_NAME,
    -> {
      outgoing {
        // Main/current capability.
        capability("com.gradleup.shadow:shadow-gradle-plugin:$version")

        // Historical capabilities.
        capability("io.github.goooler.shadow:shadow-gradle-plugin:$version")
        capability("com.github.johnrengelman:shadow:$version")
        capability("gradle.plugin.com.github.jengelman.gradle.plugins:shadow:$version")
        capability("gradle.plugin.com.github.johnrengelman:shadow:$version")
        capability("com.github.jengelman.gradle.plugins:shadow:$version")
      }
    }
  }
}

publishing.publications.withType<MavenPublication>().configureEach {
  // We don't care about capabilities being unmappable to Maven.
  suppressPomMetadataWarningsFor(API_ELEMENTS_CONFIGURATION_NAME)
  suppressPomMetadataWarningsFor(RUNTIME_ELEMENTS_CONFIGURATION_NAME)
  suppressPomMetadataWarningsFor(JAVADOC_ELEMENTS_CONFIGURATION_NAME)
  suppressPomMetadataWarningsFor(SOURCES_ELEMENTS_CONFIGURATION_NAME)
}

val testGradleVersion: String = providers.gradleProperty("testGradleVersion").orNull.let {
  val value = if (it == null || it == "current") GradleVersion.current().version else it
  logger.info("Using test Gradle version: $value")
  value
}

develocity {
  buildScan.value("testGradleVersion", testGradleVersion)
}

dependencies {
  compileOnly(libs.develocity)
  compileOnly(libs.kotlin.kmp)
  api(libs.apache.ant) // Types from Ant are exposed in the public API.
  implementation(libs.apache.commonsIo)
  implementation(libs.apache.log4j)
  implementation(libs.asm)
  implementation(libs.jdependency)
  implementation(libs.jdom2)
  implementation(libs.plexus.utils)
  implementation(libs.plexus.xml)

  testPluginClasspath(libs.agp)
  testPluginClasspath(libs.foojayResolver)
  testPluginClasspath(libs.develocity)
  testPluginClasspath(libs.kotlin.kmp)
  testPluginClasspath(libs.pluginPublish)

  lintChecks(libs.androidx.gradlePluginLints)
}

testing.suites {
  getByName<JvmTestSuite>("test") {
    dependencies {
      implementation(libs.xmlunit)
    }
  }
  register<JvmTestSuite>("documentTest") {
    targets.configureEach {
      testTask {
        val docsDir = file("docs").also {
          if (!it.exists() || !it.isDirectory) error("Docs dir $it does not exist or is not a directory.")
        }
        // Add docs as an input directory to trigger ManualCodeSnippetTests re-run on changes.
        inputs.dir(docsDir)
        systemProperty("DOCS_DIR", docsDir.absolutePath)
      }
    }
  }
  register<JvmTestSuite>("functionalTest") {
    targets.configureEach {
      testTask {
        // Required to enable `IssueExtension` for all tests.
        systemProperty("junit.jupiter.extensions.autodetection.enabled", true)

        // Required to test configuration cache in tests when using withDebug().
        // See https://github.com/gradle/gradle/issues/22765#issuecomment-1339427241.
        jvmArgs(
          "--add-opens",
          "java.base/java.util=ALL-UNNAMED",
          "--add-opens",
          "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
          "--add-opens",
          "java.base/java.lang.invoke=ALL-UNNAMED",
          "--add-opens",
          "java.base/java.net=ALL-UNNAMED",
        )
      }
    }
    dependencies {
      implementation(libs.apache.maven.modelBuilder)
      implementation(libs.moshi)
      implementation(libs.moshi.kotlin)
      implementation(libs.apache.log4j)
    }
  }

  withType<JvmTestSuite>().configureEach {
    useJUnitJupiter(libs.junit.bom.map { requireNotNull(it.version) })
    dependencies {
      implementation(libs.assertk)
    }
    targets.configureEach {
      testTask {
        systemProperty("TEST_GRADLE_VERSION", testGradleVersion)
        maxParallelForks = Runtime.getRuntime().availableProcessors()
      }
    }
  }
}

gradlePlugin {
  website = providers.gradleProperty("POM_URL")
  vcsUrl = providers.gradleProperty("POM_URL")

  plugins {
    create("shadowPlugin") {
      id = "com.gradleup.shadow"
      implementationClass = "com.github.jengelman.gradle.plugins.shadow.ShadowPlugin"
      displayName = providers.gradleProperty("POM_NAME").get()
      description = providers.gradleProperty("POM_DESCRIPTION").get()
      tags = listOf("onejar", "shade", "fatjar", "uberjar")
    }
  }

  testSourceSets(
    sourceSets["functionalTest"],
    sourceSets["documentTest"],
  )
}

// This part should be placed after testing.suites to ensure the test sourceSets are created.
kotlin.target.compilations {
  val main by getting
  getByName("functionalTest") {
    // TODO: https://youtrack.jetbrains.com/issue/KTIJ-7662
    associateWith(main)
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.release = 11
}

tasks.pluginUnderTestMetadata {
  pluginClasspath.from(
    testPluginClasspath,
  )
}

tasks.validatePlugins {
  // TODO: https://github.com/gradle/gradle/issues/22600
  enableStricterValidation = true
}

tasks.whenTaskAdded {
  if (name.contains("lint") && this::class.java.name.contains("com.android.build")) {
    // Disable lint tasks for Windows due to ExceptionInInitializerError.
    enabled = OSKind.current != OSKind.Windows
  }
}

tasks.check {
  dependsOn(
    tasks.withType<Test>(),
    // TODO: https://youtrack.jetbrains.com/issue/KT-78525
    tasks.checkLegacyAbi,
  )
}

tasks.register<Copy>("downloadStartScripts") {
  description = "Download start scripts from Gradle sources, this should be run intervally to track updates."

  val urlPrefix = "https://raw.githubusercontent.com/gradle/gradle/refs/heads/master/platforms/jvm/" +
    "plugins-application/src/main/resources/org/gradle/api/internal/plugins"
  from(resources.text.fromUri("$urlPrefix/unixStartScript.txt")) {
    rename { "unixStartScript.txt" }
  }
  from(resources.text.fromUri("$urlPrefix/windowsStartScript.txt")) {
    rename { "windowsStartScript.txt" }
  }
  val destDir = file("src/main/resources/com/github/jengelman/gradle/plugins/shadow/internal").also {
    if (!it.exists() || !it.isDirectory) error("Download destination dir $it does not exist or is not a directory.")
  }
  into(destDir)
}

tasks.clean {
  delete += listOf(
    projectDir.resolve(".gradle"),
    projectDir.resolve(".kotlin"),
    dokka.dokkaPublications.html.map { it.outputDirectory },
    // Generated by MkDocs.
    rootDir.resolve("site"),
  )
}
