import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  alias(libs.plugins.kotlin)
  alias(libs.plugins.android.lint)
  alias(libs.plugins.jetbrains.bcv)
  alias(libs.plugins.spotless)
  groovy // Required for Spock tests.
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

  testImplementation(libs.spock) {
    exclude(group = "org.codehaus.groovy")
    exclude(group = "org.hamcrest")
  }
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.junit.platformSuite)
  testImplementation(libs.xmlunit)
  testImplementation(libs.apache.commonsLang)
  testImplementation(libs.guava)

  lintChecks(libs.androidx.gradlePluginLints)
}

val isCI = providers.environmentVariable("CI").isPresent

tasks.withType<Test>().configureEach {
  useJUnitPlatform()

  maxParallelForks = Runtime.getRuntime().availableProcessors()

  if (isCI) {
    testLogging.showStandardStreams = true
    minHeapSize = "1g"
    maxHeapSize = "1g"
  }

  // Add src/docs as an input directory to trigger ManualCodeSnippetTests re-run on changes.
  inputs.dir(file("src/docs"))
  systemProperty("shadowVersion", version)

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

tasks.register("release") {
  dependsOn(
    tasks.publish,
    tasks.publishPlugins,
    tasks.gitPublishPush,
  )
}
