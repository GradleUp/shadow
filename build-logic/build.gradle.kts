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
}
