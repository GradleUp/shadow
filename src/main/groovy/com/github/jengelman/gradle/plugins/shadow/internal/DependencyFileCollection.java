package com.github.jengelman.gradle.plugins.shadow.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.file.AbstractFileCollection;

import java.io.File;
import java.util.List;
import java.util.Set;

public class DependencyFileCollection extends AbstractFileCollection {

    private final DependencyFilter filter;
    private final List<Configuration> configurations;

    public DependencyFileCollection(DependencyFilter filter, List<Configuration> configurations) {
        this.filter = filter;
        this.configurations = configurations;
    }

    @Override
    public String getDisplayName() {
        return "Shadow Dependencies to Merge";
    }

    @Override
    public Set<File> getFiles() {
        return filter.resolve(configurations).getFiles();
    }
}
