package com.github.jengelman.gradle.plugins.shadow.util.file

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.tools.ant.Project
import org.apache.tools.ant.taskdefs.Expand
import org.apache.tools.ant.taskdefs.Tar
import org.apache.tools.ant.taskdefs.Untar
import org.apache.tools.ant.taskdefs.Zip

import java.util.zip.ZipInputStream

import static org.junit.jupiter.api.Assertions.assertTrue

class TestFileHelper {
    TestFile file

    TestFileHelper(TestFile file) {
        this.file = file
    }

    void unzipTo(File target, boolean nativeTools) {
        // Check that each directory in hierarchy is present
        file.withInputStream { InputStream instr ->
            def dirs = [] as Set
            def zipStr = new ZipInputStream(instr)
            def entry
            while (entry = zipStr.getNextEntry()) {
                if (entry.directory) {
                    assertTrue(dirs.add(entry.name), "Duplicate directory '$entry.name'")
                }
                if (!entry.name.contains('/')) {
                    continue
                }
                def parent = StringUtils.substringBeforeLast(entry.name, '/') + '/'
                assertTrue(dirs.contains(parent), "Missing dir '$parent'")
            }
        }

        if (nativeTools && isUnix()) {
            def process = ['unzip', '-o', file.absolutePath, '-d', target.absolutePath].execute()
            process.consumeProcessOutput(System.out, System.err)
            assert process.waitFor() == 0
            return
        }

        def unzip = new Expand()
        unzip.src = file
        unzip.dest = target

        unzip.project = new Project()
        unzip.execute()
    }

    void untarTo(File target, boolean nativeTools) {
        if (nativeTools && isUnix()) {
            target.mkdirs()
            def builder = new ProcessBuilder(['tar', '-xpf', file.absolutePath])
            builder.directory(target)
            def process = builder.start()
            process.consumeProcessOutput()
            assert process.waitFor() == 0
            return
        }

        def untar = new Untar()
        untar.setSrc(file)
        untar.setDest(target)

        if (file.name.endsWith(".tgz")) {
            def method = new Untar.UntarCompressionMethod()
            method.value = "gzip"
            untar.compression = method
        } else if (file.name.endsWith(".tbz2")) {
            def method = new Untar.UntarCompressionMethod()
            method.value = "bzip2"
            untar.compression = method
        }

        untar.project = new Project()
        untar.execute()
    }

    private static boolean isUnix() {
        return !System.getProperty('os.name').toLowerCase().contains('windows')
    }

    String getPermissions() {
        if (!isUnix()) {
            return "-rwxr-xr-x"
        }

        def process = ["ls", "-ld", file.absolutePath].execute()
        def result = process.inputStream.text
        def error = process.errorStream.text
        def retval = process.waitFor()
        if (retval != 0) {
            throw new RuntimeException("Could not list permissions for '$file': $error")
        }
        def perms = result.split()[0]
        assert perms.matches("[d\\-][rwx\\-]{9}[@\\+]?")
        return perms.substring(1, 10)
    }

    void setPermissions(String permissions) {
        if (!isUnix()) {
            return
        }
        int m = toMode(permissions)
        setMode(m)
    }

    void setMode(int mode) {
        def process = ["chmod", Integer.toOctalString(mode), file.absolutePath].execute()
        def error = process.errorStream.text
        def retval = process.waitFor()
        if (retval != 0) {
            throw new RuntimeException("Could not set permissions for '$file': $error")
        }
    }

    private static int toMode(String permissions) {
        int m = [6, 3, 0].inject(0) { mode, pos ->
            mode |= permissions[9 - pos - 3] == 'r' ? 4 << pos : 0
            mode |= permissions[9 - pos - 2] == 'w' ? 2 << pos : 0
            mode |= permissions[9 - pos - 1] == 'x' ? 1 << pos : 0
            return mode
        }
        return m
    }

    int getMode() {
        return toMode(getPermissions())
    }

    void delete(boolean nativeTools) {
        if (isUnix() && nativeTools) {
            def process = ["rm", "-rf", file.absolutePath].execute()
            def error = process.errorStream.text
            def retval = process.waitFor()
            if (retval != 0) {
                throw new RuntimeException("Could not delete '$file': $error")
            }
        } else {
            FileUtils.deleteQuietly(file)
        }
    }

    String readLink() {
        def process = ["readlink", file.absolutePath].execute()
        def error = process.errorStream.text
        def retval = process.waitFor()
        if (retval != 0) {
            throw new RuntimeException("Could not read link '$file': $error")
        }
        return process.inputStream.text.trim()
    }

    ExecOutput exec(List args) {
        return execute(args, null)
    }

    ExecOutput execute(List args, List env) {
        def process = ([file.absolutePath] + args).execute(env, null)
        String output = process.inputStream.text
        String error = process.errorStream.text
        if (process.waitFor() != 0) {
            throw new RuntimeException("Could not execute $file. Error: $error, Output: $output")
        }
        return new ExecOutput(output, error)
    }

    void zipTo(TestFile zipFile, boolean nativeTools) {
        if (nativeTools && isUnix()) {
            def process = ['zip', zipFile.absolutePath, "-r", file.name].execute(null, zipFile.parentFile)
            process.consumeProcessOutput(System.out, System.err)
            assert process.waitFor() == 0
        } else {
            Zip zip = new Zip()
            zip.setBasedir(file)
            zip.setDestFile(zipFile)
            zip.setProject(new Project())
            def whenEmpty = new Zip.WhenEmpty()
            whenEmpty.setValue("create")
            zip.setWhenempty(whenEmpty)
            zip.execute()
        }
    }

    void tarTo(TestFile tarFile, boolean nativeTools) {
        if (nativeTools && isUnix()) {
            def process = ['tar', "-cf", tarFile.absolutePath, file.name].execute(null, tarFile.parentFile)
            process.consumeProcessOutput(System.out, System.err)
            assert process.waitFor() == 0
        } else {
            Tar tar = new Tar()
            tar.setBasedir(file)
            tar.setDestFile(tarFile)
            tar.setProject(new Project())
            tar.execute()
        }
    }
}
