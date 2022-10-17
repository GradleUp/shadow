package com.github.jengelman.gradle.plugins.shadow.tasks;

import com.github.jengelman.gradle.plugins.shadow.ShadowStats;
import com.github.jengelman.gradle.plugins.shadow.internal.*;
import com.github.jengelman.gradle.plugins.shadow.relocation.CacheableRelocator;
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator;
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator;
import com.github.jengelman.gradle.plugins.shadow.transformers.*;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CacheableTask
public class ShadowJar extends Jar implements ShadowSpec {

    private List<Transformer> transformers;
    private List<Relocator> relocators;
    private List<FileCollection> configurations;
    private transient DependencyFilter dependencyFilter;

    private boolean useR8;
    private R8Configuration r8Configuration;
    private boolean minimizeJar;
    private transient DependencyFilter dependencyFilterForMinimize;
    private FileCollection toMinimize;
    private FileCollection apiJars;
    private FileCollection sourceSetsClassesDirs;

    private final ShadowStats shadowStats = new ShadowStats();
    private final GradleVersionUtil versionUtil;

    private final ConfigurableFileCollection includedDependencies = getProject().files(new Callable<FileCollection>() {

        @Override
        public FileCollection call() throws Exception {
            return dependencyFilter.resolve(configurations);
        }
    });

    public ShadowJar() {
        super();
        setDuplicatesStrategy(DuplicatesStrategy.INCLUDE); //shadow filters out files later. This was the default behavior in  Gradle < 6.x
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
                for (Transformer transformer : transformers) {
                    if (!isCacheableTransform(transformer.getClass())) {
                        return true;
                    }
                }
                for (Relocator relocator : relocators) {
                    if (!isCacheableRelocator(relocator.getClass())) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public ShadowSpec useR8() {
        useR8 = true;
        r8Configuration = new DefaultR8Configuration();
        return this;
    }

    @Override
    public ShadowSpec useR8(Action<R8Configuration> configure) {
        useR8();
        if (configure != null) {
            configure.execute(r8Configuration);
        }
        return this;
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
        final UnusedTracker unusedTracker =
            minimizeJar ?
                (useR8 ?
                    UnusedTrackerUsingR8.forProject(getProject(), r8Configuration, getApiJars(), getSourceSetsClassesDirs().getFiles(), getToMinimize()) :
                    UnusedTrackerUsingJDependency.forProject(getApiJars(), getSourceSetsClassesDirs().getFiles(), getToMinimize())) :
                null;

        return new ShadowCopyAction(getArchiveFile().get().getAsFile(), getInternalCompressor(), documentationRegistry,
                this.getMetadataCharset(), transformers, relocators, getRootPatternSet(), shadowStats,
                versionUtil, isPreserveFileTimestamps(), minimizeJar, unusedTracker);
    }

    @Classpath
    FileCollection getToMinimize() {
        if (toMinimize == null) {
            toMinimize = minimizeJar
                    ? dependencyFilterForMinimize.resolve(configurations).minus(getApiJars())
                    : getProject().getObjects().fileCollection();
        }
        return toMinimize;
    }


    @Classpath
    FileCollection getApiJars() {
        if (apiJars == null) {
            apiJars = minimizeJar
                    ? UnusedTracker.getApiJarsFromProject(getProject())
                    : getProject().getObjects().fileCollection();
        }
        return apiJars;
    }


    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getSourceSetsClassesDirs() {
        if (sourceSetsClassesDirs == null) {
            ConfigurableFileCollection allClassesDirs = getProject().getObjects().fileCollection();
            if (minimizeJar) {
                for (SourceSet sourceSet : getProject().getExtensions().getByType(SourceSetContainer.class)) {
                    // do not include test sources
                    if (!sourceSet.getName().equals(SourceSet.TEST_SOURCE_SET_NAME)) {
                        FileCollection classesDirs = sourceSet.getOutput().getClassesDirs();
                        allClassesDirs.from(classesDirs);
                    }
                }
            }
            sourceSetsClassesDirs = allClassesDirs.filter(new Spec<File>() {
                @Override
                public boolean isSatisfiedBy(File file) {
                    return file.isDirectory();
                }
            });
        }
        return sourceSetsClassesDirs;
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

    @Classpath
    public FileCollection getIncludedDependencies() {
        return includedDependencies;
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
     * Configure inclusion/exclusion of module and project dependencies into uber jar.
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
    public ShadowJar transform(Class<? extends Transformer> clazz) throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        return transform(clazz, null);
    }

    /**
     * Add a Transformer instance for modifying JAR resources and configure.
     *
     * @param clazz the transformer class to add. Must have no-arg constructor
     * @param c the configuration for the transformer
     * @return this
     */
    public <T extends Transformer> ShadowJar transform(Class<T> clazz, Action<T> c) throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        T transformer = clazz.getDeclaredConstructor().newInstance();
        addTransform(transformer, c);
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
        addTransform(transformer, null);
        return this;
    }

    private <T extends Transformer> void addTransform(T transformer, Action<T> c) {
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
        } catch (NoSuchMethodException e) {
        } catch (InvocationTargetException e) {
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
        } catch (NoSuchMethodException e) {
        } catch (InvocationTargetException e) {
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
        } catch (NoSuchMethodException e) {
        } catch (InvocationTargetException e) {
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
        } catch (NoSuchMethodException e) {
        } catch (InvocationTargetException e) {
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
        } catch (NoSuchMethodException e) {
        } catch (InvocationTargetException e) {
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
        addRelocator(relocator, configure);
        return this;
    }

    /**
     * Add a relocator instance.
     *
     * @param relocator the relocator instance to add
     * @return this
     */
    public ShadowJar relocate(Relocator relocator) {
        addRelocator(relocator, null);
        return this;
    }

    /**
     * Add a relocator of the provided class.
     *
     * @param relocatorClass the relocator class to add. Must have a no-arg constructor.
     * @return this
     */
    public ShadowJar relocate(Class<? extends Relocator> relocatorClass) throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        return relocate(relocatorClass, null);
    }

    private <R extends Relocator> void addRelocator(R relocator, Action<R> configure) {
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
    public <R extends Relocator> ShadowJar relocate(Class<R> relocatorClass, Action<R> configure) throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        R relocator = relocatorClass.getDeclaredConstructor().newInstance();
        addRelocator(relocator, configure);
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
        this.transformers = transformers;
    }

    @Nested
    public List<Relocator> getRelocators() {
        return this.relocators;
    }

    public void setRelocators(List<Relocator> relocators) {
        this.relocators = relocators;
    }

    @Classpath @Optional
    public List<FileCollection> getConfigurations() {
        return this.configurations;
    }

    public void setConfigurations(List<FileCollection> configurations) {
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
