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

plugins { id("com.gradle.develocity") version "4.5.0" }

develocity {
  buildScan {
    termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
    termsOfUseAgree = "yes"
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

enableFeaturePreview("NO_IMPLICIT_LOOKUP_IN_PARENT_PROJECTS")

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
