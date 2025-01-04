package com.github.jengelman.gradle.plugins.shadow.util

import org.junit.jupiter.api.Tags
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace

/**
 * This is related to [spock.lang.Issue](https://github.com/spockframework/spock/blob/master/spock-core/src/main/java/spock/lang/Issue.java) but is used for JUnit 5 tests.
 *
 * @see [Tags]
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Issue(vararg val values: String)

class IssueExtension : BeforeEachCallback {
  override fun beforeEach(context: ExtensionContext) {
    val issueAnnotation = context.requiredTestMethod.getAnnotation(Issue::class.java) ?: return
    val store = context.getStore(Namespace.create(IssueExtension::class.java, context.requiredTestClass))
    val tags = issueAnnotation.values.map(::issueLinkToTag)
    store.put("tags", tags)
    tagsMap["${context.requiredTestClass.name}#${context.requiredTestMethod.name}"] = tags
  }

  companion object {
    private val tagsMap = mutableMapOf<String, List<String>>()

    fun getTagsForTest(testClass: Class<*>, testName: String): List<String> {
      return tagsMap["${testClass.name}#$testName"] ?: emptyList()
    }
  }
}

private fun issueLinkToTag(link: String): String {
  return issueLinkRegex.replace(link) { matchResult ->
    "ISSUE-${matchResult.groupValues[1]}"
  }
}

private val issueLinkRegex = "https://github\\.com/[^/]+/[^/]+/issues/(\\d+)".toRegex()
