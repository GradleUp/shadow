pluginManagement {
  repositories {
    mavenCentral()
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    gradlePluginPortal()
  }
}

plugins {
  id("com.gradle.develocity") version "4.0.2"
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

develocity {
  buildScan {
    termsOfUseUrl = "https://gradle.com/terms-of-service"
    termsOfUseAgree = "yes"
    // TODO: https://github.com/gradle/gradle/issues/22879
    val isCI = providers.environmentVariable("CI").isPresent
    publishing.onlyIf { isCI }
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    gradlePluginPortal()
  }
}

rootProject.name = "shadow"

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
