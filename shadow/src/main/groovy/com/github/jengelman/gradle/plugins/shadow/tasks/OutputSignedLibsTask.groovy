package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.tasks.Copy

class OutputSignedLibsTask extends Copy {

    static final String NAME = "copySignedLibs"
    static final String DESC = "Copies signed libraries to a specified output folder for later packaging."

}
