@file:JvmName("Utils")

package com.github.jengelman.gradle.plugins.shadow.testkit.util

import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder

val testObjectFactory: ObjectFactory = ProjectBuilder.builder().build().objects
