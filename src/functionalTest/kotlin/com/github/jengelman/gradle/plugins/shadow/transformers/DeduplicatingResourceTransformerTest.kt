package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsSubList
import assertk.assertions.endsWith
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import com.github.jengelman.gradle.plugins.shadow.testkit.containsExactlyInAnyOrder
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.getContents
import kotlin.booleanArrayOf
import kotlin.io.path.appendText
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DeduplicatingResourceTransformerTest : BaseTransformerTest() {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun conflictExclusion(excludeAll: Boolean) {
    val one = buildJarOne {
      insert("multiple-contents", "content")
      insert("single-source", "content")
      insert("same-content-twice", "content")
      insert("differing-content-2", "content")
    }
    val two = buildJarTwo {
      insert("multiple-contents", "content-is-different")
      insert("same-content-twice", "content")
      insert("differing-content-2", "content-is-different")
    }

    projectScript.appendText(
      transform<DeduplicatingResourceTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          exclude("multiple-contents")
          ${if (excludeAll) "exclude(\"differing-content-2\")" else ""}
        """.trimIndent(),
      ),
    )

    if (excludeAll) {
      runWithSuccess(shadowJarPath)
      assertThat(outputShadowedJar).useAll {
        containsExactlyInAnyOrder(
          // twice:
          "multiple-contents",
          "multiple-contents",
          "single-source",
          "same-content-twice",
          // twice:
          "differing-content-2",
          "differing-content-2",
          "META-INF/",
          "META-INF/MANIFEST.MF",
        )
        getContents("multiple-contents").containsExactlyInAnyOrder("content", "content-is-different")
        getContent("single-source").isEqualTo("content")
        getContent("same-content-twice").isEqualTo("content")
        getContents("differing-content-2").containsExactlyInAnyOrder("content", "content-is-different")
      }
    } else {
      val buildResult = runWithFailure(shadowJarPath)
      assertThat(buildResult.task(":shadowJar")!!.outcome).isSameInstanceAs(TaskOutcome.FAILED)
      val outputLines = buildResult.output.lines()
      assertThat(outputLines).containsSubList(
        listOf(
          // Keep this list approach for Unix/Windows test compatibility.
          "Execution failed for task ':shadowJar'.",
          "> Found 1 path duplicate(s) with different content in the shadow JAR:",
          "    * differing-content-2",
        ),
      )
      assertThat(outputLines).any {
        it.endsWith("/differing-content-2 (Hash: -1337566116240053116)")
      }
      assertThat(outputLines).any {
        it.endsWith("/differing-content-2 (Hash: -6159701213549668473)")
      }
    }
  }
}
