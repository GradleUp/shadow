import org.apache.tools.ant.filters.ReplaceTokens

plugins {
  id("org.ajoberstar.git-publish")
  id("com.github.node-gradle.node")
}

val yarnBuild = tasks.named("yarn_build") {
  dependsOn(tasks.yarn)
  inputs.dir("src/docs")
  outputs.dir("build/site")
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
      ),
    )
  }
}
