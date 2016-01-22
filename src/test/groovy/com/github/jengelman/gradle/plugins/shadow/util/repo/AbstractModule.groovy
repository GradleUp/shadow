package com.github.jengelman.gradle.plugins.shadow.util.repo

import com.github.jengelman.gradle.plugins.shadow.util.file.TestFile
import org.gradle.internal.hash.HashUtil


abstract class AbstractModule {
    /**
     * @param cl A closure that is passed a writer to use to generate the content.
     */
    protected void publish(TestFile file, Closure cl) {
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

    protected void publishWithStream(TestFile file, Closure cl) {
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

    protected abstract onPublish(TestFile file)

    TestFile getSha1File(TestFile file) {
        getHashFile(file, "sha1")
    }

    TestFile sha1File(TestFile file) {
        hashFile(file, "sha1", 40)
    }

    TestFile getMd5File(TestFile file) {
        getHashFile(file, "md5")
    }

    TestFile md5File(TestFile file) {
        hashFile(file, "md5", 32)
    }

    private TestFile hashFile(TestFile file, String algorithm, int len) {
        def hashFile = getHashFile(file, algorithm)
        def hash = getHash(file, algorithm)
        hashFile.text = String.format("%0${len}x", hash)
        return hashFile
    }

    private TestFile getHashFile(TestFile file, String algorithm) {
        file.parentFile.file("${file.name}.${algorithm}")
    }

    protected BigInteger getHash(TestFile file, String algorithm) {
        HashUtil.createHash(file, algorithm.toUpperCase()).asBigInteger()
    }
}
