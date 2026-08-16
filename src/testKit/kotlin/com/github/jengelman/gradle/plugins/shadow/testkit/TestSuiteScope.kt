package com.github.jengelman.gradle.plugins.shadow.testkit

import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuiteScope
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.reflect.KFunction1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

fun TestSuiteScope.tempDirFixture(): TestFixture<Path> =
  testFixture {
    createTempDirectory()
  } closeWith { @OptIn(ExperimentalPathApi::class) deleteRecursively() }

inline fun <reified T : Any> TestSuiteScope.runTests(
  noinline factory: () -> T,
  vararg functions: KFunction1<T, Unit>,
) {
  if (functions.isNotEmpty()) {
    for (function in functions) {
      runTest(function.name, factory, function)
    }
  } else {
    val discovered =
      T::class.declaredMemberFunctions.filter { it.visibility != KVisibility.PRIVATE }
    for (function in discovered) {
      function.isAccessible = true
      val valueParams = function.parameters.drop(1)
      when {
        valueParams.isEmpty() -> {
          runTest(function.name, factory) {
            function.call(this)
          }
        }

        valueParams.all { it.type.classifier == Boolean::class } -> {
          val combinations =
            valueParams.fold(listOf(emptyList<Boolean>())) { acc, _ ->
              acc.flatMap { list -> listOf(list + false, list + true) }
            }
          for (combo in combinations) {
            val name =
              "${function.name}_" +
                valueParams.zip(combo).joinToString("_") { (param, value) ->
                  "${param.name}_$value"
                }
            runTest(name, factory) {
              function.call(this, *combo.toTypedArray())
            }
          }
        }

        else -> {
          runTest(function.name, factory) {
            error(
              "runTests() can only auto-run no-arg tests or tests whose parameters are all Boolean. " +
                "'${function.name}' has unsupported parameters: " +
                valueParams.joinToString(", ") { "${it.name}:${it.type}" } +
                ". Use explicit runTest(...) registrations in the suite."
            )
          }
        }
      }
    }
  }
}

fun <T : Any> TestSuiteScope.runTest(
  name: String,
  factory: () -> T,
  block: T.() -> Unit,
) {
  test(name) {
    val instance = factory()
    try {
      instance.block()
    } catch (t: InvocationTargetException) {
      logProjectScript(instance)
      throw t.targetException ?: t
    } catch (t: Throwable) {
      logProjectScript(instance)
      throw t
    } finally {
      cleanupInstance(instance)
    }
  }
}

@PublishedApi
internal fun logProjectScript(instance: Any) {
  try {
    val scriptProp =
      instance::class.declaredMemberProperties.firstOrNull {
        it.name == "projectScript" && it.returnType.classifier == Path::class
      }
    if (scriptProp != null) {
      scriptProp.isAccessible = true
      val script = scriptProp.call(instance) as? Path
      val rootProp =
        instance::class.declaredMemberProperties.firstOrNull {
          it.name == "projectRoot" && it.returnType.classifier == Path::class
        }
      rootProp?.isAccessible = true
      val root = rootProp?.call(instance) as? Path
      if (script != null && script.exists()) {
        println("Project build script at $root:\n${script.readText()}")
      }
    }
  } catch (_: Throwable) {}
}

@OptIn(ExperimentalPathApi::class)
@PublishedApi
internal fun cleanupInstance(instance: Any) {
  try {
    if (instance is AutoCloseable) instance.close()
    val rootProp =
      instance::class.declaredMemberProperties.firstOrNull {
        it.name == "projectRoot" && it.returnType.classifier == Path::class
      }
    if (rootProp != null) {
      rootProp.isAccessible = true
      (rootProp.call(instance) as? Path)?.deleteRecursively()
    }
    val tempDirProp =
      instance::class.declaredMemberProperties.firstOrNull {
        it.name == "tempDir" && it.returnType.classifier == Path::class
      }
    if (tempDirProp != null) {
      tempDirProp.isAccessible = true
      (tempDirProp.call(instance) as? Path)?.deleteRecursively()
    }
  } catch (_: Throwable) {}
}
