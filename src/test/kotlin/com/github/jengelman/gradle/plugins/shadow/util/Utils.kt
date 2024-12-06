package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder

val testObjectFactory: ObjectFactory = ProjectBuilder.builder().build().objects

@Suppress("TestFunctionName")
fun SimpleRelocator(
  pattern: String? = null,
  shadedPattern: String? = null,
  includes: List<String>? = null,
  excludes: List<String>? = null,
  rawString: Boolean = false,
): SimpleRelocator = SimpleRelocator(
  objectFactory = testObjectFactory,
  pattern = pattern,
  shadedPattern = shadedPattern,
  includes = includes,
  excludes = excludes,
  rawString = rawString,
)
