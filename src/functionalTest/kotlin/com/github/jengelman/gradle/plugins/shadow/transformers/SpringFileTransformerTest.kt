package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import com.github.jengelman.gradle.plugins.shadow.util.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class SpringFileTransformerTest : BaseTransformerTest() {
  @Test
  fun mergeSpringFactoriesWithRelocation() {
    writeClass()

    // Create first JAR with spring.factories
    val jarA = buildJar("jarA") {
      insert(
        "META-INF/spring.factories",
        """
        org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
        com.example.autoconfigure.ConfigA,\
        com.example.autoconfigure.AnotherConfigA

        org.springframework.context.ApplicationContextInitializer=\
        com.example.initializer.InitializerA
        """.trimIndent(),
      )
    }

    // Create second JAR with spring.factories
    val jarB = buildJar("jarB") {
      insert(
        "META-INF/spring.factories",
        """
        org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
        com.example.autoconfigure.ConfigB

        org.springframework.context.ApplicationListener=\
        com.example.listener.ListenerB
        """.trimIndent(),
      )
    }

    projectScriptPath.appendText(
      """
        dependencies {
          ${implementationFiles(jarA, jarB)}
        }
        $shadowJar {
          mergeSpringFiles()
          relocate('com.example', 'com.relocated.example')
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsAtLeast(
        "META-INF/spring.factories",
      )

      // Should contain merged and relocated configurations
      getContent("META-INF/spring.factories").contains(
        "com.relocated.example.autoconfigure.ConfigA",
        "com.relocated.example.autoconfigure.ConfigB",
        "com.relocated.example.autoconfigure.AnotherConfigA",
        "com.relocated.example.initializer.InitializerA",
        "com.relocated.example.listener.ListenerB",
      )
    }
  }

  @Test
  fun mergeSpringHandlersAndSchemasWithRelocation() {
    writeClass()

    // Create JAR with spring.handlers and spring.schemas
    val jar = buildJar("spring-jar") {
      insert(
        "META-INF/spring.handlers",
        """
        http\://www.example.com/schema/config=com.example.schema.ConfigNamespaceHandler
        http\://www.example.com/schema/security=com.example.schema.SecurityNamespaceHandler
        """.trimIndent(),
      )

      insert(
        "META-INF/spring.schemas",
        """
        http\://www.example.com/schema/config=META-INF/config.xsd
        http\://www.example.com/schema/security=META-INF/security.xsd
        """.trimIndent(),
      )
    }

    projectScriptPath.appendText(
      """
        dependencies {
          ${implementationFiles(jar)}
        }
        $shadowJar {
          mergeSpringFiles()
          relocate('com.example', 'com.relocated.example')
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsAtLeast(
        "META-INF/spring.handlers",
        "META-INF/spring.schemas",
      )
      // Handlers should have relocated class names
      getContent("META-INF/spring.handlers").contains(
        "com.relocated.example.schema.ConfigNamespaceHandler",
        "com.relocated.example.schema.SecurityNamespaceHandler",
      )
      // Schemas should NOT have relocated paths
      getContent("META-INF/spring.schemas").contains(
        "META-INF/config.xsd",
        "META-INF/security.xsd",
      )
    }
  }

  @Test
  fun mergeSpringAutoconfigureMetadataWithRelocation() {
    writeClass()

    val jar = buildJar("autoconfigure-jar") {
      insert(
        "META-INF/spring-autoconfigure-metadata.properties",
        """
        com.example.autoconfigure.MyAutoConfiguration.ConditionalOnClass=com.example.service.MyService,com.example.service.AnotherService
        com.example.autoconfigure.MyAutoConfiguration.Configuration=
        com.example.autoconfigure.AnotherAutoConfiguration.ConditionalOnProperty.name=my.property
        """.trimIndent(),
      )
    }

    projectScriptPath.appendText(
      """
        dependencies {
          ${implementationFiles(jar)}
        }
        $shadowJar {
          mergeSpringFiles()
          relocate('com.example', 'com.relocated.example')
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsAtLeast("META-INF/spring-autoconfigure-metadata.properties")
      // All class names should be relocated
      getContent("META-INF/spring-autoconfigure-metadata.properties").contains(
        "com.relocated.example.autoconfigure.MyAutoConfiguration.ConditionalOnClass=com.relocated.example.service.MyService,com.relocated.example.service.AnotherService",
        "com.relocated.example.autoconfigure.MyAutoConfiguration.Configuration=",
        "com.relocated.example.autoconfigure.AnotherAutoConfiguration.ConditionalOnProperty.name=my.property",
      )
    }
  }
}
