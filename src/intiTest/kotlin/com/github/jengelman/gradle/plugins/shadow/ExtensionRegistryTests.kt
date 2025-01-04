package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.IssueExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.engine.config.JupiterConfiguration
import org.junit.jupiter.engine.extension.ExtensionRegistry
import org.junit.jupiter.engine.extension.MutableExtensionRegistry
import org.mockito.Mockito
import org.mockito.Mockito.mock

/**
 * Copied from [ExtensionRegistryTests.java](https://github.com/junit-team/junit5/blob/16c6f72c1c728c015e35cb739ea75884f19f990c/jupiter-tests/src/test/java/org/junit/jupiter/engine/extension/ExtensionRegistryTests.java).
 */
class ExtensionRegistryTests {
  private val configuration: JupiterConfiguration = mock()
  private lateinit var registry: MutableExtensionRegistry

  @Test
  fun newRegistryWithoutParentHasDefaultExtensionsPlusAutodetectedExtensionsLoadedViaServiceLoader() {
    Mockito.`when`(configuration.isExtensionAutoDetectionEnabled).thenReturn(true)
    registry = MutableExtensionRegistry.createRegistryWithDefaultExtensions(configuration)

    val extensions = registry.getExtensions(Extension::class.java)

    assertEquals(NUM_DEFAULT_EXTENSIONS + 1, extensions.size)

    assertExtensionRegistered(registry, IssueExtension::class.java)
  }

  @Issue("https://github.com/GradleUp/shadow/issues/82")
  @Test
  fun testWithIssueTag() {
    val tags = IssueExtension.getTagsForTest(this::class.java, "testWithIssueTag")
    assertTrue(tags.contains("ISSUE-82"))
  }

  private fun assertExtensionRegistered(registry: ExtensionRegistry, extensionType: Class<out Extension>) {
    assertFalse(registry.getExtensions(extensionType).isEmpty()) {
      extensionType.simpleName + " should be present"
    }
  }
}

private const val NUM_DEFAULT_EXTENSIONS = 7
