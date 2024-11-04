plugins {
  id("com.gradle.plugin-publish")
  id("com.vanniktech.maven.publish")
}

version = providers.gradleProperty("VERSION_NAME").get()
group = providers.gradleProperty("GROUP").get()
description = providers.gradleProperty("POM_DESCRIPTION").get()

gradlePlugin {
  website = providers.gradleProperty("POM_URL")
  vcsUrl = providers.gradleProperty("POM_URL")

  plugins {
    create("shadowPlugin") {
      id = "com.gradleup.shadow"
      implementationClass = "com.github.jengelman.gradle.plugins.shadow.ShadowPlugin"
      displayName = providers.gradleProperty("POM_NAME").get()
      description = providers.gradleProperty("POM_DESCRIPTION").get()
      tags = listOf("onejar", "shade", "fatjar", "uberjar")
    }
  }
}

tasks.publishPlugins {
  doFirst {
    if (version.toString().endsWith("SNAPSHOT")) {
      error("Cannot publish SNAPSHOT versions to Plugin Portal!")
    }
  }
  notCompatibleWithConfigurationCache("https://github.com/gradle/gradle/issues/21283")
}

tasks.withType<Javadoc>().configureEach {
  (options as? StandardJavadocDocletOptions)?.let {
    it.links(
      "https://docs.oracle.com/en/java/javase/17/docs/api/",
      "https://docs.groovy-lang.org/2.4.7/html/gapi/",
    )
    it.addStringOption("Xdoclint:none", "-quiet")
  }
}

configurations {
  listOf(
    apiElements,
    runtimeElements,
    named("javadocElements"),
    named("sourcesElements"),
  ).forEach {
    it.configure {
      outgoing {
        // Main/current capability
        capability("com.gradleup.shadow:shadow-gradle-plugin:$version")

        // Historical capabilities
        capability("io.github.goooler.shadow:shadow-gradle-plugin:$version")
        capability("com.github.johnrengelman:shadow:$version")
        capability("gradle.plugin.com.github.jengelman.gradle.plugins:shadow:$version")
        capability("gradle.plugin.com.github.johnrengelman:shadow:$version")
        capability("com.github.jengelman.gradle.plugins:shadow:$version")
      }
    }
  }
}

publishing.publications.withType<MavenPublication>().configureEach {
  // We don't care about capabilities being unmappable to Maven
  suppressPomMetadataWarningsFor("apiElements")
  suppressPomMetadataWarningsFor("runtimeElements")
  suppressPomMetadataWarningsFor("javadocElements")
  suppressPomMetadataWarningsFor("sourcesElements")
}
