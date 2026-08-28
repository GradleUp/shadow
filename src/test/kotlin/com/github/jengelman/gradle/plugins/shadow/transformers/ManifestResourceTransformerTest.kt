package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.testkit.crlfEolString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.jar.Attributes
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.Manifest
import org.junit.jupiter.api.Test

/**
 * Modified from
 * [org.apache.maven.plugins.shade.resource.ManifestResourceTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ManifestResourceTransformerTest.java).
 */
class ManifestResourceTransformerTest : BaseTransformerTest<ManifestResourceTransformer>() {

  @Test
  fun canTransformResource() =
    with(transformer) {
      assertThat(canTransformResource(MANIFEST_NAME)).isTrue()
      assertThat(canTransformResource(MANIFEST_NAME.lowercase())).isTrue()
      assertThat(canTransformResource("META-INF/OTHER.MF")).isFalse()
    }

  @Test
  fun hasTransformedResource() =
    with(transformer) {
      assertThat(hasTransformedResource()).isTrue()
    }

  @Test
  fun mainClassAndManifestEntries() =
    with(transformer) {
      mainClass.set("com.example.Main")
      manifestEntries.put("Custom-Key", "CustomValue")

      transform(textContext(MANIFEST_NAME, "Manifest-Version: 1.0\r\n\r\n"))

      transformToJar().use { jarPath ->
        assertThat(jarPath.getMainAttr("Main-Class")).isEqualTo("com.example.Main")
        assertThat(jarPath.getMainAttr("Custom-Key")).isEqualTo("CustomValue")
      }
    }

  @Test
  fun rewriteDefaultAttributes() =
    with(transformer) {
      val relocator = SimpleRelocator("javax", "jakarta")

      transform(context(testManifest, relocator))

      transformToJar().use { jarPath ->
        assertThat(jarPath.getMainAttr("Export-Package"))
          .isEqualTo(
            "jakarta.decorator;version=\"2.0\";uses:=\"jakarta.enterprise.inject\"," +
              "jakarta.enterprise.context;version=\"2.0\";uses:=\"jakarta.enterprise.util,jakarta.inject\""
          )
        assertThat(jarPath.getMainAttr("Import-Package"))
          .isEqualTo("jakarta.el,jakarta.enterprise.context;version=\"[2.0,3)\"")
        assertThat(jarPath.getMainAttr("Provide-Capability"))
          .isEqualTo(
            "osgi.contract;osgi.contract=JavaCDI;" +
              "uses:=\"jakarta.enterprise.context,jakarta.enterprise.context.spi,jakarta.enterprise.context.control," +
              "jakarta.enterprise.util,jakarta.enterprise.inject,jakarta.enterprise.inject.spi," +
              "jakarta.enterprise.inject.spi.configurator,jakarta.enterprise.inject.literal," +
              "jakarta.enterprise.inject.se,jakarta.enterprise.event," +
              "jakarta.decorator\";version:List<Version>=\"2.0,1.2,1.1,1.0\""
          )
        assertThat(jarPath.getMainAttr("Require-Capability"))
          .isEqualTo(
            "osgi.serviceloader;" +
              "filter:=\"(osgi.serviceloader=jakarta.enterprise.inject.se.SeContainerInitializer)\";" +
              "cardinality:=multiple,osgi.serviceloader;" +
              "filter:=\"(osgi.serviceloader=jakarta.enterprise.inject.spi.CDIProvider)\";" +
              "cardinality:=multiple,osgi.extender;" +
              "filter:=\"(osgi.extender=osgi.serviceloader.processor)\"," +
              "osgi.contract;osgi.contract=JavaEL;filter:=\"(&(osgi.contract=JavaEL)(version=2.2.0))\"," +
              "osgi.contract;osgi.contract=JavaInterceptor;" +
              "filter:=\"(&(osgi.contract=JavaInterceptor)(version=1.2.0))\"," +
              "osgi.contract;osgi.contract=JavaInject;" +
              "filter:=\"(&(osgi.contract=JavaInject)(version=1.0.0))\"," +
              "osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\""
          )
      }
    }

  @Test
  fun rewriteDefaultAttributesWithSameSuffix() =
    with(transformer) {
      val relocator = SimpleRelocator("javax", "shaded.javax")

      transform(context(testManifest, relocator))

      transformToJar().use { jarPath ->
        assertThat(jarPath.getMainAttr("Export-Package"))
          .isEqualTo(
            "shaded.javax.decorator;version=\"2.0\";uses:=\"shaded.javax.enterprise.inject\"," +
              "shaded.javax.enterprise.context;version=\"2.0\";uses:=\"shaded.javax.enterprise.util,shaded.javax.inject\""
          )
        assertThat(jarPath.getMainAttr("Import-Package"))
          .isEqualTo("shaded.javax.el,shaded.javax.enterprise.context;version=\"[2.0,3)\"")
        assertThat(jarPath.getMainAttr("Provide-Capability"))
          .isEqualTo(
            "osgi.contract;osgi.contract=JavaCDI;" +
              "uses:=\"shaded.javax.enterprise.context,shaded.javax.enterprise.context.spi,shaded.javax.enterprise.context.control," +
              "shaded.javax.enterprise.util,shaded.javax.enterprise.inject,shaded.javax.enterprise.inject.spi," +
              "shaded.javax.enterprise.inject.spi.configurator,shaded.javax.enterprise.inject.literal," +
              "shaded.javax.enterprise.inject.se,shaded.javax.enterprise.event," +
              "shaded.javax.decorator\";version:List<Version>=\"2.0,1.2,1.1,1.0\""
          )
        assertThat(jarPath.getMainAttr("Require-Capability"))
          .isEqualTo(
            "osgi.serviceloader;" +
              "filter:=\"(osgi.serviceloader=shaded.javax.enterprise.inject.se.SeContainerInitializer)\";" +
              "cardinality:=multiple,osgi.serviceloader;" +
              "filter:=\"(osgi.serviceloader=shaded.javax.enterprise.inject.spi.CDIProvider)\";" +
              "cardinality:=multiple,osgi.extender;" +
              "filter:=\"(osgi.extender=osgi.serviceloader.processor)\"," +
              "osgi.contract;osgi.contract=JavaEL;filter:=\"(&(osgi.contract=JavaEL)(version=2.2.0))\"," +
              "osgi.contract;osgi.contract=JavaInterceptor;" +
              "filter:=\"(&(osgi.contract=JavaInterceptor)(version=1.2.0))\"," +
              "osgi.contract;osgi.contract=JavaInject;" +
              "filter:=\"(&(osgi.contract=JavaInject)(version=1.0.0))\"," +
              "osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\""
          )
      }
    }

