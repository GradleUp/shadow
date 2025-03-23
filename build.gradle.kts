@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.android.lint)
  alias(libs.plugins.jetbrains.bcv)
  alias(libs.plugins.spotless)
  id("shadow.convention.publish")
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
  explicitApi()
  compilerOptions {
    // https://docs.gradle.org/current/userguide/compatibility.html#kotlin
    apiVersion = KotlinVersion.KOTLIN_1_8
    jvmTarget = JvmTarget.JVM_11
    freeCompilerArgs.addAll(
      "-Xjvm-default=all",
    )
  }
}

lint {
  baseline = file("lint-baseline.xml")
  ignoreTestSources = true
  warningsAsErrors = true
}

spotless {
  kotlin {
    ktlint(libs.ktlint.get().version)
  }
  kotlinGradle {
    ktlint(libs.ktlint.get().version)
    target("**/*.kts")
    targetExclude("build-logic/build/**")
  }
}

val testPluginClasspath by configurations.registering {
  isCanBeResolved = true
}

dependencies {
  compileOnly(libs.kotlin.kmp)
  implementation(libs.apache.ant)
  implementation(libs.apache.commonsIo)
  implementation(libs.apache.log4j)
  implementation(libs.asm)
  implementation(libs.jdependency)
  implementation(libs.jdom2)
  implementation(libs.plexus.utils)
  implementation(libs.plexus.xml)

  testPluginClasspath(libs.foojayResolver)
  testPluginClasspath(libs.pluginPublish)
  testPluginClasspath(libs.kotlin.kmp)

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
        val docsDir = file("docs")
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
        maxParallelForks = Runtime.getRuntime().availableProcessors()
      }
    }
  }
}

// This part should be placed after testing.suites to ensure the test sourceSets are created.
kotlin.target.compilations {
  val main by getting
  val functionalTest by getting {
    // TODO: https://youtrack.jetbrains.com/issue/KTIJ-7662
    associateWith(main)
  }
}

gradlePlugin {
  testSourceSets(
    sourceSets["functionalTest"],
    sourceSets["documentTest"],
  )
}

tasks.pluginUnderTestMetadata {
  // Plugins used in tests could be resolved in classpath.
  pluginClasspath.from(
    testPluginClasspath,
  )
}

tasks.validatePlugins {
  // TODO: https://github.com/gradle/gradle/issues/22879
  enableStricterValidation = true
}

tasks.check {
  dependsOn(tasks.withType<Test>())
}

tasks.register<Copy>("downloadStartScripts") {
  description = "Download start scripts from Gradle sources, this should be run intervally to track updates."

  val urlPrefix = "https://raw.githubusercontent.com/gradle/gradle/refs/heads/master/platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins"
  from(resources.text.fromUri("$urlPrefix/unixStartScript.txt")) {
    rename { "unixStartScript.txt" }
  }
  from(resources.text.fromUri("$urlPrefix/windowsStartScript.txt")) {
    rename { "windowsStartScript.txt" }
  }
  val destDir = file("src/main/resources/com/github/jengelman/gradle/plugins/shadow/internal")
  if (!destDir.exists() || !destDir.isDirectory || destDir.listFiles().isNullOrEmpty()) {
    error("Download destination dir $destDir does not exist or is empty.")
  }
  into(destDir)
}

tasks.clean {
  val includedBuilds = gradle.includedBuilds
  dependsOn(includedBuilds.map { it.task(path) })

  val rootDirs = includedBuilds.map { it.projectDir } + projectDir
  delete += listOf(
    rootDirs.map { it.resolve(".gradle") },
    rootDirs.map { it.resolve(".kotlin") },
    tasks.dokkaHtml.map { it.outputDirectory },
    // Generated by MkDocs.
    rootDir.resolve("site"),
  )
}
