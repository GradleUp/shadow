package com.github.jengelman.gradle.plugins.shadow.tasks;

import com.github.jengelman.gradle.plugins.shadow.ShadowStats;
import com.github.jengelman.gradle.plugins.shadow.internal.*;
import com.github.jengelman.gradle.plugins.shadow.relocation.CacheableRelocator;
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator;
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator;
import com.github.jengelman.gradle.plugins.shadow.transformers.*;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.util.PatternSet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CacheableTask
public class ShadowJar extends Jar implements ShadowSpec {

    private List<Transformer> transformers;
    private List<Relocator> relocators;
    private List<Configuration> configurations;
    private DependencyFilter dependencyFilter;
    private boolean minimizeJar;
    private boolean hasUncacheableActions;
    private DependencyFilter dependencyFilterForMinimize;

    private final ShadowStats shadowStats = new ShadowStats();
    private final GradleVersionUtil versionUtil;

    public ShadowJar() {
        super();
        versionUtil = new GradleVersionUtil(getProject().getGradle().getGradleVersion());
        dependencyFilter = new DefaultDependencyFilter(getProject());
        dependencyFilterForMinimize = new MinimizeDependencyFilter(getProject());
        setManifest(new DefaultInheritManifest(getServices().get(FileResolver.class)));
        transformers = new ArrayList<>();
        relocators = new ArrayList<>();
        configurations = new ArrayList<>();

        this.getInputs().property("minimize", new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return minimizeJar;
            }
        });
        this.getOutputs().doNotCacheIf("Has one or more transforms or relocators that are not cacheable", new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task task) {
                return hasUncacheableActions;
            }
        });
    }

    public ShadowJar minimize() {
        minimizeJar = true;
        return this;
    }

    public ShadowJar minimize(Action<DependencyFilter> c) {
        minimize();
        if (c != null) {
            c.execute(dependencyFilterForMinimize);
        }
        return this;
    }

    @Override
    @Internal
    public ShadowStats getStats() {
        return shadowStats;
    }

    @Override
    public InheritManifest getManifest() {
        return (InheritManifest) super.getManifest();
    }

    @Override
    protected CopyAction createCopyAction() {
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry.class);
        final UnusedTracker unusedTracker = minimizeJar ? UnusedTracker.forProject(getProject(), configurations, dependencyFilterForMinimize) : null;
        return new ShadowCopyAction(getArchivePath(), getInternalCompressor(), documentationRegistry,
                this.getMetadataCharset(), transformers, relocators, getRootPatternSet(), shadowStats,
                versionUtil, isPreserveFileTimestamps(), minimizeJar, unusedTracker);
    }

    @Internal
    protected ZipCompressor getInternalCompressor() {
        return versionUtil.getInternalCompressor(getEntryCompression(), this);
    }

    @TaskAction
    protected void copy() {
        from(getIncludedDependencies());
        super.copy();
        getLogger().info(shadowStats.toString());
    }

    @InputFiles
    public FileCollection getIncludedDependencies() {
        return getProject().files(new Callable<FileCollection>() {

            @Override
            public FileCollection call() throws Exception {
                return dependencyFilter.resolve(configurations);
            }
        });
    }

    /**
     * Utility method for assisting between changes in Gradle 1.12 and 2.x.
     *
     * @return this
     */
    @Internal
    protected PatternSet getRootPatternSet() {
        return versionUtil.getRootPatternSet(getMainSpec());
    }

    /**
     * Configure inclusion/exclusion of module & project dependencies into uber jar.
     *
     * @param c the configuration of the filter
     * @return this
     */
    public ShadowJar dependencies(Action<DependencyFilter> c) {
        if (c != null) {
            c.execute(dependencyFilter);
        }
        return this;
    }

    /**
     * Add a Transformer instance for modifying JAR resources and configure.
     *
     * @param clazz the transformer to add. Must have a no-arg constructor
     * @return this
     */
    public ShadowJar transform(Class<? extends Transformer> clazz) throws InstantiationException, IllegalAccessException {
        return transform(clazz, null);
    }

    /**
     * Add a Transformer instance for modifying JAR resources and configure.
     *
     * @param clazz the transformer class to add. Must have no-arg constructor
     * @param c the configuration for the transformer
     * @return this
     */
    public <T extends Transformer> ShadowJar transform(Class<T> clazz, Action<T> c) throws InstantiationException, IllegalAccessException {
        T transformer = clazz.newInstance();
        addTransform(transformer, c, isCacheableTransform(clazz));
        return this;
    }

    private boolean isCacheableTransform(Class<? extends Transformer> clazz) {
        return clazz.isAnnotationPresent(CacheableTransformer.class);
    }

    /**
     * Add a preconfigured transformer instance.
     *
     * @param transformer the transformer instance to add
     * @return this
     */
    public ShadowJar transform(Transformer transformer) {
        addTransform(transformer, null, isCacheableTransform(transformer.getClass()));
        return this;
    }

    private <T extends Transformer> void addTransform(T transformer, Action<T> c, boolean isCacheable) {
        if (!isCacheable) {
            hasUncacheableActions = true;
        }

        if (c != null) {
            c.execute(transformer);
        }

        transformers.add(transformer);
    }

    /**
     * Syntactic sugar for merging service files in JARs.
     *
     * @return this
     */
    public ShadowJar mergeServiceFiles() {
        try {
            transform(ServiceFileTransformer.class);
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Syntactic sugar for merging service files in JARs.
     *
     * @return this
     */
    public ShadowJar mergeServiceFiles(final String rootPath) {
        try {
            transform(ServiceFileTransformer.class, new Action<ServiceFileTransformer>() {

                @Override
                public void execute(ServiceFileTransformer serviceFileTransformer) {
                    serviceFileTransformer.setPath(rootPath);
                }
            });
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Syntactic sugar for merging service files in JARs.
     *
     * @return this
     */
    public ShadowJar mergeServiceFiles(Action<ServiceFileTransformer> configureClosure) {
        try {
            transform(ServiceFileTransformer.class, configureClosure);
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Syntactic sugar for merging Groovy extension module descriptor files in JARs
     *
     * @return this
     */
    public ShadowJar mergeGroovyExtensionModules() {
        try {
            transform(GroovyExtensionModuleTransformer.class);
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Syntax sugar for merging service files in JARs
     *
     * @return this
     */
    public ShadowJar append(final String resourcePath) {
        try {
            transform(AppendingTransformer.class, new Action<AppendingTransformer>() {
                @Override
                public void execute(AppendingTransformer transformer) {
                    transformer.setResource(resourcePath);
                }
            });
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Add a class relocator that maps each class in the pattern to the provided destination.
     *
     * @param pattern the source pattern to relocate
     * @param destination the destination package
     * @return this
     */
    public ShadowJar relocate(String pattern, String destination) {
        return relocate(pattern, destination, null);
    }

    /**
     * Add a class relocator that maps each class in the pattern to the provided destination.
     *
     * @param pattern the source pattern to relocate
     * @param destination the destination package
     * @param configure the configuration of the relocator
     * @return this
     */
    public ShadowJar relocate(String pattern, String destination, Action<SimpleRelocator> configure) {
        SimpleRelocator relocator = new SimpleRelocator(pattern, destination, new ArrayList<String>(), new ArrayList<String>());
        addRelocator(relocator, configure, isCacheableRelocator(relocator.getClass()));
        return this;
    }

    /**
     * Add a relocator instance.
     *
     * @param relocator the relocator instance to add
     * @return this
     */
    public ShadowJar relocate(Relocator relocator) {
        addRelocator(relocator, null, isCacheableRelocator(relocator.getClass()));
        return this;
    }

    /**
     * Add a relocator of the provided class.
     *
     * @param relocatorClass the relocator class to add. Must have a no-arg constructor.
     * @return this
     */
    public ShadowJar relocate(Class<? extends Relocator> relocatorClass) throws InstantiationException, IllegalAccessException {
        return relocate(relocatorClass, null);
    }

    private <R extends Relocator> void addRelocator(R relocator, Action<R> configure, boolean isCacheableRelocator) {
        if (!isCacheableRelocator) {
            hasUncacheableActions = true;
        }

        if (configure != null) {
            configure.execute(relocator);
        }

        relocators.add(relocator);
    }

    /**
     * Add a relocator of the provided class and configure.
     *
     * @param relocatorClass the relocator class to add. Must have a no-arg constructor
     * @param configure the configuration for the relocator
     * @return this
     */
    public <R extends Relocator> ShadowJar relocate(Class<R> relocatorClass, Action<R> configure) throws InstantiationException, IllegalAccessException {
        R relocator = relocatorClass.newInstance();
        addRelocator(relocator, configure, isCacheableRelocator(relocatorClass));
        return this;
    }

    private boolean isCacheableRelocator(Class<? extends Relocator> relocatorClass) {
        return relocatorClass.isAnnotationPresent(CacheableRelocator.class);
    }

    @Nested
    public List<Transformer> getTransformers() {
        return this.transformers;
    }

    public void setTransformers(List<Transformer> transformers) {
        hasUncacheableActions = true;
        this.transformers = transformers;
    }

    @Nested
    public List<Relocator> getRelocators() {
        return this.relocators;
    }

    public void setRelocators(List<Relocator> relocators) {
        hasUncacheableActions = true;
        this.relocators = relocators;
    }

    @InputFiles @Optional
    public List<Configuration> getConfigurations() {
        return this.configurations;
    }

    public void setConfigurations(List<Configuration> configurations) {
        this.configurations = configurations;
    }

    @Internal
    public DependencyFilter getDependencyFilter() {
        return this.dependencyFilter;
    }

    public void setDependencyFilter(DependencyFilter filter) {
        this.dependencyFilter = filter;
    }
}
