package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import com.github.jengelman.gradle.plugins.shadow.util.containsAtLeast
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class SpringFileTransformerTest : BaseTransformerTest() {
  @Test
  fun mergeSpringFactoriesWithRelocation() {
    writeClass()

    // Create first JAR with spring.factories
    val jarA = createJar("jarA") {
      writeText(
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
    val jarB = createJar("jarB") {
      writeText(
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
          implementation files('${jarA.toAbsolutePath()}')
          implementation files('${jarB.toAbsolutePath()}')
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

      val springFactoriesContent = getContent("META-INF/spring.factories")
      // Should contain merged and relocated configurations
      assertThat(springFactoriesContent).contains("com.relocated.example.autoconfigure.ConfigA")
      assertThat(springFactoriesContent).contains("com.relocated.example.autoconfigure.ConfigB")
      assertThat(springFactoriesContent).contains("com.relocated.example.autoconfigure.AnotherConfigA")
      assertThat(springFactoriesContent).contains("com.relocated.example.initializer.InitializerA")
      assertThat(springFactoriesContent).contains("com.relocated.example.listener.ListenerB")
    }
  }

  @Test
  fun mergeSpringHandlersAndSchemasWithRelocation() {
    writeClass()

    // Create JAR with spring.handlers and spring.schemas
    val jar = createJar("spring-jar") {
      writeText(
        "META-INF/spring.handlers",
        """
        http\://www.example.com/schema/config=com.example.schema.ConfigNamespaceHandler
        http\://www.example.com/schema/security=com.example.schema.SecurityNamespaceHandler
        """.trimIndent(),
      )

      writeText(
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
          implementation files('${jar.toAbsolutePath()}')
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

      val handlersContent = getContent("META-INF/spring.handlers")
      // Handlers should have relocated class names
      assertThat(handlersContent).contains("com.relocated.example.schema.ConfigNamespaceHandler")
      assertThat(handlersContent).contains("com.relocated.example.schema.SecurityNamespaceHandler")

      val schemasContent = getContent("META-INF/spring.schemas")
      // Schemas should NOT have relocated paths
      assertThat(schemasContent).contains("META-INF/config.xsd")
      assertThat(schemasContent).contains("META-INF/security.xsd")
    }
  }

  @Test
  fun mergeSpringAutoconfigureMetadataWithRelocation() {
    writeClass()

    val jar = createJar("autoconfigure-jar") {
      writeText(
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
          implementation files('${jar.toAbsolutePath()}')
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

      val metadataContent = getContent("META-INF/spring-autoconfigure-metadata.properties")
      // All class names should be relocated
      assertThat(metadataContent).contains("com.relocated.example.autoconfigure.MyAutoConfiguration.ConditionalOnClass=com.relocated.example.service.MyService,com.relocated.example.service.AnotherService")
      assertThat(metadataContent).contains("com.relocated.example.autoconfigure.MyAutoConfiguration.Configuration=")
      assertThat(metadataContent).contains("com.relocated.example.autoconfigure.AnotherAutoConfiguration.ConditionalOnProperty.name=my.property")
    }
  }
}
