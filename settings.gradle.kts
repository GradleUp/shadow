pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }

  includeBuild("build-logic")
}

plugins {
  id("com.gradle.develocity") version "3.18.1"
}

develocity {
  buildScan {
    termsOfUseUrl = "https://gradle.com/terms-of-service"
    termsOfUseAgree = "yes"
    // TODO: workaround for https://github.com/gradle/gradle/issues/22879.
    val isCI = providers.environmentVariable("CI").isPresent
    publishing.onlyIf { isCI }
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

rootProject.name = "shadow"

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":api")
