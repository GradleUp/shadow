package com.github.jengelman.gradle.plugins.shadow.util

import java.io.OutputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder

val testObjectFactory: ObjectFactory = ProjectBuilder.builder().build().objects

val NO_OP_HANDLER = InvocationHandler { _, _, _ -> }

inline fun <reified T : Any> noOpDelegate(): T {
  val javaClass = T::class.java
  return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(javaClass), NO_OP_HANDLER) as T
}

fun OutputStream.zipOutputStream(): ZipOutputStream {
  return this as? ZipOutputStream ?: ZipOutputStream(this)
}
