package com.github.jengelman.gradle.plugins.shadow.util.repo

import com.github.jengelman.gradle.plugins.shadow.util.HashUtil


abstract class AbstractModule {
    /**
     * @param cl A closure that is passed a writer to use to generate the content.
     */
    protected void publish(File file, Closure cl) {
        def hashBefore = file.exists() ? getHash(file, "sha1") : null
        def tmpFile = file.parentFile.file("${file.name}.tmp")

        tmpFile.withWriter("utf-8") {
            cl.call(it)
        }

        def hashAfter = getHash(tmpFile, "sha1")
        if (hashAfter == hashBefore) {
            // Already published
            return
        }

        assert !file.exists() || file.delete()
        assert tmpFile.renameTo(file)
        onPublish(file)
    }

    protected void publishWithStream(File file, Closure cl) {
        def hashBefore = file.exists() ? getHash(file, "sha1") : null
        def tmpFile = file.parentFile.file("${file.name}.tmp")

        tmpFile.withOutputStream {
            cl.call(it)
        }

        def hashAfter = getHash(tmpFile, "sha1")
        if (hashAfter == hashBefore) {
            // Already published
            return
        }

        assert !file.exists() || file.delete()
        assert tmpFile.renameTo(file)
        onPublish(file)
    }

    protected abstract onPublish(File file)

    static File getSha1File(File file) {
        getHashFile(file, "sha1")
    }

    static File sha1File(File file) {
        hashFile(file, "sha1", 40)
    }

    static File getMd5File(File file) {
        getHashFile(file, "md5")
    }

    static File md5File(File file) {
        hashFile(file, "md5", 32)
    }

    private static File hashFile(File file, String algorithm, int len) {
        def hashFile = getHashFile(file, algorithm)
        def hash = getHash(file, algorithm)
        hashFile.text = String.format("%0${len}x", hash)
        return hashFile
    }

    private static File getHashFile(File file, String algorithm) {
        file.parentFile.file("${file.name}.${algorithm}")
    }

    private static BigInteger getHash(File file, String algorithm) {
        HashUtil.createHash(file, algorithm.toUpperCase()).asBigInteger()
    }
}
