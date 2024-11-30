package com.github.jengelman.gradle.plugins.shadow.util.file

class ExecOutput {
    ExecOutput(String rawOutput, String error) {
        this.rawOutput = rawOutput
        this.out = rawOutput.replaceAll("\r\n|\r", "\n")
        this.error = error
    }

    String rawOutput
    String out
    String error
}
