import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  kotlin("jvm") version "2.0.21"
  groovy // Required for Spock tests.
  id("shadow.convention.publish")
  id("shadow.convention.deploy")
  id("com.diffplug.spotless") version "7.0.0.BETA4"
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

spotless {
  kotlin {
    ktlint()
  }
  kotlinGradle {
    ktlint()
    target("**/*.kts")
    targetExclude("build-logic/build/**")
  }
}

dependencies {
  implementation("org.jdom:jdom2:2.0.6.1")
  implementation("org.ow2.asm:asm-commons:9.7.1")
  implementation("commons-io:commons-io:2.18.0")
  implementation("org.apache.ant:ant:1.10.15")
  implementation("org.codehaus.plexus:plexus-utils:4.0.2")
  implementation("org.codehaus.plexus:plexus-xml:4.0.4")
  implementation("org.apache.logging.log4j:log4j-core:2.24.1")
  implementation("org.vafer:jdependency:2.11")

  testImplementation("org.spockframework:spock-core:2.3-groovy-3.0") {
    exclude(group = "org.codehaus.groovy")
    exclude(group = "org.hamcrest")
  }
  testImplementation("org.xmlunit:xmlunit-legacy:2.10.0")
  testImplementation("org.apache.commons:commons-lang3:3.17.0")
  testImplementation("com.google.guava:guava:33.3.1-jre")
  testImplementation(platform("org.junit:junit-bom:5.11.3"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.junit.platform:junit-platform-suite-engine")
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
