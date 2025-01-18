package com.github.jengelman.gradle.plugins.shadow.util

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ParameterizedTest
@ValueSource(booleans = [false, true])
annotation class BooleanParameterizedTest
