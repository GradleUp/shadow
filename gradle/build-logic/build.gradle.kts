plugins {
  `kotlin-dsl`
}

gradlePlugin {
  plugins {
    register("build-logic") {
      implementationClass = "com.github.jengelman.gradle.plugins.shadow.buildlogic.BuildLogicPlugin"
    }
  }
}
