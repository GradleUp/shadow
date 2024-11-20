import org.apache.tools.ant.filters.ReplaceTokens

plugins {
  id("org.ajoberstar.git-publish")
  id("com.github.node-gradle.node")
}

gitPublish {
  repoUri = "https://github.com/GradleUp/shadow.git"
  branch = "gh-pages"
  contents {
    from("build/site")
    into("api") {
      from(tasks.named("dokkaHtml"))
    }
    filter<ReplaceTokens>(
      "tokens" to mapOf(
        "version" to version,
        "snapshot-version" to "$version-SNAPSHOT",
      ),
    )
  }
}

node {
  yarnVersion = "1.5.1"
}

val yarnBuild = tasks.named("yarn_build") {
  inputs.files(fileTree("src/docs"))
  outputs.dir(file("build/site"))
  dependsOn(tasks.yarn)
}

tasks.gitPublishCopy {
  dependsOn(yarnBuild, tasks.named("dokkaHtml"))
}
