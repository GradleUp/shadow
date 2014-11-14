package com.github.jengelman.gradle.testkit

import org.gradle.testkit.functional.GradleRunner
import org.gradle.testkit.functional.internal.DefaultGradleRunner
import org.gradle.testkit.functional.internal.GradleHandle
import org.gradle.testkit.functional.internal.GradleHandleFactory
import org.gradle.testkit.functional.internal.toolingapi.BuildLauncherBackedGradleHandle
import org.gradle.testkit.functional.internal.toolingapi.ToolingApiGradleHandleFactory
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

public class GradleRunnerFactory {

    public static GradleRunner create(Closure connectorConfigureAction = null, Closure buildConfigureAction = null) {
        GradleHandleFactory toolingApiHandleFactory = new ConfigurableToolingApiGradleHandleFactory(connectorConfigureAction, buildConfigureAction);

        // TODO: Which class would be attached to the right classloader? Is using something from the test kit right?
        ClassLoader sourceClassLoader = GradleRunnerFactory.class.getClassLoader();
        GradleHandleFactory classpathInjectingHandleFactory = new ClasspathInjectingFilteringGrandleHandleFactory(sourceClassLoader, toolingApiHandleFactory);

        return new DefaultGradleRunner(classpathInjectingHandleFactory);
    }
}
