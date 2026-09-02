package com.github.jengelman.gradle.plugins.shadow.buildlogic

import kotlin.io.path.isSymbolicLink
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateDocTests : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val inputDirectory: DirectoryProperty

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun generate() {
    val docRoot = inputDirectory.get().asFile

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
          sequence {
            while (matcher.find()) {
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
                )
              )
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
            (lang, relativePath, _, lineNumber, snippet, sourceLocation) ->
            val functionName = "line_${lineNumber}_$lang"
            """
            |  @Test
            |  @DisplayName("$relativePath:$lineNumber ($lang)")
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
            |import com.github.jengelman.gradle.plugins.shadow.SnippetExecutable
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
)

private val pattern = "(?ims) {4}```(groovy|kotlin)\n(.*?)\n {4}```".toPattern()
