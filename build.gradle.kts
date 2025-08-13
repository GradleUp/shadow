import org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.JAVADOC_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.SOURCES_ELEMENTS_CONFIGURATION_NAME

plugins {
  groovy
  `java-gradle-plugin`
  id("com.gradle.plugin-publish") version "1.3.1"
  id("com.vanniktech.maven.publish") version "0.32.0"
  id("com.diffplug.spotless") version "7.0.4"
}

version = providers.gradleProperty("VERSION_NAME").get()
group = providers.gradleProperty("GROUP").get()
description = providers.gradleProperty("POM_DESCRIPTION").get()

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  withSourcesJar()
  withJavadocJar()
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
}

spotless {
  kotlinGradle {
    ktlint()
    target("**/*.kts")
    targetExclude("build-logic/build/**")
  }
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

dependencies {
  api("org.apache.ant:ant:1.10.15") // Types from Ant are exposed in the public API.
  implementation("org.jdom:jdom2:2.0.6.1")
  implementation("org.ow2.asm:asm-commons:9.8")
  implementation("commons-io:commons-io:2.19.0")
  implementation("org.codehaus.plexus:plexus-utils:4.0.2")
  implementation("org.codehaus.plexus:plexus-xml:4.1.0")
  implementation("org.apache.logging.log4j:log4j-core:2.24.1")
  implementation("org.vafer:jdependency:2.13")

  testImplementation("org.spockframework:spock-core:2.3-groovy-4.0") {
    exclude(group = "org.codehaus.groovy")
    exclude(group = "org.hamcrest")
  }
  testImplementation("org.xmlunit:xmlunit-legacy:2.10.2")
  testImplementation("org.apache.commons:commons-lang3:3.17.0")
  testImplementation("com.google.guava:guava:33.3.1-jre")
  testImplementation(platform("org.junit:junit-bom:5.13.1"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.junit.platform:junit-platform-suite-engine")
}

tasks.withType<Javadoc>().configureEach {
  (options as? StandardJavadocDocletOptions)?.let {
    it.links(
      "https://docs.oracle.com/en/java/javase/17/docs/api/",
      "https://docs.groovy-lang.org/2.4.7/html/gapi/",
    )
    it.addStringOption("Xdoclint:none", "-quiet")
  }
}

val isCI = providers.environmentVariable("CI").isPresent

tasks.withType<Test>().configureEach {
  useJUnitPlatform()

  val testGradleVersion = providers.gradleProperty("testGradleVersion").orNull.let {
    if (it == null || it == "current") GradleVersion.current().version else it
  }
  logger.info("Using test Gradle version: $testGradleVersion")
  systemProperty("TEST_GRADLE_VERSION", testGradleVersion)

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
