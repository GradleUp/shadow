package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class SpringBootTransformerTest : BaseTransformerTest() {

  @Test
  fun mergeSpringFactories() {
    val one = buildJarOne {
      insert(
        SpringBootTransformer.PATH_SPRING_FACTORIES,
        "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.example.FooAutoConfiguration",
      )
    }
    val two = buildJarTwo {
      insert(
        SpringBootTransformer.PATH_SPRING_FACTORIES,
        "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.example.BarAutoConfiguration",
      )
    }
    projectScript.appendText(
      transform<SpringBootTransformer>(dependenciesBlock = implementationFiles(one, two))
    )

    runWithSuccess(shadowJarPath)

    val content =
      outputShadowedJar.use { it.getContent(SpringBootTransformer.PATH_SPRING_FACTORIES) }
    assertThat(content)
      .isEqualTo(
        "org.springframework.boot.autoconfigure.EnableAutoConfiguration=" +
          "com.example.FooAutoConfiguration,com.example.BarAutoConfiguration\n"
      )
  }

  @Test
  fun mergeSpringFactoriesWithMultipleKeys() {
    val one = buildJarOne {
      insert(
        SpringBootTransformer.PATH_SPRING_FACTORIES,
        "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.example.FooAutoConfiguration\n" +
          "org.springframework.context.ApplicationListener=com.example.FooListener",
      )
    }
    val two = buildJarTwo {
      insert(
        SpringBootTransformer.PATH_SPRING_FACTORIES,
        "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.example.BarAutoConfiguration\n" +
          "org.springframework.context.ApplicationListener=com.example.BarListener",
      )
    }
    projectScript.appendText(
      transform<SpringBootTransformer>(dependenciesBlock = implementationFiles(one, two))
    )

    runWithSuccess(shadowJarPath)

    val content =
      outputShadowedJar.use { it.getContent(SpringBootTransformer.PATH_SPRING_FACTORIES) }
    assertThat(content)
      .isEqualTo(
        "org.springframework.boot.autoconfigure.EnableAutoConfiguration=" +
          "com.example.FooAutoConfiguration,com.example.BarAutoConfiguration\n" +
          "org.springframework.context.ApplicationListener=" +
          "com.example.FooListener,com.example.BarListener\n"
      )
  }

  @Test
  fun mergeSpringImports() {
    val one = buildJarOne {
      insert(
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
        "com.example.FooAutoConfiguration",
      )
    }
    val two = buildJarTwo {
      insert(
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
        "com.example.BarAutoConfiguration",
      )
    }
    projectScript.appendText(
      transform<SpringBootTransformer>(dependenciesBlock = implementationFiles(one, two))
    )

    runWithSuccess(shadowJarPath)

    val content =
      outputShadowedJar.use {
        it.getContent(
          "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        )
      }
    assertThat(content)
      .isEqualTo("com.example.FooAutoConfiguration\ncom.example.BarAutoConfiguration")
  }

  @Test
  fun deduplicateSpringImports() {
    val one = buildJarOne {
      insert(
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
        "com.example.FooAutoConfiguration\ncom.example.BarAutoConfiguration",
      )
    }
    val two = buildJarTwo {
      insert(
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
        "com.example.BarAutoConfiguration\ncom.example.BazAutoConfiguration",
      )
    }
    projectScript.appendText(
      transform<SpringBootTransformer>(dependenciesBlock = implementationFiles(one, two))
    )

    runWithSuccess(shadowJarPath)

    val content =
      outputShadowedJar.use {
        it.getContent(
          "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        )
      }
    assertThat(content)
      .isEqualTo(
        "com.example.FooAutoConfiguration\ncom.example.BarAutoConfiguration\ncom.example.BazAutoConfiguration"
      )
  }

  @Test
  fun mergeSpringHandlers() {
    val one = buildJarOne {
      insert(
        SpringBootTransformer.PATH_SPRING_HANDLERS,
        "http\\://www.example.com/schema/foo=com.example.FooNamespaceHandler",
      )
    }
    val two = buildJarTwo {
      insert(
        SpringBootTransformer.PATH_SPRING_HANDLERS,
        "http\\://www.example.com/schema/bar=com.example.BarNamespaceHandler",
      )
    }
    projectScript.appendText(
      transform<SpringBootTransformer>(dependenciesBlock = implementationFiles(one, two))
    )

    runWithSuccess(shadowJarPath)

    val content =
      outputShadowedJar.use { it.getContent(SpringBootTransformer.PATH_SPRING_HANDLERS) }
    assertThat(content)
      .isEqualTo(
        "http\\://www.example.com/schema/bar=com.example.BarNamespaceHandler\n" +
          "http\\://www.example.com/schema/foo=com.example.FooNamespaceHandler\n"
      )
  }

  @Test
  fun relocateClassesInSpringFactories() {
    val one = buildJarOne {
      insert(
        SpringBootTransformer.PATH_SPRING_FACTORIES,
        "com.example.SomeInterface=com.example.SomeImplementation",
      )
    }
    projectScript.appendText(
      """
      dependencies {
        ${implementationFiles(one)}
      }
      $shadowJarTask {
        transform(${SpringBootTransformer::class.java.name})
        relocate('com.example', 'shadow.example')
      }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    val content =
      outputShadowedJar.use { it.getContent(SpringBootTransformer.PATH_SPRING_FACTORIES) }
    assertThat(content)
      .isEqualTo("shadow.example.SomeInterface=shadow.example.SomeImplementation\n")
  }

  @Test
  fun relocateClassesInSpringImports() {
    val one = buildJarOne {
      insert(
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
        "com.example.FooAutoConfiguration",
      )
    }
    projectScript.appendText(
      """
      dependencies {
        ${implementationFiles(one)}
      }
      $shadowJarTask {
        transform(${SpringBootTransformer::class.java.name})
        relocate('com.example', 'shadow.example')
      }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    val content =
      outputShadowedJar.use {
        it.getContent(
          "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        )
      }
    assertThat(content).isEqualTo("shadow.example.FooAutoConfiguration")
  }
}
