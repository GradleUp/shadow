package com.github.jengelman.gradle.plugins.shadow

/** Restricts nested Shadow DSL blocks from accidentally calling outer receiver APIs. */
@DslMarker public annotation class ShadowDsl

@Deprecated(
  message = "Use `ShadowDsl` instead. This will be removed in Shadow 10.",
  replaceWith = ReplaceWith("ShadowDsl", "com.github.jengelman.gradle.plugins.shadow.ShadowDsl"),
  level = DeprecationLevel.ERROR,
)
@DslMarker
public annotation class ShadowDslMarker
