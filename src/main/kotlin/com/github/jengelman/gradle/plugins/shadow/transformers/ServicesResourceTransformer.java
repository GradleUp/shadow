/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.shade.resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugins.shade.relocation.Relocator;

/**
 * Resources transformer that relocates classes in META-INF/services and appends entries in META-INF/services resources
 * into a single resource. For example, if there are several META-INF/services/org.apache.maven.project.ProjectBuilder
 * resources spread across many JARs the individual entries will all be concatenated into a single
 * META-INF/services/org.apache.maven.project.ProjectBuilder resource packaged into the resultant JAR produced by the
 * shading process.
 */
public class ServicesResourceTransformer extends AbstractCompatibilityTransformer {
  private static final String SERVICES_PATH = "META-INF/services";

  private final Map<String, Set<String>> serviceEntries = new HashMap<>();

  private long time = Long.MIN_VALUE;

  @Override
  public boolean canTransformResource(String resource) {
    return resource.startsWith(SERVICES_PATH);
  }

  @Override
  public void processResource(String resource, InputStream is, final List<Relocator> relocators, long time)
    throws IOException {
    resource = resource.substring(SERVICES_PATH.length() + 1);
    for (Relocator relocator : relocators) {
      if (relocator.canRelocateClass(resource)) {
        resource = relocator.relocateClass(resource);
        break;
      }
    }
    resource = SERVICES_PATH + '/' + resource;

    Set<String> out = serviceEntries.computeIfAbsent(resource, k -> new LinkedHashSet<>());

    Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name());
    while (scanner.hasNextLine()) {
      String relContent = scanner.nextLine();
      for (Relocator relocator : relocators) {
        if (relocator.canRelocateClass(relContent)) {
          relContent = relocator.applyToSourceContent(relContent);
        }
      }
      out.add(relContent);
    }

    if (time > this.time) {
      this.time = time;
    }
  }

  @Override
  public boolean hasTransformedResource() {
    return !serviceEntries.isEmpty();
  }

  @Override
  public void modifyOutputStream(JarOutputStream jos) throws IOException {
    for (Map.Entry<String, Set<String>> entry : serviceEntries.entrySet()) {
      String key = entry.getKey();
      Set<String> data = entry.getValue();

      JarEntry jarEntry = new JarEntry(key);
      jarEntry.setTime(time);
      jos.putNextEntry(jarEntry);

      IOUtils.writeLines(data, "\n", jos, StandardCharsets.UTF_8);
      jos.flush();
      data.clear();
    }
  }
}
