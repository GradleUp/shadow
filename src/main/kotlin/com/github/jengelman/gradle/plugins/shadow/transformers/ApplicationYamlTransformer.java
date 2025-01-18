package com.github.jengelman.gradle.plugins.shadow.transformers;

import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.codehaus.plexus.util.IOUtil;
import org.gradle.api.file.FileTreeElement;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ApplicationYamlTransformer implements Transformer {

  private static final String FILE_NAME = "application.yml";
  private static final String DOCUMENT_SEPARATOR = "---";

  private final List<String> parts = new ArrayList<>();

  @Override
  public boolean canTransformResource(FileTreeElement element) {
    return element.getName().equals(FILE_NAME);
  }

  @Override
  public void transform(TransformerContext context) {
    parts.add(toString(context.getInputStream()));
  }

  @Override
  public boolean hasTransformedResource() {
    return !parts.isEmpty();
  }

  @Override
  public void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {
    try {
      os.putNextEntry(new ZipEntry(FILE_NAME));
      IOUtil.copy(new ByteArrayInputStream(getContent().getBytes(StandardCharsets.UTF_8)), os);
      os.closeEntry();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot write application.yml", e);
    }
  }

  @Override
  public @NotNull String getName() {
    return getClass().getSimpleName();
  }

  private String toString(InputStream is) {
    String text = new BufferedReader(
      new InputStreamReader(is, StandardCharsets.UTF_8))
      .lines()
      .collect(Collectors.joining("\n"));

    try {
      is.close();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot close input stream", e);
    }

    return text;
  }

  private String getContent() {
    return parts.stream().map(part -> {
      if (!part.startsWith(DOCUMENT_SEPARATOR)) {
        return String.format("%s\n%s", DOCUMENT_SEPARATOR, part);
      }
      return part;
    }).collect(Collectors.joining(String.format("%n")));
  }
}
