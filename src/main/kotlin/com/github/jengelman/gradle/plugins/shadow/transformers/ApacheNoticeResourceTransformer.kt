package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.property
import java.io.PrintWriter
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TreeSet
import javax.inject.Inject
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * Merges `META-INF/NOTICE.TXT` files.
 *
 * Modified from [org.apache.maven.plugins.shade.resource.ApacheNoticeResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/ApacheNoticeResourceTransformer.java).
 *
 * @author John Engelman
 */
public open class ApacheNoticeResourceTransformer @Inject constructor(
  final override val objectFactory: ObjectFactory,
) : Transformer {
  private val entries = mutableSetOf<String>()
  private val organizationEntries = mutableMapOf<String, MutableSet<String>>()
  private inline val charset get() = Charset.forName(charsetName.get())

  @get:Input
  public open val projectName: Property<String> = objectFactory.property("")

  @get:Input
  public open val addHeader: Property<Boolean> = objectFactory.property(true)

  @get:Input
  public open val preamble1: Property<String> = objectFactory.property(
    """
      // ------------------------------------------------------------------
      // NOTICE file corresponding to the section 4d of The Apache License,
      // Version 2.0, in this case for
    """.trimIndent(),
  )

  @get:Input
  public open val preamble2: Property<String> = objectFactory.property(
    "\n// ------------------------------------------------------------------\n",
  )

  @get:Input
  public open val preamble3: Property<String> = objectFactory.property("This product includes software developed at\n")

  @get:Input
  public open val organizationName: Property<String> = objectFactory.property("The Apache Software Foundation")

  @get:Input
  public open val organizationURL: Property<String> = objectFactory.property("http://www.apache.org/")

  @get:Input
  public open val inceptionYear: Property<String> = objectFactory.property("2006")

  @get:Optional
  @get:Input
  public open val copyright: Property<String> = objectFactory.property()

  /**
   * The file encoding of the `NOTICE` file.
   */
  @get:Input
  public open val charsetName: Property<String> = objectFactory.property(Charsets.UTF_8.name())

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.relativePath.pathString
    return NOTICE_PATH.equals(path, ignoreCase = true) ||
      NOTICE_TXT_PATH.equals(path, ignoreCase = true) ||
      NOTICE_MD_PATH.equals(path, ignoreCase = true)
  }

  override fun transform(context: TransformerContext) {
    val projectName = projectName.get()
    val addHeader = addHeader.get()
    val preamble1 = preamble1.get()
    val preamble2 = preamble2.get()
    val preamble3 = preamble3.get()
    val organizationName = organizationName.get()
    val organizationURL = organizationURL.get()
    val inceptionYear = inceptionYear.get()

    if (entries.isEmpty()) {
      val year = SimpleDateFormat("yyyy", Locale.US).format(Date()).let {
        if (inceptionYear != it) "$inceptionYear-$it" else it
      }
      // add headers
      if (addHeader) {
        entries.add("$preamble1$projectName$preamble2")
      } else {
        entries.add("")
      }
      // fake second entry, we'll look for a real one later
      entries.add("$projectName\nCopyright $year $organizationName\n")
      entries.add("$preamble3$organizationName ($organizationURL).\n")
    }

    val reader = context.inputStream.bufferedReader(charset)
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
          val entry = sb.toString()
          if (entry.startsWith(projectName) && entry.contains("Copyright ")) {
            copyright.set(entry)
          }
          if (currentOrg == null) {
            entries.add(entry)
          } else {
            currentOrg.add(entry)
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
    val copyright = copyright.orNull

    val zipEntry = ZipEntry(NOTICE_PATH)
    zipEntry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, zipEntry.time)
    os.putNextEntry(zipEntry)

    val writer = PrintWriter(os.writer(charset))

    var count = 0
    for (line in entries) {
      count++
      if (line == copyright && count != 2) continue
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

  private companion object {
    private const val NOTICE_PATH = "META-INF/NOTICE"
    private const val NOTICE_TXT_PATH = "META-INF/NOTICE.txt"
    private const val NOTICE_MD_PATH = "META-INF/NOTICE.md"
  }
}
