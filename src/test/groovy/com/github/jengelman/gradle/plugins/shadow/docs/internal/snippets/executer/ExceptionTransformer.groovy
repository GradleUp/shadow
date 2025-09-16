package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer

class ExceptionTransformer {

    final String sourceClassName
    final String sourceFileName
    final Integer lineNumber

    ExceptionTransformer(String sourceClassName, String sourceFileName, Integer lineNumber) {
        this.sourceClassName = sourceClassName
        this.sourceFileName = sourceFileName
        this.lineNumber = lineNumber
    }

    Throwable transform(Throwable throwable, Integer offset) throws Exception {
        def errorLine = 0

        if (throwable instanceof CompileException) {
            errorLine = throwable.lineNo
        } else {
            def frame = throwable.getStackTrace().find { it.fileName == sourceClassName }
            if (frame) {
                errorLine = frame.lineNumber
            } else {
                frame = throwable.getStackTrace().find { it.fileName == "Example.java" }
                if (frame) {
                    errorLine = frame.lineNumber
                }
            }
        }
        errorLine = errorLine - offset
        StackTraceElement[] stack = throwable.getStackTrace()
        List<StackTraceElement> newStack = new ArrayList<StackTraceElement>(stack.length + 1)
        newStack.add(new StackTraceElement(sourceClassName, "javadoc", sourceFileName, lineNumber + errorLine))
        newStack.addAll(stack)
        throwable.setStackTrace(newStack as StackTraceElement[])
        throwable
    }
}
