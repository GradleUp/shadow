package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import org.apache.commons.lang.StringUtils

class MavenScope {
    Map<String, MavenDependency> dependencies = [:]

    void assertDependsOn(String[] expected) {
        assert dependencies.size() == expected.length
        expected.each {
            String key = StringUtils.substringBefore(it, "@")
            def dependency = expectDependency(key)

            String type = null
            if (it != key) {
                type = StringUtils.substringAfter(it, "@")
            }
            assert dependency.hasType(type)
        }
    }

    MavenDependency expectDependency(String key) {
        final dependency = dependencies[key]
        if (dependency == null) {
            throw new AssertionError("Could not find expected dependency $key. Actual: ${dependencies.values()}")
        }
        return dependency
    }
}
