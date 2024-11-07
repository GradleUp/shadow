/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") you may not use this file except in compliance
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
package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext.Companion.getEntryTimestamp
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File

/**
 * A resource processor that allows the addition of an arbitrary file
 * content into the shaded JAR.
 *
 * Modified from `org.apache.maven.plugins.shade.resource.IncludeResourceTransformer.java`
 *
 * @author John Engelman
 */
public class IncludeResourceTransformer : Transformer by NoOpTransformer {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  public var file: File? = null

  @get:Input
  public var resource: String? = null

  override fun hasTransformedResource(): Boolean = file?.exists() == true

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    check(file != null) { "file must be set" }
    check(resource != null) { "resource must be set" }

    val entry = ZipEntry(resource)
    entry.time = getEntryTimestamp(preserveFileTimestamps, entry.time)
    os.putNextEntry(entry)

    file!!.inputStream().use { inputStream ->
      inputStream.copyTo(os)
    }
  }
}
