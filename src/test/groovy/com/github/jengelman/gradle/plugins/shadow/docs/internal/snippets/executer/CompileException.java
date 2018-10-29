package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer;

public class CompileException extends RuntimeException {

  private static final long serialVersionUID = 0;

  private final int lineNo;

  public CompileException(Throwable cause, int lineNo) {
    super(cause);
    this.lineNo = lineNo;
  }

  public int getLineNo() {
    return lineNo;
  }

}