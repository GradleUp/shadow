pluginManagement {
  repositories {
    mavenLocal()
    gradlePluginPortal()
  }

  includeBuild("..")
}
include(":app", ":lib")
