import gradle.kotlin.dsl.accessors._a257bd6ce496772590aa10dcded4cc98.dokkaHtml
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
  id("org.ajoberstar.git-publish")
  id("com.github.node-gradle.node")
}

val yarnBuild = tasks.named("yarn_build") {
  inputs.files(fileTree("src/docs"))
  outputs.dir(file("build/site"))
  dependsOn(tasks.yarn)
}

gitPublish {
  repoUri = "https://github.com/GradleUp/shadow.git"
  branch = "gh-pages"
  username = providers.environmentVariable("GITHUB_USER")
  password = providers.environmentVariable("GITHUB_TOKEN")
  contents {
    from(yarnBuild)
    from(tasks.dokkaHtml) {
      into("api")
    }
    filter<ReplaceTokens>(
      "tokens" to mapOf(
        "version" to version,
        "snapshot-version" to "$version-SNAPSHOT",
      ),
    )
  }
}
