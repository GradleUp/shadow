package com.github.jengelman.gradle.plugins.shadow.internal;

import com.android.tools.r8.*;
import com.android.tools.r8.origin.Origin;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * An implementation of an {@code UnusedTracker} using R8, the optimization and shrinking tool
 * of the Android project.
 * <p>
 * Contrary to the {@code UnusedTrackerUsingJDependency} this {@code UnusedTracker} is able to
 * fully remove unused fields / methods, leading to much better results for complex dependencies
 * like e.g. guava.
 * <p>
 * This class is written in Java to avoid groovy madness especially in combination with R8.
 */
public class UnusedTrackerUsingR8 extends UnusedTracker {
    private static final String TMP_DIR = "tmp/shadowJar/minimize";

    private final Path tmpDir;
    private final Collection<File> projectFiles;
    private final Collection<File> dependencies;

    private UnusedTrackerUsingR8(Path tmpDir, Iterable<File> classDirs, FileCollection classJars, FileCollection toMinimize) {
        super(toMinimize);

        this.tmpDir = tmpDir;

        this.projectFiles = new ArrayList<>();
        this.dependencies = new ArrayList<>();

        for (File dir : classDirs) {
            Path path = Paths.get(dir.getAbsolutePath());
            collectClassFiles(path, projectFiles);
        }

        projectFiles.addAll(classJars.getFiles());
    }

    @Override
    public Path getPathToProcessedClass(String classname) {
        final String className = FilenameUtils.removeExtension(classname).replace('/', '.');
        return Paths.get(tmpDir.toString(), className.replaceAll("\\.", "/") + ".class");
    }

    @Override
    public Set<String> findUnused() {
        // We effectively run R8 twice:
        //  * first time disabling any processing, to retrieve all project classes
        //  * second time with a full shrink run to get all unused classes
        R8Command.Builder builder = R8Command.builder(new GradleDiagnosticHandler(true));

        try {
            populateBuilderWithProjectFiles(builder);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        // add all dependency jars
        for (File dep : dependencies) {
            Path path = Paths.get(dep.getAbsolutePath());
            builder.addProgramFiles(path);
        }

        final Set<String> removedClasses = new HashSet<>();

        // Add any class from the usage list to the list of removed classes.
        // This is a bit of a hack but the best things I could think of so far.
        builder.setProguardUsageConsumer(new StringConsumer() {
            private final String LINE_SEPARATOR = System.getProperty("line.separator");
            private String  lastString = LINE_SEPARATOR;
            private String  classString;
            private boolean expectingSeparator;

            @Override
            public void accept(String s, DiagnosticsHandler handler) {
                if (classString == null && Objects.equals(lastString, LINE_SEPARATOR)) {
                    classString        = s;
                    expectingSeparator = true;
                } else if (expectingSeparator && Objects.equals(s, LINE_SEPARATOR)) {
                    removedClasses.add(classString);
                    classString        = null;
                    expectingSeparator = false;
                } else {
                    classString        = null;
                    expectingSeparator = false;
                }

                lastString = s;
            }
        });

        List<String> proguardConfig;

        try {
            proguardConfig = getKeepRules();
            proguardConfig.add("-dontoptimize");
            proguardConfig.add("-dontobfuscate");
            proguardConfig.add("-ignorewarnings");
            // TODO: consider keeping all attributes by default to avoid negative side-effects.
            // proguardConfig.add("-keepattributes **")

            // TODO: add support for custom rules that can be configured via the shadowSpec.
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        builder.addProguardConfiguration(proguardConfig, Origin.unknown());

        // Set compilation mode to debug to disable any code of instruction
        // level optimizations.
        builder.setMode(CompilationMode.DEBUG);

        builder.setProgramConsumer(new ClassFileConsumer() {
            @Override
            public void accept(ByteDataView byteDataView, String s, DiagnosticsHandler diagnosticsHandler) {
                String name = typeNameToExternalClassName(s);

                // any class that is actually going to be written to the output
                // must not be present in the set of removed classes.
                // Should not really be needed, but we prefer to be paranoid.
                removedClasses.remove(name);

                Path classFile = Paths.get(tmpDir.toString(), externalClassNameToInternal(name) + ".class");
                try {
                    Files.createDirectories(classFile.getParent());
                    Files.write(classFile, byteDataView.getBuffer());
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }

            @Override
            public void finished(DiagnosticsHandler diagnosticsHandler) {}
        });

        try {
            R8.run(builder.build());
        } catch (CompilationFailedException cfe) {
            throw new RuntimeException(cfe);
        }

        return removedClasses;
    }

    private static String typeNameToExternalClassName(String typeName) {
        String className = typeName.startsWith("L") && typeName.endsWith(";") ?
                typeName.substring(1, typeName.length() - 1) :
                typeName;

        return className.replaceAll("/", ".");
    }

    private static String externalClassNameToInternal(String className) {
        return className.replaceAll("\\.", "/");
    }

    private static boolean isClassFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return !name.equals("module-info.class") && name.endsWith(".class");
    }

    /**
     * Returns a collection of necessary keep rules. For this purpose, R8 is executed
     * in a kind of pass through mode where we collect all classes that should
     * be kept as is (classes from each source set and api dependencies).
     * <p>
     * This could be achieved differently and more efficiently, but is done like
     * that for convenience reasons atm.
     */
    private List<String> getKeepRules() throws IOException, CompilationFailedException {
        R8Command.Builder builder = R8Command.builder(new GradleDiagnosticHandler(false));

        populateBuilderWithProjectFiles(builder);

        // add all dependencies as library jars to avoid warnings
        for (File dep : dependencies) {
            Path path = Paths.get(dep.getAbsolutePath());
            builder.addLibraryFiles(path);
        }

        // disable everything, we just want to get a list of
        // all project classes.
        List<String> configs = new ArrayList<>();
        configs.add("-dontshrink");
        configs.add("-dontoptimize");
        configs.add("-dontobfuscate");
        configs.add("-ignorewarnings");
        configs.add("-dontwarn");

        builder.addProguardConfiguration(configs, Origin.unknown());

        // Set compilation mode to debug to disable any code of instruction
        // level optimizations.
        builder.setMode(CompilationMode.DEBUG);

        final List<String> keepRules = new ArrayList<>();

        builder.setProgramConsumer(new ClassFileConsumer() {
            @Override
            public void accept(ByteDataView byteDataView, String s, DiagnosticsHandler diagnosticsHandler) {
                String name = typeNameToExternalClassName(s);
                keepRules.add("-keep,includedescriptorclasses class " + name + " { *; }");
            }

            @Override
            public void finished(DiagnosticsHandler diagnosticsHandler) {}
        });

        R8.run(builder.build());
        return keepRules;
    }

    private void populateBuilderWithProjectFiles(R8Command.Builder builder) throws IOException {
        addJDKLibrary(builder);

        for (File f : projectFiles) {
            Path path = Paths.get(f.getAbsolutePath());

            if (f.getAbsolutePath().endsWith(".class")) {
                byte[] bytes = Files.readAllBytes(Paths.get(f.getAbsolutePath()));
                builder.addClassProgramData(bytes, Origin.unknown());
            } else {
                builder.addProgramFiles(path);
            }
        }
    }

    private void collectClassFiles(Path dir, Collection<File> result) {
        File file = dir.toFile();
        if (file.exists()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    if (child.isDirectory()) {
                        collectClassFiles(child.toPath(), result);
                    } else {
                        Path relative = child.toPath();
                        if (isClassFile(relative)) {
                            result.add(child.getAbsoluteFile());
                        }
                    }
                }
            }
        }
    }

