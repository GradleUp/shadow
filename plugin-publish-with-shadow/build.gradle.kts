plugins {
  `java-gradle-plugin`
  id("com.gradle.plugin-publish")
  id("com.gradleup.shadow") version "9.0.0-beta4"
}

group = "com.my.plugin"
version = "1.0.0"

dependencies {
  implementation("com.google.code.gson:gson:2.11.0")
  shadow("com.squareup.moshi:moshi:1.15.2")
}

tasks.shadowJar {
  archiveClassifier = ""
}

gradlePlugin {
  plugins {
    create("myPlugin") {
      id = "com.my.plugin"
      implementationClass = "com.my.plugin.MyPlugin"
    }
  }
}

publishing {
  publications {
    create<MavenPublication>("pluginMaven") {
      artifactId = "my-gradle-plugin"
    }
  }
  repositories {
    maven(layout.buildDirectory.dir("maven-repo"))
  }
}
