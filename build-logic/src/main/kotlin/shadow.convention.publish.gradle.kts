plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
    id("com.vanniktech.maven.publish")
}

version = providers.gradleProperty("VERSION_NAME").get()
group = providers.gradleProperty("GROUP").get()
description = providers.gradleProperty("POM_DESCRIPTION").get()

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

gradlePlugin {
    website = providers.gradleProperty("POM_URL")
    vcsUrl = providers.gradleProperty("POM_URL")

    plugins {
        create("shadowPlugin") {
            id = "me.champeau.gradle.japicmp"
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
            "https://docs.oracle.com/javase/17/docs/api",
            "https://docs.groovy-lang.org/2.4.7/html/gapi/"
        )
        it.addStringOption("Xdoclint:none", "-quiet")
    }
}
