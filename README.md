Gradle Shadow
=============

Shadow is a port of the Maven Shade plugin to the Gradle framework and Groovy. Where possible, the original
Shade code has been retained except for porting the files from Java to Groovy. Additionally, test cases included
in the Shade plugin have been retained where possible.

Not all of Shade's features are implemented within Shadow (although the code maybe be ported). Please see the Feature
Backlog below.

How to use
=============

+ Apply the plugin to your Gradle build file

    buildscript {
        repositories {
            ivy {
                name 'Gradle Shadow'
                url 'http://dl.bintray.com/content/johnrengelman/gradle-plugins'
            }
        }
        dependencies {
            classpath 'org.gradle.plugins:shadow:0.7.PORT-SNAPSHOT'
        }
    }

    apply plugin: 'shadow'

+ Configure Shadow using the 'shadow' keyword in your Gradle build file. For example, you probably want to exclude
jar signature files

    shadow {
        exclude 'META-INF'/*.DSA'
        exclude 'META-INF/*.RSA'
    }

+ Call the Shadow task

    $ gradle shadow

+ The shadow artifact will be created in your configured build directory (by default: build/libs/<project>-shadow-<version>.jar

Configuration Options
=====================

+ destinationDir - configures the output directory for shadow. Default: $buildDir/libs/
+ baseName - configures the base name of the output file. Default: ${archivesBaseName}-shadow-${version}
+ extension - configures the extension of the output file. Default: jar
+ stats - enables/disables output of statistics for Shadow. Useful for analyzing performance. Default: false

Configuring Output of Signed Libraries
======================================

It may be useful to not include certain libraries in the shadow jar, but have them available in a know location for
later packaging. For example, an encryption library must remain signed for the JVM to use it as a security provider.
Since the signature files are removed by Shadow, it's not worthwile to include it in the Shadow jar. This can be
accomplished by using the 'signedCompile' and 'signedRuntime' configurations that Shadow provides for these dependencies.

When running Shadow, dependencies declared for these configurations will by copied into a 'signedLibs' folder in the
configured destination directory and exclude from the collective jar. For example, a project 'foo' has the following:

    dependencies {
        signedCompile 'org.bouncycastle:bcprov-jdk15on:1.47'
    }

Results in the following output:

    build
        libs
            foo-shadow-1.0.jar
            signedLibs
                bcprov-jdk15on-1.47.jar

Good Things To Know
===================

The default implementation excludes all META-INF/INDEX.LIST files.

Version History
===============

+ v0.7 (in progress) - all the v0.6 features, but using a port of the Shade code. Primarily this involves using a port
of the DefaultShader class instead of the from scratch implementation used in v0.6. This will allow for integration of
more of Shade's features with minor changes.
+ v0.6 - first release, mostly written from scratch using Shade code.

Feature Backlog
===============
+ Allow for configuration of transformers, relocators, and filters (expected for v0.7)
+ Allow for configuration of a custom Caster (Shader) implementation
+ Port shade integration tests (expect most for v0.7)
