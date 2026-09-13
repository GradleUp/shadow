package com.github.jengelman.gradle.plugins.shadow.buildlogic

import kotlin.io.path.isSymbolicLink
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * TODO: blocked by https://github.com/gradle/test-retry-gradle-plugin/issues/540 or
 *   https://github.com/gradle/test-retry-gradle-plugin/issues/152
 */
abstract class GenerateDocTests : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val inputDirectory: DirectoryProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val testSourceDirectories: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun generate() {
    val docRoot = inputDirectory.get().asFile

    val classMethods = mutableMapOf<String, MutableSet<String>>()
    val methodPattern = Regex("""\bfun\s+`?([A-Za-z0-9_]+)`?\s*[(<]""")
    val javaMethodPattern = Regex("""(?:public|protected|private|void)\s+([A-Za-z0-9_]+)\s*\(""")

    testSourceDirectories.asFileTree.forEach { file ->
      if (file.isFile && (file.extension == "kt" || file.extension == "java")) {
        val className = file.nameWithoutExtension
        val text = file.readText()
        val methods = classMethods.getOrPut(className) { mutableSetOf() }
        methodPattern.findAll(text).forEach { methods += it.groupValues[1] }
        javaMethodPattern.findAll(text).forEach { methods += it.groupValues[1] }
      }
    }

    val snippets =
      docRoot
        .walk()
        .filter { it.isFile && it.extension == "md" && !it.toPath().isSymbolicLink() }
        .sortedBy { it.path }
        .flatMap { file ->
          val source = file.readText()
          val relativePath = file.relativeTo(docRoot).invariantSeparatorsPath
          val parentDir = file.parentFile.relativeTo(docRoot).invariantSeparatorsPath
          val matcher = pattern.matcher(source)
          var lastEnd = 0
          var currentTestRef: String? = null
          sequence {
            while (matcher.find()) {
              val textBefore = source.substring(lastEnd, matcher.start())
              val testCommentMatcher = testAnnotationPattern.matcher(textBefore)
              if (testCommentMatcher.find()) {
                currentTestRef = testCommentMatcher.group(1).trim()
              } else if (matcher.group(1) == "kotlin") {
                // Reset for a new snippet group if no test annotation was found before the kotlin
                // block
                currentTestRef = null
              }

              var line = 1
              for (i in 0 until matcher.start().coerceAtMost(source.length)) {
                if (source[i] == '\n') line++
              }
              yield(
                Snippet(
                  lang = matcher.group(1),
                  relativePath = relativePath,
                  parentDir = parentDir,
                  lineNumber = line,
                  snippet = matcher.group(2),
                  sourceLocation = "${file.toURI()}:$line",
                  testRef = currentTestRef,
                )
              )
              lastEnd = matcher.end()
            }
          }
        }
        .toList()

    val groovySnippets = snippets.filter { it.lang == "groovy" }
    val kotlinSnippets = snippets.filter { it.lang == "kotlin" }

    check(snippets.isNotEmpty()) { "No code snippets found in $docRoot." }
    check(groovySnippets.size == kotlinSnippets.size) {
      "All languages must have the same number of code snippets: groovy=${groovySnippets.size}, kotlin=${kotlinSnippets.size}"
    }

    snippets.forEach { snippet ->
      val testRef = snippet.testRef ?: return@forEach
      val parts = testRef.split('#', limit = 2)
      val className = parts[0]
      val methodName = parts.getOrNull(1)

      val methods = classMethods[className]
      check(methods != null) {
        "Referenced test class '$className' not found in test sources (referenced from ${snippet.sourceLocation})."
      }
      if (methodName != null) {
        check(methodName in methods) {
          "Referenced test method '$methodName' not found in test class '$className' (referenced from ${snippet.sourceLocation})."
        }
      }
    }

    val outputDir = outputDirectory.get().asFile
    snippets
      .groupBy { it.parentDir }
      .forEach { (parentDir, fileSnippets) ->
        val className =
          if (parentDir.isEmpty()) {
            "ReadmeDocTest"
          } else {
            parentDir.split('/', '-').joinToString("") { it.replaceFirstChar(Char::titlecase) } +
              "DocTest"
          }
        val functionsCode =
          fileSnippets.joinToString("\n\n") {
            (lang, relativePath, _, lineNumber, snippet, sourceLocation, testRef) ->
            val functionName = "line_${lineNumber}_$lang"
            val displayName = "$relativePath:$lineNumber ($lang)"
            val kdoc =
              if (testRef != null) {
                val kdocRef = testRef.replace('#', '.')
                "  /** @see [$kdocRef] */\n"
              } else {
                ""
              }
            """
            |$kdoc  @Test
            |  @DisplayName("$displayName")
            |  fun `$functionName`(@TempDir tempDir: Path) {
            |    SnippetExecutable(
            |      lang = "$lang",
            |      snippet =
            |        ""${'"'}
            |        $snippet
            |        ""${'"'},
            |      sourceLocation = "$sourceLocation",
            |    ).execute(tempDir)
            |  }
            """
              .trimMargin()
          }
        outputDir
          .resolve("com/github/jengelman/gradle/plugins/shadow/docs/$className.kt")
          .apply { parentFile.mkdirs() }
          .writeText(
            """
            |package com.github.jengelman.gradle.plugins.shadow.docs
            |
            |import com.github.jengelman.gradle.plugins.shadow.*
            |import com.github.jengelman.gradle.plugins.shadow.SnippetExecutable
            |import com.github.jengelman.gradle.plugins.shadow.transformers.*
            |import java.nio.file.Path
            |import org.junit.jupiter.api.DisplayName
            |import org.junit.jupiter.api.Test
            |import org.junit.jupiter.api.io.TempDir
            |
            |class $className {
            |$functionsCode
            |}
            |
            """
              .trimMargin()
          )
      }
  }
}

private data class Snippet(
  val lang: String,
  val relativePath: String,
  val parentDir: String,
  val lineNumber: Int,
  val snippet: String,
  val sourceLocation: String,
  val testRef: String?,
)

private val pattern = "(?ims) {4}```(groovy|kotlin)\n(.*?)\n {4}```".toPattern()
private val testAnnotationPattern = "<!--\\s*test:\\s*([^>]+?)\\s*-->".toPattern()
