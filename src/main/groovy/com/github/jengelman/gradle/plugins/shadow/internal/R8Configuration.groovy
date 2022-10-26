package com.github.jengelman.gradle.plugins.shadow.internal

interface R8Configuration {

    Collection<String> getRules()

    void rule(String rule)

    Collection<File> getConfigurationFiles()

    void configuration(File configurationFile)
}
