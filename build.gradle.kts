import org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.JAVADOC_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.SOURCES_ELEMENTS_CONFIGURATION_NAME
import org.gradle.plugin.compatibility.compatibility

plugins {
  groovy
  `java-gradle-plugin`
  alias(libs.plugins.pluginPublish)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.spotless)
}

version = providers.gradleProperty("VERSION_NAME").get()
group = providers.gradleProperty("GROUP").get()
description = providers.gradleProperty("POM_DESCRIPTION").get()

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

gradlePlugin {
  website = providers.gradleProperty("POM_URL")
  vcsUrl = providers.gradleProperty("POM_URL")

  plugins {
    create("com.gradleup.shadow") {
      implementationClass = "com.github.jengelman.gradle.plugins.shadow.ShadowPlugin"
      displayName = providers.gradleProperty("POM_NAME").get()
      description = providers.gradleProperty("POM_DESCRIPTION").get()
      tags = listOf("onejar", "shade", "fatjar", "uberjar")
      compatibility { features { configurationCache = true } }
    }
  }
}

spotless {
  kotlinGradle {
    ktlint()
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
  api(libs.apache.ant) // Types from Ant are exposed in the public API.
  implementation(libs.commons.io)
  implementation(libs.jdom2)
  implementation(libs.plexus.utils)
  implementation(libs.plexus.xml)
  implementation(libs.apache.log4j)
  implementation(libs.jdependency)

  testImplementation("org.spockframework:spock-core:2.4-groovy-4.0") {
    exclude(group = "org.codehaus.groovy")
    exclude(group = "org.hamcrest")
  }
  testImplementation(libs.xmlunit)
  testImplementation("org.apache.commons:commons-lang3:3.17.0")
  testImplementation("com.google.guava:guava:33.3.1-jre")
  testImplementation(platform(libs.junit.bom))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.junit.platform:junit-platform-suite-engine")
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()

  val testGradleVersion = providers.gradleProperty("testGradleVersion").orNull.let {
    if (it == null || it == "current") GradleVersion.current().version else it
  }
  logger.lifecycle("Using test Gradle version: $testGradleVersion")
  systemProperty("TEST_GRADLE_VERSION", testGradleVersion)

  // Required to test configuration cache in tests when using withDebug()
  // https://github.com/gradle/gradle/issues/22765#issuecomment-1339427241
  jvmArgs(
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
    "--add-opens=java.base/java.net=ALL-UNNAMED",
  )
}
