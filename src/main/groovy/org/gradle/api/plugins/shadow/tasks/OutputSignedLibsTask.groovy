package org.gradle.api.plugins.shadow.tasks

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.copy.CopyActionImpl
import org.gradle.api.internal.file.copy.FileCopyActionImpl
import org.gradle.api.internal.file.copy.FileCopySpecVisitor
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory

class OutputSignedLibsTask extends AbstractCopyTask {

    static final String NAME = "copySignedLibs"
    static final String DESC = "Copies signed libraries to a specified output folder for later packaging."

    @OutputDirectory
    File outputDir = project.shadow.signedLibsDir

    @Override
    @InputFiles
    FileCollection getSource() {
        project.files(signedJars)
    }

    @Override
    protected CopyActionImpl getCopyAction() {

        return new OutputSignedLibsCopyTaskAction(this, getServices().get(FileResolver.class))
    }

    List<File> getSignedJars() {
        signedCompileJars + signedRuntimeJars
    }

    List<File> getSignedCompileJars() {
        project.configurations.signedCompile.resolve() as List
    }

    List<File> getSignedRuntimeJars() {
        project.configurations.signedRuntime.resolve() as List
    }
}

class OutputSignedLibsCopyTaskAction extends FileCopyActionImpl {

    OutputSignedLibsTask task

    OutputSignedLibsCopyTaskAction(OutputSignedLibsTask task, FileResolver fileResolver) {
        super(fileResolver, new FileCopySpecVisitor())
        this.task = task
        from(task.signedJars)
        into(task.outputDir)
    }

}
