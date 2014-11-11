package com.github.jengelman.gradle.testkit

import org.gradle.testkit.functional.GradleRunner
import org.gradle.testkit.functional.internal.DefaultGradleRunner
import org.gradle.testkit.functional.internal.GradleHandleFactory
import org.gradle.testkit.functional.internal.toolingapi.ToolingApiGradleHandleFactory

public class GradleRunnerFactory {

    public static GradleRunner create(Closure connectorConfigureAction) {
        GradleHandleFactory toolingApiHandleFactory = new ToolingApiGradleHandleFactory(connectorConfigureAction);

        // TODO: Which class would be attached to the right classloader? Is using something from the test kit right?
        ClassLoader sourceClassLoader = GradleRunnerFactory.class.getClassLoader();
        GradleHandleFactory classpathInjectingHandleFactory = new ClasspathInjectingFilteringGrandleHandleFactory(sourceClassLoader, toolingApiHandleFactory);

        return new DefaultGradleRunner(classpathInjectingHandleFactory);
    }

    public static GradleRunner create() {
        return create(null);
    }

}
