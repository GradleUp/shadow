Gradle Shadow
=============

Shadow is a port of the Maven Shade plugin to the Gradle framework and Groovy. Where possible, the original
Shade code has been retained except for porting the files from Java to Groovy. Additionally, test cases included
in the Shade plugin have been retained where possible.

Not all of Shade's features are implemented within Shadow (although the code maybe be ported). Please see the Feature
Backlog below.

Current Status
=============

Latest Release: 0.8 (Released 1/3/2014)

[![Build Status](https://drone.io/github.com/johnrengelman/shadow/status.png)](https://drone.io/github.com/johnrengelman/shadow/latest)

How to use
=============

+ Apply the plugin to your Gradle build file

        buildscript {
            repositories {
                maven {
                    jcenter()
                }
            }
            dependencies {
                classpath 'com.github.jengelman.gradle.plugins:shadow:0.8'
            }
        }

        apply plugin: 'shadow'

+ Configure Shadow using the 'shadow' keyword in your Gradle build file. For example, you probably want to exclude
jar signature files

        shadow {
            exclude 'META-INF/*.DSA'
            exclude 'META-INF/*.RSA'
        }

+ Call the Shadow task

        $ gradle shadowJar

+ The shadow artifact will be created in your configured build directory (by default: build/distributions/<project>-<version>-shadow.jar

Configuration Options
=====================

+ destinationDir - configures the output directory for shadow. Default: $buildDir/libs/
+ baseName - configures the base name of the output file. Default: ${archivesBaseName}-${version}-${classifier}
+ classifier - the classifier the append to the artifact. Default: shadow
+ extension - configures the extension of the output file. Default: jar
+ stats - enables/disables output of statistics for Shadow. Useful for analyzing performance. Default: false
+ artifactAttached - if true, keep original jar; else overwrite the default artifact. Default: true
+ groupFilter - configured the inclusion of only specific artifacts to the shadow. Default: * (all artifacts)
+ outputFile - configures a specific file as output for shadow. If set, overrides all naming configurations. Default: not configured

Extensions
==========
+ Transformers - apply a transformer class to the processing

        import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
        shadow {
            transformer(AppendingTransformer) {
                resource = 'META-INF/spring.handlers'
            }
            transformer(AppendingTransformer) {
                resource = 'META-INF/spring.schemas'
            }
        }

+ Artifact Set - specify the included/excluded artifacts (this includes or excludes specific jars)

        shadow {
            artifactSet {
                include 'org.apache.maven.its.shade.aie'
                exclude '*:b:jar:'
            }
        }

+ Filters - filter contents of shadow jar by dependency

        shadow {
            filter('org.apache.maven.its.shade.fac:a') {
                include '**/a.properties'
            }
            filter('org.apache.maven.its.shade.fac:b:client') {
                exclude 'org/apache/*'
                exclude 'org/apache/maven/b/'
            }
            filter('*:*') {
                exclude 'org/*'
            }
        }

        OR SHORTHAND

        shadow {
            include 'META-INF/MANIFEST.MF'
            exclude 'META-INF/*.RSA'
        }

+ Relocators - relocate class from one package to another

        shadow {
            relocation {
                pattern = 'junit.textui'
                shadedPattern = 'a'
                excludes = ['junit.textui.TestRunner']
            }
        }


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
            foo-1.0-shadow.jar
            signedLibs
                bcprov-jdk15on-1.47.jar

Good Things To Know
===================

The default implementation excludes all META-INF/INDEX.LIST files.

Version History
===============

+ v0.8
   + Changed Maven Group ID to com.github.jengelman.gradle.plugins
   + Published artifact to JCenter
   + Upgraded to Gradle 1.10
   + Main task renamed to be 'shadowJar' instead of 'shadow'. This was done so the task and extension namespace
     did not collide.
   + Changed default output location to be ${buildDir}/distributions instead of ${buildDir}/libs
   + Added support for class Relocation, thanks to [Baron Roberts](https://github.com/baron1405)
+ v0.7.4 - upgrade to Gradle 1.6 internally and remove use of deprecated methods.
+ v0.7.3 - fix bad method call in the AppendingTransformer
+ v0.7.2 - fix a bug that was preventing multiple includes/excludes in the artifactSet. Fix bug in filtering
shorthand style that caused filters to not be applied.
+ v0.7.1 - fix the up-to-date bug where the shadow task wasn't executing after making a source change. Changed the
BinTray repo to Maven compatabile instead of Ivy.
+ v0.7 - all the v0.6 features, but using a port of the Shade code. Primarily this involves using a port
of the DefaultShader class instead of the from scratch implementation used in v0.6. This will allow for integration of
more of Shade's features with minor changes.
   + Includes support for SimpleFilter
   + Includes support for Transformers: ApacheLicenseResourceTransformer, ApacheNoticeResourceTransformer,
   AppendingTransformer, ComponentsXmlResourceTransformer, DontIncludeResourceTransformer, IncludeResourceTransformer,
   ManifestResourceTransformer, ServiceFileTransformer, XmlAppendingTransfomer
+ v0.6 - first release, mostly written from scratch using Shade code as reference.
+ v0.5 and earlier - incremental internal releases.

Feature Backlog
===============
+ Port support for configuration of a custom Caster (Shader) implementation
+ Automatically configure Shadow output as publish artifact
+ Port support for generation of shadow sources jar
+ Port support for minijar filter
