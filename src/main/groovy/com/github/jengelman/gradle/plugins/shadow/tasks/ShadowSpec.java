package com.github.jengelman.gradle.plugins.shadow.tasks;

import com.github.jengelman.gradle.plugins.shadow.ShadowStats;
import com.github.jengelman.gradle.plugins.shadow.internal.DependencyFilter;
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator;
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator;
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer;
import org.gradle.api.Action;
import org.gradle.api.file.CopySpec;

import java.lang.reflect.InvocationTargetException;

interface ShadowSpec extends CopySpec {
    ShadowSpec minimize();

    ShadowSpec minimize(Action<DependencyFilter> configureClosure);

    ShadowSpec dependencies(Action<DependencyFilter> configure);

    ShadowSpec transform(Class<? extends Transformer> clazz) throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException;

    <T extends Transformer> ShadowSpec transform(Class<T> clazz, Action<T> configure) throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException;

    ShadowSpec transform(Transformer transformer);

    ShadowSpec mergeServiceFiles();

    ShadowSpec mergeServiceFiles(String rootPath);

    ShadowSpec mergeServiceFiles(Action<ServiceFileTransformer> configureClosure);

    ShadowSpec mergeGroovyExtensionModules();

    ShadowSpec append(String resourcePath);

    ShadowSpec relocate(String pattern, String destination);

    ShadowSpec relocate(String pattern, String destination, Action<SimpleRelocator> configure);

    ShadowSpec relocate(Relocator relocator);

    ShadowSpec relocate(Class<? extends Relocator> clazz) throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException;

    <R extends Relocator> ShadowSpec relocate(Class<R> clazz, Action<R> configure) throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException;

    ShadowStats getStats();
}
