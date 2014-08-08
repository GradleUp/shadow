package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import org.gradle.api.file.CopySpec

interface ShadowSpec extends CopySpec {

    ShadowSpec dependencies(Closure configure)

    ShadowSpec transform(Class<? super Transformer> clazz)

    ShadowSpec transform(Class<? super Transformer> clazz, Closure configure)

    ShadowSpec transform(Transformer transformer)

    ShadowSpec mergeServiceFiles()

    ShadowSpec mergeServiceFiles(String rootPath)

    ShadowSpec mergeServiceFiles(Closure configureClosure)

    ShadowSpec mergeGroovyExtensionModules()

    ShadowSpec append(String resourcePath)

    ShadowSpec appendManifest(Closure configure)

    ShadowSpec relocate(String pattern, String destination)

    ShadowSpec relocate(String pattern, String destination, Closure configure)

    ShadowSpec relocate(Relocator relocator)

    ShadowSpec relocate(Class<? super Relocator> clazz)

    ShadowSpec relocate(Class<? super Relocator> clazz, Closure configure)
}
