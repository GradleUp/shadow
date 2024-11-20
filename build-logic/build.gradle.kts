plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation("com.gradle.publish:plugin-publish-plugin:1.3.0")
  implementation("com.vanniktech:gradle-maven-publish-plugin:0.30.0")
  implementation("org.jetbrains.dokka:org.jetbrains.dokka.gradle.plugin:1.9.20")
  implementation("org.ajoberstar.git-publish:gradle-git-publish:4.2.2")
  implementation("com.github.node-gradle:gradle-node-plugin:7.1.0")
}
