# Publishing Shadow JARs

## Publishing with Maven-Publish Plugin

The Shadow plugin will automatically configure the necessary tasks in the presence of Gradle's
`maven-publish` plugin.
The plugin provides the `component` method from the `shadow` extension to configure the
publication with the necessary artifact and dependencies in the POM file.

```groovy
// Publishing a Shadow JAR with the Maven-Publish Plugin
apply plugin: 'java'
apply plugin: 'maven-publish'
apply plugin: 'com.github.johnrengelman.shadow'

publishing {
  publications {
    shadow(MavenPublication) { publication ->
      project.shadow.component(publication)
    }
  }
  repositories {
    maven {
      url "http://repo.myorg.com"
    }
  }
}
```

## Shadow Configuration and Publishing

The Shadow plugin provides a custom configuration (`configurations.shadow`) to specify
runtime dependencies that are **not** merged into the final JAR file.
When configuring publishing with the Shadow plugin, the dependencies in the `shadow`
configuration, are translated to become `RUNTIME` scoped dependencies of the
published artifact.

No other dependencies are automatically configured for inclusion in the POM file.
For example, excluded dependencies are **not** automatically added to the POM file or
if the configuration for merging are modified by specifying
`shadowJar.configurations = [configurations.myconfiguration]`, there is no automatic
configuration of the POM file.

This automatic configuration occurs _only_ when using the above methods for
configuring publishing. If this behavior is not desirable, then publishing **must**
be manually configured.
