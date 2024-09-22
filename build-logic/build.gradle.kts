plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation("com.gradle.publish:plugin-publish-plugin:1.3.0")
  implementation("com.vanniktech:gradle-maven-publish-plugin:0.29.0")
  implementation("org.ajoberstar.git-publish:gradle-git-publish:4.2.2")
  implementation("com.github.node-gradle:gradle-node-plugin:7.0.2")
  implementation("me.champeau.gradle:japicmp-gradle-plugin:0.4.3")
}
