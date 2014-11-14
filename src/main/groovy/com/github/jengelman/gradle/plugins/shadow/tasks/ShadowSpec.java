package com.github.jengelman.gradle.plugins.shadow.tasks;

import com.github.jengelman.gradle.plugins.shadow.internal.DependencyFilter;
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator;
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator;
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer;
import org.gradle.api.Action;
import org.gradle.api.file.CopySpec;

public interface ShadowSpec extends CopySpec {

    public ShadowSpec dependencies(Action<DependencyFilter> configure);

    public ShadowSpec transform(Class<? extends Transformer> clazz) throws InstantiationException, IllegalAccessException;

    public <T extends Transformer> ShadowSpec transform(Class<T> clazz, Action<T> configure) throws InstantiationException, IllegalAccessException;

    public ShadowSpec transform(Transformer transformer);

    public ShadowSpec mergeServiceFiles();

    public ShadowSpec mergeServiceFiles(String rootPath);

    public ShadowSpec mergeServiceFiles(Action<ServiceFileTransformer> configureClosure);

    public ShadowSpec mergeGroovyExtensionModules();

    public ShadowSpec append(String resourcePath);

    public ShadowSpec relocate(String pattern, String destination);

    public ShadowSpec relocate(String pattern, String destination, Action<SimpleRelocator> configure);

    public ShadowSpec relocate(Relocator relocator);

    public ShadowSpec relocate(Class<? extends Relocator> clazz) throws InstantiationException, IllegalAccessException;

    public <R extends Relocator> ShadowSpec relocate(Class<R> clazz, Action<R> configure) throws InstantiationException, IllegalAccessException;
}
