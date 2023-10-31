package com.github.jengelman.gradle.plugins.shadow.util;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.UncheckedException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {
    public static HashValue createHash(File file, String algorithm) {
        try {
            return createHash(new FileInputStream(file), algorithm);
        } catch (UncheckedIOException e) {
            // Catch any unchecked io exceptions and add the file path for troubleshooting
            throw new UncheckedIOException(String.format("Failed to create %s hash for file %s.", algorithm, file.getAbsolutePath()), e.getCause());
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static HashValue createHash(InputStream instr, String algorithm) {
        MessageDigest messageDigest;
        try {
            messageDigest = createMessageDigest(algorithm);
            byte[] buffer = new byte[4096];
            try {
                while (true) {
                    int nread = instr.read(buffer);
                    if (nread < 0) {
                        break;
                    }
                    messageDigest.update(buffer, 0, nread);
                }
            } finally {
                instr.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new HashValue(messageDigest.digest());
    }

    private static MessageDigest createMessageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static HashValue sha1(byte[] bytes) {
        return createHash(new ByteArrayInputStream(bytes), "SHA1");
    }

    public static HashValue sha1(InputStream inputStream) {
        return createHash(inputStream, "SHA1");
    }

    public static HashValue md5(File file) {
        return createHash(file, "MD5");
    }

    public static HashValue sha1(File file) {
        return createHash(file, "SHA1");
    }

    public static HashValue sha256(byte[] bytes) {
        return createHash(new ByteArrayInputStream(bytes), "SHA-256");
    }

    public static HashValue sha256(InputStream inputStream) {
        return createHash(inputStream, "SHA-256");
    }

    public static HashValue sha256(File file) {
        return createHash(file, "SHA-256");
    }

    public static HashValue sha512(InputStream inputStream) {
        return createHash(inputStream, "SHA-512");
    }

    public static HashValue sha512(File file) {
        return createHash(file, "SHA-512");
    }
}

