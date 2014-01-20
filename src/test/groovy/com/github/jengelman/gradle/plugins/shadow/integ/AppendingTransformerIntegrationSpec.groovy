package com.github.jengelman.gradle.plugins.shadow.integ

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowTask
import org.gradle.StartParameter
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider
import org.gradle.api.internal.file.archive.ZipFileTree
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.MultiParentClassLoader
import org.gradle.internal.classloader.MutableURLClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.service.ServiceLocator
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.ConnectionFactory
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.DistributionFactory
import org.gradle.tooling.internal.consumer.connection.AbstractConsumerConnection
import org.gradle.tooling.internal.consumer.connection.ActionAwareConsumerConnection
import org.gradle.tooling.internal.consumer.connection.BuildActionRunnerBackedConsumerConnection
import org.gradle.tooling.internal.consumer.connection.ConnectionVersion4BackedConsumerConnection
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.consumer.connection.InternalConnectionBackedConsumerConnection
import org.gradle.tooling.internal.consumer.connection.ModelBuilderBackedConsumerConnection
import org.gradle.tooling.internal.consumer.connection.NoToolingApiConnection
import org.gradle.tooling.internal.consumer.converters.ConsumerTargetTypeProvider
import org.gradle.tooling.internal.consumer.loader.CachingToolingImplementationLoader
import org.gradle.tooling.internal.consumer.loader.SynchronizedToolingImplementationLoader
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader
import org.gradle.tooling.internal.consumer.parameters.ConsumerConnectionParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.BuildActionRunner
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.InternalBuildActionExecutor
import org.gradle.tooling.internal.protocol.InternalConnection
import org.gradle.tooling.internal.protocol.ModelBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Ignore
import spock.lang.Specification

@Ignore('Still trying to figure out how to better implement integrations tests')
class AppendingTransformerIntegrationSpec extends Specification {

    DefaultGradleConnector gradleConnector
    ProjectConnection project
    BuildLauncher build
    TmpDirTemporaryFileProvider fileProvider = new TmpDirTemporaryFileProvider()
    File projectDir
    File buildFile

    def setup() {
        projectDir = fileProvider.createTemporaryDirectory("gradle-${projectName}", 'projectDir')
        gradleConnector = createConnectorWithPlugin()
        gradleConnector.embedded(true)
        gradleConnector.forProjectDirectory(projectDir)

        project = gradleConnector.connect()
        buildFile = new File(projectDir, 'build.gradle')
        buildFile.text = """
    import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
    //import org.objectweb.asm.commons.Remapper
    //buildscript {
    //    dependencies {
    //        classpath files('${classesPath}')
    //    }
    //}
    apply plugin: ShadowPlugin
"""
        new File(projectDir, 'settings.gradle').text = "rootProject.name='${projectName}'"
        build = project.newBuild().forTasks(ShadowTask.NAME)
    }

    private GradleConnector createConnectorWithPlugin() {
        ConnectionFactory connectionFactory = new ConnectionFactory(new SynchronizedToolingImplementationLoader(new CachingToolingImplementationLoader(new PluginToolingImplementationLoader(this.class.classLoader, packages))))
        def connector = new DefaultGradleConnector(connectionFactory, new DistributionFactory(StartParameter.DEFAULT_GRADLE_USER_HOME))
        return connector
    }

    private String[] getPackages() {
        return ['com.github.jengelman.gradle.plugins.shadow']
    }

    def cleanup() {
        project.close()
    }

    String getProjectName() {
        return this.class.simpleName
    }

    String getClassesPath() {
        System.getProperty('SHADOW_CLASS_PATH', System.getProperty('user.dir') + '/build/classes/production/shadow')
    }

    def "appending files of same name"() {
        given:
        buildFile << """
    //import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer

    dependencies {
        //compile 'org.apache.maven.its.shade.at:one:0.1'
        //compile 'org.apache.maven.its.shade.at:two:0.1'
    }

    shadow {
        artifactAttached = false
        //transformer(AppendingTransformer) {
        //    resource = 'META-INF/services/org.apache.maven.Shade'
        //}
    }
"""
        when:
        build.run()

        then:
        File jar = new File(projectDir, "build/libs/$projectName}.jar")
        assert jar.exists()
        FileTree files = zipTree(jar)
        List<File> serviceFiles = files.matching {
            include {
                it.relativePath.toString() == 'META-INF/services/org.apache.maven.Shade'
            }
        }.files as List
        assert serviceFiles.size() == 1

        def text = serviceFiles.first().text
        def providers = text.split('(\r\n)|(\r)|(\n)')
        assert providers.size() == 2
        assert providers[0] == 'one # NOTE: No newline terminates this line/file'
        assert providers[1] == 'two # NOTE: No newline terminates this line/file'
    }

