import org.apache.tools.ant.filters.ReplaceTokens

plugins {
  id("org.ajoberstar.git-publish")
  id("com.github.node-gradle.node")
}

gitPublish {
  repoUri = "https://github.com/GradleUp/shadow.git"
  branch = "gh-pages"
  username = providers.environmentVariable("GITHUB_USER")
  password = providers.environmentVariable("GITHUB_TOKEN")
  contents {
    from(yarnBuild)
    from(tasks.named("dokkaHtml")) {
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

val yarnBuild = tasks.named("yarn_build") {
  dependsOn(tasks.yarn)
  inputs.files(fileTree("src/docs"))
  outputs.dir(file("build/site"))
}
