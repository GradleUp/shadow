package com.github.jengelman.gradle.testkit

import org.gradle.api.Transformer
import org.gradle.internal.UncheckedException
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.testkit.functional.internal.GradleHandle
import org.gradle.testkit.functional.internal.GradleHandleFactory
import org.gradle.testkit.functional.internal.classpath.ClasspathAddingInitScriptBuilder
import org.gradle.util.CollectionUtils
import org.gradle.util.GFileUtils

public class ClasspathInjectingFilteringGrandleHandleFactory implements GradleHandleFactory {

    private final ClassLoader classLoader;
    private final GradleHandleFactory delegateFactory;

    public ClasspathInjectingFilteringGrandleHandleFactory(ClassLoader classLoader, GradleHandleFactory delegateFactory) {
        this.classLoader = classLoader;
        this.delegateFactory = delegateFactory;
    }

    public GradleHandle start(File directory, List<String> arguments) {
        File testKitDir = new File(directory, ".gradle-test-kit");
        if (!testKitDir.exists()) {
            GFileUtils.mkdirs(testKitDir);
        }

        File initScript = new File(testKitDir, "init.gradle");
        new ClasspathAddingInitScriptBuilder().build(initScript, getClasspathAsFiles());

        List<String> ammendedArguments = new ArrayList<String>(arguments.size() + 2);
        ammendedArguments.add("-I");
        ammendedArguments.add(initScript.getAbsolutePath());
        ammendedArguments.addAll(arguments);
        return delegateFactory.start(directory, ammendedArguments);
    }

    private List<File> getClasspathAsFiles() {
        List<URL> classpathUrls = ClasspathUtil.getClasspath(classLoader);
        List<File> sourceClasspath =  CollectionUtils.collect(classpathUrls, new ArrayList<File>(classpathUrls.size()), new Transformer<File, URL>() {
            public File transform(URL url) {
                try {
                    return new File(url.toURI());
                } catch (URISyntaxException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        });
        List<File> filteredClasspath = sourceClasspath.findAll { file ->
            !['wrapper/dist', 'spock', 'xmlunit'].any {
                file.path.contains(it)
            }
        }
        return filteredClasspath
    }

}
