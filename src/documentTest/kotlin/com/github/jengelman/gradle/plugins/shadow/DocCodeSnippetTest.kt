package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.testkit.tempDirFixture
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.coroutineContext
import de.infix.testBalloon.framework.core.invocation
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.Dispatchers

val DocCodeSnippetTest by
  testSuite(
    testConfig =
      TestConfig.invocation(TestConfig.Invocation.Concurrent)
        // Each snippet runs a nested Gradle build. Two-way parallelism performed better than
        // four-way by avoiding excessive CPU, memory, and disk contention.
        .coroutineContext(Dispatchers.Default.limitedParallelism(2))
        .testScope(isEnabled = false)
  ) {
    val langExecutables = DslLang.entries.map(DslLang::extractCodeSnippets)

    check(langExecutables.sumOf { it.size } > 0) { "No code snippets found." }
    check(langExecutables.map { it.size }.distinct().size == 1) {
      "All languages must have the same number of code snippets."
    }

    tempDirFixture() asParameterForEach
      {
        for (executable in langExecutables.flatten()) {
          test(executable.displayName) { testDir -> executable.execute(testDir) }
        }
      }
  }
