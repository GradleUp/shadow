package com.github.jengelman.gradle.plugins.shadow.internal

class DefaultR8Configuration implements R8Configuration {

    private final Collection<String> additionalRules = new ArrayList<>();

    @Override
    Collection<String> getRules() {
        return additionalRules
    }

    @Override
    void rule(String rule) {
        additionalRules.add(rule)
    }
}
