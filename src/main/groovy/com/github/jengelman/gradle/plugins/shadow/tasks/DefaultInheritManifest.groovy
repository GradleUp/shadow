package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.java.archives.Attributes
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestException
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.java.archives.internal.DefaultManifestMergeSpec
import org.gradle.util.ConfigureUtil

import java.nio.charset.Charset

class DefaultInheritManifest implements InheritManifest {

    private List<DefaultManifestMergeSpec> inheritMergeSpecs = []

    private final FileResolver fileResolver

    private final Manifest internalManifest

    DefaultInheritManifest(FileResolver fileResolver) {
        this.internalManifest = new DefaultManifest(fileResolver)
        this.fileResolver = fileResolver
    }

    public InheritManifest inheritFrom(Object... inheritPaths) {
        inheritFrom(inheritPaths, null)
        return this
    }

    public InheritManifest inheritFrom(Object inheritPaths, Closure closure) {
        DefaultManifestMergeSpec mergeSpec = new DefaultManifestMergeSpec()
        mergeSpec.from(inheritPaths)
        inheritMergeSpecs.add(mergeSpec)
        ConfigureUtil.configure(closure, mergeSpec)
        return this
    }

    @Override
    Attributes getAttributes() {
        return internalManifest.getAttributes()
    }

    @Override
    Map<String, Attributes> getSections() {
        return internalManifest.getSections()
    }

    @Override
    Manifest attributes(Map<String, ?> map) throws ManifestException {
        internalManifest.attributes(map)
        return this
    }

    @Override
    Manifest attributes(Map<String, ?> map, String s) throws ManifestException {
        internalManifest.attributes(map, s)
        return this
    }

    @Override
    public DefaultManifest getEffectiveManifest() {
        DefaultManifest base = new DefaultManifest(fileResolver)
        inheritMergeSpecs.each {
            base = it.merge(base, fileResolver)
        }
        base.from internalManifest
        return base.getEffectiveManifest()
    }

    @Override
    String getContentCharset() {
        return internalManifest.getContentCharset()
    }

    @Override
    void setContentCharset(String contentCharset) {
        if (contentCharset == null) {
            throw new InvalidUserDataException("contentCharset must not be null");
        }
        if (!Charset.isSupported(contentCharset)) {
            throw new InvalidUserDataException(String.format("Charset for contentCharset '%s' is not supported by your JVM", contentCharset));
        }
        this.internalManifest.contentCharset= contentCharset
    }

    @Override
    Manifest writeTo(Writer writer) {
        this.getEffectiveManifest().writeTo(writer)
        return this
    }

    @Override
    Manifest writeTo(OutputStream outputStream) {
        this.getEffectiveManifest().writeTo(outputStream)
        return this
    }

    @Override
    Manifest writeTo(Object o) {
        this.getEffectiveManifest().writeTo(o)
        return this
    }

    @Override
    Manifest from(Object... objects) {
        internalManifest.from(objects)
        return this
    }

    @Override
    Manifest from(Object o, Closure<?> closure) {
        internalManifest.from(o, closure)
        return this
    }
}
