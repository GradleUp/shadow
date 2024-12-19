plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(libs.pluginPublish)
  implementation(libs.mavenPublish)
  implementation(libs.gitPublish)
  implementation(libs.node)
  implementation(libs.jetbrains.dokka)
}
