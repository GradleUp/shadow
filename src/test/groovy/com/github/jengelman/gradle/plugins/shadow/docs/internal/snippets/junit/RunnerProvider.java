package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.junit;

import org.junit.runner.Runner;

import java.util.List;

public interface RunnerProvider {

  List<Runner> getRunners();

}