  @Test
  fun rewriteAttributesToRelocate() =
    with(transformer) {
      val source =
        """
        |Manifest-Version: 1.0
        |description-custom: This jar uses javax packages
        |"""
          .trimMargin()
          .crlfEolString

      val relocator = SimpleRelocator("javax", "jakarta")
      attributesToRelocate.addAll("description-custom", "attribute-unknown")

      transform(textContext(MANIFEST_NAME, source, relocator))

      transformToJar().use { jarPath ->
        assertThat(jarPath.getMainAttr("description-custom"))
          .isEqualTo("This jar uses jakarta packages")
      }
    }

  @Test
  fun rewriteAttributesRespectingExcludes() =
    with(transformer) {
      val source =
        """
        |Manifest-Version: 1.0
        |Export-Package: org.foo.public.api;version="1.0",org.foo.internal.impl;version="1.0"
        |"""
          .trimMargin()
          .crlfEolString

      val relocator =
        SimpleRelocator(
          "org.foo",
          "shaded.org.foo",
          excludes = listOf("org.foo.internal.*"),
        )

      transform(textContext(MANIFEST_NAME, source, relocator))

      transformToJar().use { jarPath ->
        assertThat(jarPath.getMainAttr("Export-Package"))
          .isEqualTo(
            """shaded.org.foo.public.api;version="1.0",org.foo.internal.impl;version="1.0""""
          )
      }
    }

  @Test
  fun removeAttributeUsingNullConstant() =
    with(transformer) {
      val source =
        """
        |Manifest-Version: 1.0
        |Header-To-Remove: Value1
        |Header-To-Keep: Value2
        |"""
          .trimMargin()
          .crlfEolString

      manifestEntries.put("Header-To-Remove", ManifestResourceTransformer.NULL)

      transform(textContext(MANIFEST_NAME, source))

      transformToJar().use { jarPath ->
        assertThat(jarPath.getMainAttr("Header-To-Remove")).isNull()
        assertThat(jarPath.getMainAttr("Header-To-Keep")).isEqualTo("Value2")
      }
    }

  private companion object {
    fun context(
      manifest: Manifest,
      vararg relocators: Relocator,
    ): TransformerContext {
      val os = ByteArrayOutputStream()
      manifest.write(os)
      return TransformerContext(
        path = MANIFEST_NAME,
        inputStream = ByteArrayInputStream(os.toByteArray()),
        relocators = relocators.toSet(),
      )
    }

    private val testManifest
      get() =
        Manifest().apply {
          with(mainAttributes) {
            put(Attributes.Name.MANIFEST_VERSION, "1.0")
            putValue(
              "Export-Package",
              "javax.decorator;version=\"2.0\";uses:=\"javax.enterprise.inject\"," +
                "javax.enterprise.context;version=\"2.0\";uses:=\"javax.enterprise.util,javax.inject\"",
            )
            putValue(
              "Import-Package",
              "javax.el,javax.enterprise.context;version=\"[2.0,3)\"",
            )
            putValue(
              "Provide-Capability",
              "osgi.contract;osgi.contract=JavaCDI;uses:=\"" +
                "javax.enterprise.context,javax.enterprise.context.spi,javax.enterprise.context.control," +
                "javax.enterprise.util,javax.enterprise.inject,javax.enterprise.inject.spi," +
                "javax.enterprise.inject.spi.configurator,javax.enterprise.inject.literal," +
                "javax.enterprise.inject.se,javax.enterprise.event,javax.decorator\";" +
                "version:List<Version>=\"2.0,1.2,1.1,1.0\"",
            )
            putValue(
              "Require-Capability",
              "osgi.serviceloader;" +
                "filter:=\"(osgi.serviceloader=javax.enterprise.inject.se.SeContainerInitializer)\";" +
                "cardinality:=multiple," +
                "osgi.serviceloader;" +
                "filter:=\"(osgi.serviceloader=javax.enterprise.inject.spi.CDIProvider)\";" +
                "cardinality:=multiple,osgi.extender;" +
                "filter:=\"(osgi.extender=osgi.serviceloader.processor)\"," +
                "osgi.contract;osgi.contract=JavaEL;filter:=\"(&(osgi.contract=JavaEL)(version=2.2.0))\"," +
                "osgi.contract;osgi.contract=JavaInterceptor;" +
                "filter:=\"(&(osgi.contract=JavaInterceptor)(version=1.2.0))\"," +
                "osgi.contract;osgi.contract=JavaInject;" +
                "filter:=\"(&(osgi.contract=JavaInject)(version=1.0.0))\"," +
                "osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"",
            )
          }
        }
  }
}