    private static void addJDKLibrary(R8Command.Builder builder) throws IOException {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null) {
            javaHome = System.getenv("JAVA_HOME");
        }

        if (javaHome == null) {
            throw new RuntimeException("unable to determine 'java.home' environment variable");
        }

        builder.addLibraryResourceProvider(JdkClassFileProvider.fromJdkHome(Paths.get(javaHome)));
    }

    @Override
    public void addDependency(File jarOrDir) {
        if (toMinimize.contains(jarOrDir)) {
            dependencies.add(jarOrDir);
        }
    }

    public static UnusedTracker forProject(Project project, FileCollection apiJars, Iterable<File> sourceSetsClassesDirs, FileCollection toMinimize) {
        try {
            Path tmpDir = Paths.get(project.getBuildDir().getAbsolutePath(), TMP_DIR);
            Files.createDirectories(tmpDir);

            return new UnusedTrackerUsingR8(tmpDir, sourceSetsClassesDirs, apiJars, toMinimize);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static class GradleDiagnosticHandler implements DiagnosticsHandler {

        private static final Logger logger = LoggerFactory.getLogger(GradleDiagnosticHandler.class);

        private final boolean enableInfo;

        GradleDiagnosticHandler(boolean enableInfo) {
            this.enableInfo = enableInfo;
        }

        @Override
        public void error(Diagnostic error) {
            if (logger.isErrorEnabled()) {
                DiagnosticsHandler.printDiagnosticToStream(error, "Error", System.err);
            }
        }

        @Override
        public void warning(Diagnostic warning) {
            if (logger.isWarnEnabled()) {
                DiagnosticsHandler.printDiagnosticToStream(warning, "Warning", System.out);
            }
        }

        @Override
        public void info(Diagnostic info) {
            if (logger.isInfoEnabled() && enableInfo) {
                DiagnosticsHandler.printDiagnosticToStream(info, "Info", System.out);
            }
        }
    }
}