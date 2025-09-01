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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.maven.plugins.shade.relocation.Relocator;

/**
 * Aggregate Apache Groovy extension modules descriptors
 */
public class GroovyResourceTransformer extends AbstractCompatibilityTransformer {

  static final String EXT_MODULE_NAME_LEGACY = "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule";

  // Since Groovy 2.5.x/Java 9 META-INF/services may only be used by Service Providers
  static final String EXT_MODULE_NAME = "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule";

  private List<String> extensionClassesList = new ArrayList<>();

  private List<String> staticExtensionClassesList = new ArrayList<>();

  private String extModuleName = "no-module-name";

  private String extModuleVersion = "1.0";

  private long time = Long.MIN_VALUE;

  @Override
  public boolean canTransformResource(String resource) {
    return EXT_MODULE_NAME.equals(resource) || EXT_MODULE_NAME_LEGACY.equals(resource);
  }

  @Override
  public void processResource(String resource, InputStream is, List<Relocator> relocators, long time)
    throws IOException {
    Properties out = new Properties();
    try (InputStream props = is) {
      out.load(props);
    }
    String extensionClasses = out.getProperty("extensionClasses", "").trim();
    if (extensionClasses.length() > 0) {
      append(extensionClasses, extensionClassesList);
    }
    String staticExtensionClasses =
      out.getProperty("staticExtensionClasses", "").trim();
    if (staticExtensionClasses.length() > 0) {
      append(staticExtensionClasses, staticExtensionClassesList);
    }
    if (time > this.time) {
      this.time = time;
    }
  }

  private void append(String entry, List<String> list) {
    if (entry != null) {
      Collections.addAll(list, entry.split("\\s*,\\s*"));
    }
  }

  @Override
  public boolean hasTransformedResource() {
    return extensionClassesList.size() > 0 && staticExtensionClassesList.size() > 0;
  }

  @Override
  public void modifyOutputStream(JarOutputStream os) throws IOException {
    if (hasTransformedResource()) {
      JarEntry jarEntry = new JarEntry(EXT_MODULE_NAME);
      jarEntry.setTime(time);
      os.putNextEntry(jarEntry);

      Properties desc = new Properties();
      desc.put("moduleName", extModuleName);
      desc.put("moduleVersion", extModuleVersion);
      if (extensionClassesList.size() > 0) {
        desc.put("extensionClasses", join(extensionClassesList));
      }
      if (staticExtensionClassesList.size() > 0) {
        desc.put("staticExtensionClasses", join(staticExtensionClassesList));
      }
      desc.store(os, null);
    }
  }

  private String join(Collection<String> strings) {
    Iterator<String> it = strings.iterator();
    switch (strings.size()) {
      case 0:
        return "";
      case 1:
        return it.next();
      default:
        StringBuilder buff = new StringBuilder(it.next());
        while (it.hasNext()) {
          buff.append(",").append(it.next());
        }
        return buff.toString();
    }
  }

  public void setExtModuleName(String extModuleName) {
    this.extModuleName = extModuleName;
  }

  public void setExtModuleVersion(String extModuleVersion) {
    this.extModuleVersion = extModuleVersion;
  }
}
