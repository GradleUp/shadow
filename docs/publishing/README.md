# Publishing Shadow JARs

## Publishing with Maven-Publish Plugin

The Shadow plugin will automatically configure the necessary tasks in the presence of Gradle's
`maven-publish` plugin.
The plugin provides the `shadow` component to configure the publication with the necessary
artifact and dependencies in the POM file.

    plugins {
      id 'java'
      id 'maven-publish'
      id 'com.gradleup.shadow'
    }
    
    publishing {
      publications {
        shadow(MavenPublication) {
          from(components.shadow) // or components["shadow"] in Kotlin DSL
        }
      }
      repositories {
        maven {
          url = "https://repo.myorg.com"
        }
      }
    }

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


## Publish the Shadowed JAR instead of the Original JAR

You may want to publish the shadowed JAR instead of the original JAR. This can be done by trimming 
the `archiveClassifier` of the shadowed JAR like the following:

    plugins {
      id 'java'
      id 'maven-publish'
      id 'com.gradleup.shadow'
    }
    
    group = 'shadow'
    version = '1.0'
    
    dependencies {
      // This will be bundled in the shadowed JAR and not declared in the POM.
      implementation 'some:a:1.0'
      // This will be excluded from the shadowed JAR but declared as a runtime dependency in `META-INF/MANIFEST.MF`
      // file's `Class-Path` entry, and also in the POM file.
      shadow 'some:b:1.0'
      // This will be excluded from the shadowed JAR and not declared in the POM or `META-INF/MANIFEST.MF`.
      compileOnly 'some:c:1.0'
    }
    
    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      archiveClassifier = ''
    }
    
    publishing {
      publications {
        shadow(MavenPublication) {
          from components.shadow
        }
      }
      repositories {
        maven {
          url = "https://repo.myorg.com"
        }
      }
    }


## Publish Custom ShadowJar Task Outputs

It is possible to publish a custom `ShadowJar` task's output via the [`MavenPublication.artifact(java.lang.Object)`](https://docs.gradle.org/current/dsl/org.gradle.api.publish.maven.MavenPublication.html#org.gradle.api.publish.maven.MavenPublication:artifact(java.lang.Object)) method. 

    plugins {
      id 'java'
      id 'maven-publish'
      id 'com.gradleup.shadow'
    }
    
    def testShadowJar = tasks.register('testShadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      group = com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.GROUP_NAME
      description = "Create a combined JAR of project and test dependencies"
      archiveClassifier = "tests"
      from sourceSets.test.output
      configurations = [project.configurations.testRuntimeClasspath]
    }
    
    dependencies {
      testImplementation 'junit:junit:3.8.2'
    }
    
    publishing {
      publications {
        shadow(MavenPublication) {
          artifact(testShadowJar)
        }
      }
      repositories {
        maven {
          url = "https://repo.myorg.com"
        }
      }
    }
