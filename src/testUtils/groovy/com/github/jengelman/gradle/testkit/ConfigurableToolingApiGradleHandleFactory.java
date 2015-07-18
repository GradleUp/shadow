package com.github.jengelman.gradle.testkit;

import groovy.lang.Closure;
import org.gradle.testkit.functional.internal.GradleHandle;
import org.gradle.testkit.functional.internal.GradleHandleFactory;
import org.gradle.testkit.functional.internal.toolingapi.BuildLauncherBackedGradleHandle;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import java.io.File;
import java.util.List;

public class ConfigurableToolingApiGradleHandleFactory implements GradleHandleFactory {

    private final Closure connectorConfigureAction;
    private final Closure buildConfigureAction;

    private ProjectConnection connection;

    public ConfigurableToolingApiGradleHandleFactory() {
        this(null);
    }

    public ConfigurableToolingApiGradleHandleFactory(Closure connectorConfigureAction) {
        this(connectorConfigureAction, null);
    }

    public ConfigurableToolingApiGradleHandleFactory(Closure connectorConfigureAction, Closure buildConfigureAction) {
        this.connectorConfigureAction = connectorConfigureAction;
        this.buildConfigureAction = buildConfigureAction;
    }

    public GradleHandle start(File directory, List<String> arguments) {
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(directory);
        configureConnector(connector);
        connection = connector.connect();
        BuildLauncher launcher = connection.newBuild();
        configureBuild(launcher);
        String[] argumentArray = new String[arguments.size()];
        arguments.toArray(argumentArray);
        launcher.withArguments(argumentArray);
        return new BuildLauncherBackedGradleHandle(launcher);
    }

    @Override
    public void close() {
        if (connection != null) {
            connection.close();
        }
    }

    private void configureConnector(GradleConnector connector) {
        if (connectorConfigureAction != null) {
            connectorConfigureAction.setDelegate(connector);
            connectorConfigureAction.call(connector);
        }
    }

    private void configureBuild(BuildLauncher launcher) {
        if (buildConfigureAction != null) {
            buildConfigureAction.setDelegate(launcher);
            buildConfigureAction.call(launcher);
        }
    }
}
