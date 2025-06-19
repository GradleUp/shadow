plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation("com.gradle.publish:plugin-publish-plugin:1.3.1")
  implementation("com.vanniktech:gradle-maven-publish-plugin:0.32.0")
}
