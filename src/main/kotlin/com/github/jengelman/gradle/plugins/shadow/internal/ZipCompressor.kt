package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.internal.file.archive.compression.ArchiveOutputStreamFactory

/**
 * Modified from [org.gradle.api.internal.file.copy.ZipCompressor.java](https://github.com/gradle/gradle/blob/73091267320cd330bcb3457903436579bac354ce/platforms/core-configuration/file-operations/src/main/java/org/gradle/api/internal/file/copy/ZipCompressor.java).
 */
public interface ZipCompressor : ArchiveOutputStreamFactory
