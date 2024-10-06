package com.github.jengelman.gradle.plugins.shadow.util.file;


import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A JUnit rule which provides a unique temporary folder for the test.
 */
public class TestNameTestDirectoryProvider implements MethodRule, TestRule, TestDirectoryProvider {
    private TestFile dir;
    private String prefix;
    private static final TestFile root;
    private static final AtomicInteger testCounter = new AtomicInteger(1);

    static {
        // NOTE: the space in the directory name is intentional
        root = new TestFile(new File("build/tmp/test files"));
    }

    private String determinePrefix() {
        StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().endsWith("Test") || element.getClassName().endsWith("Spec")) {
                return StringUtils.substringAfterLast(element.getClassName(), ".") + "/unknown-test-" + testCounter.getAndIncrement();
            }
        }
        return "unknown-test-class-" + testCounter.getAndIncrement();
    }

    @Override
    public Statement apply(final Statement base, final FrameworkMethod method, final Object target) {
        init(method.getName(), target.getClass().getSimpleName());
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();
                getTestDirectory().maybeDeleteDir();
                // Don't delete on failure
            }
        };
    }

    @Override
    public Statement apply(final @NotNull Statement base, Description description) {
        init(description.getMethodName(), description.getTestClass().getSimpleName());
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();
                getTestDirectory().maybeDeleteDir();
                // Don't delete on failure
            }
        };
    }

    private void init(String methodName, String className) {
        if (methodName == null) {
            // must be a @ClassRule; use the rule's class name instead
            methodName = getClass().getSimpleName();
        }
        if (prefix == null) {
            String safeMethodName = methodName.replaceAll("\\s", "_").replace(File.pathSeparator, "_").replace(":", "_");
            if (safeMethodName.length() > 64) {
                safeMethodName = safeMethodName.substring(0, 32) + "..." + safeMethodName.substring(safeMethodName.length() - 32);
            }
            prefix = String.format("%s/%s", className, safeMethodName);
        }
    }

    public static TestNameTestDirectoryProvider newInstance() {
        return new TestNameTestDirectoryProvider();
    }

    public static TestNameTestDirectoryProvider newInstance(FrameworkMethod method, Object target) {
        TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider();
        testDirectoryProvider.init(method.getName(), target.getClass().getSimpleName());
        return testDirectoryProvider;
    }

    public TestFile getTestDirectory() {
        if (dir == null) {
            if (prefix == null) {
                // This can happen if this is used in a constructor or a @Before method. It also happens when using
                // @RunWith(SomeRunner) when the runner does not support rules.
                prefix = determinePrefix();
            }
            for (int counter = 1; true; counter++) {
                dir = root.file(counter == 1 ? prefix : String.format("%s%d", prefix, counter));
                if (dir.mkdirs()) {
                    break;
                }
            }
        }
        return dir;
    }

    public TestFile file(Object... path) {
        return getTestDirectory().file(path);
    }

    public TestFile createFile(Object... path) {
        return file(path).createFile();
    }

    public TestFile createDir(Object... path) {
        return file(path).createDir();
    }
}
