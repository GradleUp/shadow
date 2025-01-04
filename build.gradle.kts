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

val intiTest: SourceSet by sourceSets.creating
val intiTestImplementation: Configuration by configurations.getting {
  extendsFrom(configurations.testImplementation.get())
}
val intiTestRuntimeOnly: Configuration by configurations.getting {
  extendsFrom(configurations.testRuntimeOnly.get())
}

val funcTest: SourceSet by sourceSets.creating
val funcTestImplementation: Configuration by configurations.getting {
  extendsFrom(configurations.testImplementation.get())
  // TODO: this will be removed after we migrated all functional tests to Kotlin.
  extendsFrom(intiTestImplementation)
}
val funcTestRuntimeOnly: Configuration by configurations.getting {
  extendsFrom(configurations.testRuntimeOnly.get())
}

gradlePlugin {
  testSourceSets.add(intiTest)
  testSourceSets.add(funcTest)
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

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertk)
  testImplementation(libs.xmlunit)
  testImplementation(libs.apache.commonsLang)
  testRuntimeOnly(libs.junit.platformLauncher)

  funcTestImplementation(libs.spock) {
    exclude(group = "org.codehaus.groovy")
    exclude(group = "org.hamcrest")
  }
  funcTestImplementation(sourceSets.main.get().output)
  funcTestImplementation(intiTest.output)

  intiTestImplementation(libs.okio)
  intiTestImplementation(libs.apache.maven.modelBuilder)
  intiTestImplementation(libs.apache.maven.repositoryMetadata)
  // TODO: this will be removed after we migrated all functional tests to Kotlin.
  intiTestImplementation(sourceSets.main.get().output)

  lintChecks(libs.androidx.gradlePluginLints)
  lintChecks(libs.assertk.lint)
}

val integrationTest by tasks.registering(Test::class) {
  description = "Runs the integration tests."
  group = LifecycleBasePlugin.VERIFICATION_GROUP
  testClassesDirs = intiTest.output.classesDirs
  classpath = intiTest.runtimeClasspath

  // TODO: this should be moved into functionalTest after we migrated all functional tests to Kotlin.
  // Required to enable `IssueExtension` for all tests.
  systemProperty("junit.jupiter.extensions.autodetection.enabled", true)

  val docsDir = file("src/docs")
  // Add src/docs as an input directory to trigger ManualCodeSnippetTests re-run on changes.
  inputs.dir(docsDir)
  systemProperty("DOCS_DIR", docsDir.absolutePath)
}

val functionalTest by tasks.registering(Test::class) {
  description = "Runs the functional tests."
  group = LifecycleBasePlugin.VERIFICATION_GROUP
  testClassesDirs = funcTest.output.classesDirs
  classpath = funcTest.runtimeClasspath
}

tasks.check {
  dependsOn(integrationTest, functionalTest)
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  maxParallelForks = Runtime.getRuntime().availableProcessors()

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

tasks.whenTaskAdded {
  if (name == "lintAnalyzeJvmTest") {
    // This task often fails on Windows CI devices.
    enabled = !providers.systemProperty("os.name").get().startsWith("Windows") &&
      !providers.environmentVariable("CI").isPresent
  }
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
