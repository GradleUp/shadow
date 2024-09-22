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

import java.io.PrintWriter
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TreeSet
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * Merges `META-INF/NOTICE.TXT` files.
 *
 * Modified from `org.apache.maven.plugins.shade.resource.ApacheNoticeResourceTransformer.java`.
 */
class ApacheNoticeResourceTransformer : Transformer {

  private val entries = LinkedHashSet<String>()
  private val organizationEntries = LinkedHashMap<String, MutableSet<String>>()

  @Input
  var projectName: String = ""

  @Input
  var addHeader: Boolean = true

  @Input
  var preamble1: String = """
    // ------------------------------------------------------------------
    // NOTICE file corresponding to the section 4d of The Apache License,
    // Version 2.0, in this case for
  """.trimIndent()

  @Input
  var preamble2: String = "\n// ------------------------------------------------------------------\n"

  @Input
  var preamble3: String = "This product includes software developed at\n"

  @Input
  var organizationName: String = "The Apache Software Foundation"

  @Input
  var organizationURL: String = "https://www.apache.org/"

  @Input
  var inceptionYear: String = "2006"

  @Optional
  @Input
  var copyright: String? = null

  /**
   * The file encoding of the `NOTICE` file.
   */
  @Optional
  @Input
  var encoding: Charset = Charsets.UTF_8

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.relativePath.pathString
    return NOTICE_PATH.equals(path, ignoreCase = true) || NOTICE_TXT_PATH.equals(path, ignoreCase = true)
  }

  override fun transform(context: TransformerContext) {
    if (entries.isEmpty()) {
      val year = SimpleDateFormat("yyyy").format(Date())
      val displayYear = if (inceptionYear != year) "$inceptionYear-$year" else year

      // add headers
      if (addHeader) {
        entries.add("$preamble1$projectName$preamble2")
      } else {
        entries.add("")
      }
      // fake second entry, we'll look for a real one later
      entries.add("$projectName\nCopyright $displayYear $organizationName\n")
      entries.add("$preamble3$organizationName ($organizationURL).\n")
    }

    val reader = context.inputStream.bufferedReader(encoding)

    var line = reader.readLine()
    val sb = StringBuffer()
    var currentOrg: MutableSet<String>? = null
    var lineCount = 0
    while (line != null) {
      val trimmedLine = line.trim()

      if (!trimmedLine.startsWith("//")) {
        if (trimmedLine.isNotEmpty()) {
          if (trimmedLine.startsWith("- ")) {
            // resource-bundle 1.3 mode
            if (lineCount == 1 && sb.toString().contains("This product includes/uses software(s) developed by")) {
              currentOrg = organizationEntries.getOrPut(sb.toString().trim()) { TreeSet() }
              sb.setLength(0)
            } else if (sb.isNotEmpty() && currentOrg != null) {
              currentOrg.add(sb.toString())
              sb.setLength(0)
            }
          }
          sb.append(line).append("\n")
          lineCount++
        } else {
          val ent = sb.toString()
          if (ent.startsWith(projectName) && ent.contains("Copyright ")) {
            copyright = ent
          }
          if (currentOrg == null) {
            entries.add(ent)
          } else {
            currentOrg.add(ent)
          }
          sb.setLength(0)
          lineCount = 0
          currentOrg = null
        }
      }

      line = reader.readLine()
    }
    if (sb.isNotEmpty()) {
      if (currentOrg == null) {
        entries.add(sb.toString())
      } else {
        currentOrg.add(sb.toString())
      }
    }
  }

  override fun hasTransformedResource(): Boolean = true

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val zipEntry = ZipEntry(NOTICE_PATH)
    zipEntry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, zipEntry.time)
    os.putNextEntry(zipEntry)

    val writer = PrintWriter(os.writer(encoding))

    var count = 0
    for (line in entries) {
      ++count
      if (line == copyright && count != 2) {
        continue
      }

      if (count == 2 && copyright != null) {
        writer.print(copyright)
        writer.print('\n')
      } else {
        writer.print(line)
        writer.print('\n')
      }
      if (count == 3) {
        // do org stuff
        for ((key, value) in organizationEntries) {
          writer.print(key)
          writer.print('\n')
          for (l in value) {
            writer.print(l)
          }
          writer.print('\n')
        }
      }
    }

    writer.flush()
    entries.clear()
  }

  companion object {
    private const val NOTICE_PATH = "META-INF/NOTICE"
    private const val NOTICE_TXT_PATH = "META-INF/NOTICE.txt"
  }
}
