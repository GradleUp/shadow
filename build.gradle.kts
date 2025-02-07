@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.android.lint)
  alias(libs.plugins.jetbrains.bcv)
  alias(libs.plugins.spotless)
  id("shadow.convention.publish")
  id("shadow.convention.deploy")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
  explicitApi()
  compilerOptions {
    // https://docs.gradle.org/current/userguide/compatibility.html#kotlin
    apiVersion = KotlinVersion.KOTLIN_1_8
    jvmTarget = JvmTarget.JVM_1_8
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

val testPluginClasspath: Configuration by configurations.creating {
  isCanBeResolved = true
}

dependencies {
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

  lintChecks(libs.androidx.gradlePluginLints)
}

testing.suites {
  getByName<JvmTestSuite>("test") {
    dependencies {
      implementation(libs.xmlunit)
    }
  }
  register<JvmTestSuite>("integrationTest") {
    testType = TestSuiteType.INTEGRATION_TEST
    targets.configureEach {
      testTask {
        val docsDir = file("src/docs")
        // Add src/docs as an input directory to trigger ManualCodeSnippetTests re-run on changes.
        inputs.dir(docsDir)
        systemProperty("DOCS_DIR", docsDir.absolutePath)
      }
    }
  }
  register<JvmTestSuite>("functionalTest") {
    testType = TestSuiteType.FUNCTIONAL_TEST
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
    sourceSets["integrationTest"],
  )
}

tasks.pluginUnderTestMetadata {
  // Plugins used in tests could be resolved in classpath.
  pluginClasspath.from(
    testPluginClasspath,
  )
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

  val dirs = includedBuilds.map { it.projectDir } + projectDir
  delete.addAll(dirs.map { it.resolve(".gradle") })
  delete.addAll(dirs.map { it.resolve(".kotlin") })
  delete.add("node_modules")
}

tasks.register("releaseAll") {
  description = "Publishes the plugin to maven repos and deploys website."
  group = PublishingPlugin.PUBLISH_TASK_GROUP

  dependsOn(
    tasks.publish,
    tasks.publishPlugins,
    tasks.gitPublishPush,
  )
}
