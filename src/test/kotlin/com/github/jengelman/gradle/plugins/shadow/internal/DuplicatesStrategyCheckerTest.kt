package com.github.jengelman.gradle.plugins.shadow.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.transformers.BaseTransformerTest.Companion.canTransformResource
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer.Companion.create
import com.github.jengelman.gradle.plugins.shadow.util.testObjectFactory
import java.io.File
import java.net.JarURLConnection
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.toPath
import kotlin.io.path.walk
import kotlin.reflect.full.isSubclassOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DuplicatesStrategyCheckerTest {
  @TempDir lateinit var tempDir: Path

  @Test
  fun checkDupStrategyInvocationCount() {
    val allResourceTransformers =
      getTransformerClasses().map {
        it.create(testObjectFactory)
      }
    assertThat(allResourceTransformers.size).isEqualTo(17)

    var invocationCount = 0
    onCheckDupStrategyInvoked = { invocationCount++ }
    try {
      allResourceTransformers.forEach {
        val file = createTempFile(directory = tempDir).toFile()
        it.canTransformResource(path = file.path, file = file)
      }
      assertThat(invocationCount).isEqualTo(14)
    } finally {
      onCheckDupStrategyInvoked = null
    }
  }
}

private fun getTransformerClasses(): List<Class<out ResourceTransformer>> {
  val packageName = "com.github.jengelman.gradle.plugins.shadow.transformers"
  val parentClass = ResourceTransformer::class
  val packagePath = packageName.replace('.', File.separatorChar)
  val classLoader =
    Thread.currentThread().contextClassLoader ?: ResourceTransformer::class.java.classLoader
  val resources = classLoader.getResources(packagePath)
  val classes = mutableListOf<Class<out ResourceTransformer>>()
  val block = { className: String ->
    runCatching {
      val clazz = Class.forName(className)
      with(clazz.kotlin) {
        if (isSubclassOf(parentClass) && !isAbstract) {
          @Suppress("UNCHECKED_CAST") classes.add(clazz as Class<out ResourceTransformer>)
        }
      }
    }
  }
  for (url in resources) {
    when (url.protocol) {
      "file" -> {
        val directory = url.toURI().toPath()
        if (directory.exists()) {
          directory
            .walk()
            .filter { it.isRegularFile() && it.extension == "class" }
            .forEach { file ->
              val relativePath = file.pathString.substringAfter(packagePath).removeSuffix(".class")
              val className = packageName + relativePath.replace(File.separatorChar, '.')
              block(className)
            }
        }
      }
      "jar" -> {
        val connection = url.openConnection() as JarURLConnection
        val jarFile = connection.jarFile
        jarFile.entries().toList().forEach {
          val name = it.name
          if (name.startsWith(packagePath) && name.endsWith(".class")) {
            val className = name.removeSuffix(".class").replace('/', '.')
            block(className)
          }
        }
      }
    }
  }
  return classes
}
