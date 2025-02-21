package com.github.jengelman.gradle.plugins.shadow.util

import java.io.OutputStream
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder

val testObjectFactory: ObjectFactory = ProjectBuilder.builder().build().objects

fun OutputStream.zipOutputStream(): ZipOutputStream {
  return this as? ZipOutputStream ?: ZipOutputStream(this)
}
