plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(libs.pluginPublish)
  implementation(libs.mavenPublish)
  implementation(libs.jetbrains.dokka)
}
