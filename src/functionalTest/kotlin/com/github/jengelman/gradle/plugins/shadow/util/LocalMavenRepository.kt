package com.github.jengelman.gradle.plugins.shadow.util

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
          buildJar { insert("g.properties", "g") }
          addDependency(pomModule("my", "pom-dep", "1.0"))
        }
      bomModule("my", "bom", "1.0") {
        addDependency(a)
        addDependency(b)
        addDependency(c)
        addDependency(d)
        addDependency(e)
        addDependency(f)
        addDependency(g)
      }
    }
}
