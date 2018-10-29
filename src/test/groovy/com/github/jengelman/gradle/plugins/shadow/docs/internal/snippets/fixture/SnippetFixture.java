package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture;

import com.github.jengelman.gradle.plugins.shadow.docs.internal.Block;

public class SnippetFixture {

  public void around(Block action) throws Exception {
    action.execute();
  }

  public String transform(String text) {
    return text;
  }

  public String pre() {
    return "";
  }

  public String post() {
    return "";
  }

  public Integer getOffset() {
    return pre().split("\n").length;
  }

}