    FileTree zipTree(File zip) {
        new ZipFileTree(zip, fileProvider.createTemporaryDirectory("gradle-${projectName}", 'zip'))
    }

    public class PluginToolingImplementationLoader implements ToolingImplementationLoader {
        private static final Logger LOGGER = LoggerFactory.getLogger(PluginToolingImplementationLoader.class);
        private final ClassLoader classLoader;
        private final String[] allowedPackages;

        PluginToolingImplementationLoader(ClassLoader classLoader, String... allowedPackages) {
            this.allowedPackages = allowedPackages;
            this.classLoader = classLoader;
        }

        public ConsumerConnection create(Distribution distribution, ProgressLoggerFactory progressLoggerFactory, ConsumerConnectionParameters connectionParameters) {
            LOGGER.debug("Using tooling provider from {}", distribution.getDisplayName());
            ClassLoader classLoader = createImplementationClassLoader(distribution, progressLoggerFactory);
            ServiceLocator serviceLocator = new ServiceLocator(classLoader);
            try {
                org.gradle.internal.Factory<ConnectionVersion4> factory = serviceLocator.findFactory(ConnectionVersion4.class);
                if (factory == null) {
                    return new NoToolingApiConnection(distribution);
                }
                // ConnectionVersion4 is a part of the protocol and cannot be easily changed.
                ConnectionVersion4 connection = factory.create();

                ProtocolToModelAdapter adapter = new ProtocolToModelAdapter(new ConsumerTargetTypeProvider());
                ModelMapping modelMapping = new ModelMapping();

                // Adopting the connection to a refactoring friendly type that the consumer owns
                AbstractConsumerConnection adaptedConnection;
                if (connection instanceof ModelBuilder && connection instanceof InternalBuildActionExecutor) {
                    adaptedConnection = new ActionAwareConsumerConnection(connection, modelMapping, adapter);
                } else if (connection instanceof ModelBuilder) {
                    adaptedConnection = new ModelBuilderBackedConsumerConnection(connection, modelMapping, adapter);
                } else if (connection instanceof BuildActionRunner) {
                    adaptedConnection = new BuildActionRunnerBackedConsumerConnection(connection, modelMapping, adapter);
                } else if (connection instanceof InternalConnection) {
                    adaptedConnection = new InternalConnectionBackedConsumerConnection(connection, modelMapping, adapter);
                } else {
                    adaptedConnection = new ConnectionVersion4BackedConsumerConnection(connection, modelMapping, adapter);
                }
                adaptedConnection.configure(connectionParameters);
                return adaptedConnection;
            } catch (UnsupportedVersionException e) {
                throw e;
            } catch (Throwable t) {
                throw new GradleConnectionException(String.format("Could not create an instance of Tooling API implementation using the specified %s.", distribution.getDisplayName()), t);
            }
        }

        private ClassLoader createImplementationClassLoader(Distribution distribution, ProgressLoggerFactory progressLoggerFactory) {
            ClassPath implementationClasspath = distribution.getToolingImplementationClasspath(progressLoggerFactory);
            LOGGER.debug("Using tooling provider classpath: {}", implementationClasspath);
            // On IBM JVM 5, ClassLoader.getResources() uses a combination of findResources() and getParent() and traverses the hierarchy rather than just calling getResources()
            // Wrap our real classloader in one that hides the parent.
            // TODO - move this into FilteringClassLoader
            MultiParentClassLoader parentObfuscatingClassLoader = new MultiParentClassLoader(classLoader);
            FilteringClassLoader filteringClassLoader = new FilteringClassLoader(parentObfuscatingClassLoader);
            filteringClassLoader.allowPackage("org.gradle.tooling.internal.protocol");
            allowedPackages.each {
                filteringClassLoader.allowPackage(it)
            }
            return new MutableURLClassLoader(filteringClassLoader, implementationClasspath.getAsURLArray()) {
                @Override
                public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    //TODO:ADAM - remove this.
                    allowedPackages.each {
                        if (name.startsWith(it)) {
                            System.out.println("loading allowed package");
                        }
                    }
                    if (name.startsWith("com.sun.jdi.")) {
                        System.out.println(String.format("=> Loading JDI class %s in provider ClassLoader. Should not be.", name));
                    }
                    return super.loadClass(name, resolve);
                }
            };
        }
    }
}
