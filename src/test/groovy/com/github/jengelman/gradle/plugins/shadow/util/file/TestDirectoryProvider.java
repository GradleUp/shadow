package com.github.jengelman.gradle.plugins.shadow.util.file;

/**
 * Implementations provide a working space to be used in tests.
 *
 * The client is not responsible for removing any files.
 */
public interface TestDirectoryProvider {

    /**
     * The directory to use, guaranteed to exist.
     *
     * @return The directory to use, guaranteed to exist.
     */
    TestFile getTestDirectory();

}
