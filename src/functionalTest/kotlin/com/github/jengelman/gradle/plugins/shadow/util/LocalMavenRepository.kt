package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.BasePluginTest.Companion.createEmptyClassBytes
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory

fun createDefaultLocalMavenRepository(junitJar: Path): AppendableMavenRepository {
  return AppendableMavenRepository(
      root = createTempDirectory().resolve("local-maven-repo").createDirectories()
    )
    .apply {
      jarModule("junit", "junit", "3.8.2") { useJar(junitJar) }
      val a =
        jarModule("my", "a", "1.0") {
          buildJar {
            insert("a.properties", "a")
            insert("a2.properties", "a2")
          }
        }
      val b = jarModule("my", "b", "1.0") { buildJar { insert("b.properties", "b") } }
      val c = jarModule("my", "c", "1.0") { buildJar { insert("c.properties", "c") } }
      val d =
        jarModule("my", "d", "1.0") {
          buildJar { insert("d.properties", "d") }
          // Depends on c but c does not depend on d.
          addDependency(c)
        }
      val e =
        jarModule("my", "e", "1.0") {
          buildJar { insert("e.properties", "e") }
          // Circular dependency with f.
          addDependency("my:f:1.0")
        }
      val f =
        jarModule("my", "f", "1.0") {
          buildJar { insert("f.properties", "f") }
          // Circular dependency with e.
          addDependency(e)
        }
      val g =
        jarModule("my", "g", "1.0") {
          buildJar { insert("g/G.class", createEmptyClassBytes("g/G")) }
          buildSourcesJar {
            insert(
              "g/G.java",
              """
              |package g;
              |public class G {}
              """
                .trimMargin(),
            )
          }
        }
      val h =
        jarModule("my", "h", "1.0") {
          buildJar {
            insert("h/H.class", createEmptyClassBytes("h/H"))
            insert("h/UnusedH.class", createEmptyClassBytes("h/UnusedH"))
          }
          buildSourcesJar {
            insert(
              "h/H.java",
              """
              |package h;
              |public class H {}
              """
                .trimMargin(),
            )
            insert(
              "h/UnusedH.java",
              """
              |package h;
              |public class UnusedH {}
              """
                .trimMargin(),
            )
          }
        }
      val k =
        jarModule("my", "k", "1.0") {
          buildJar {
            insert("k/CustomUtils.class", createEmptyClassBytes("k/CustomUtils", "Utils.kt"))
            insert(
              "k/CustomUnusedUtils.class",
              createEmptyClassBytes("k/CustomUnusedUtils", "UnusedUtils.kt"),
            )
          }
          buildSourcesJar {
            insert(
              "k/Utils.kt",
              """
              |@file:JvmName("CustomUtils")
              |package k
              |fun util() {}
              """
                .trimMargin(),
            )
            insert(
              "k/UnusedUtils.kt",
              """
              |@file:JvmName("CustomUnusedUtils")
              |package k
              |fun unusedUtil() {}
              """
                .trimMargin(),
            )
          }
        }
      bomModule("my", "bom", "1.0") {
        addDependency(a)
        addDependency(b)
        addDependency(c)
        addDependency(d)
        addDependency(e)
        addDependency(f)
        addDependency(g)
        addDependency(h)
        addDependency(k)
      }
    }
}
