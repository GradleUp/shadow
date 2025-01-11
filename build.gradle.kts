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

dependencies {
  implementation(libs.apache.ant)
  implementation(libs.apache.commonsIo)
  implementation(libs.apache.log4j)
  implementation(libs.asm)
  implementation(libs.jdependency)
  implementation(libs.jdom2)
  implementation(libs.plexus.utils)
  implementation(libs.plexus.xml)

  lintChecks(libs.androidx.gradlePluginLints)
}

@Suppress("UnstableApiUsage")
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

        // Required to test configuration cache in tests when using withDebug()
        // https://github.com/gradle/gradle/issues/22765#issuecomment-1339427241
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
      // Seems we can't ref project() here due to some limitations of rootProject.
      implementation(sourceSets.main.get().output)
      implementation(libs.apache.ant)
      implementation(libs.apache.maven.modelBuilder)
      implementation(libs.moshi)
      implementation(libs.moshi.kotlin)
    }
  }

  withType<JvmTestSuite>().configureEach {
    useJUnitJupiter(libs.junit.bom.get().version)
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

gradlePlugin {
  testSourceSets(
    sourceSets["functionalTest"],
    sourceSets["integrationTest"],
  )
}

tasks.check {
  dependsOn(
    testing.suites.named("integrationTest"),
    testing.suites.named("functionalTest"),
  )
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
