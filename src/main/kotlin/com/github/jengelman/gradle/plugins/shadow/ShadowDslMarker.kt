package com.github.jengelman.gradle.plugins.shadow

/** Restricts nested Shadow DSL blocks from accidentally calling outer receiver APIs. */
@DslMarker public annotation class ShadowDslMarker
