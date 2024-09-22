import me.champeau.gradle.japicmp.JapicmpTask

plugins {
  `java-library`
  id("me.champeau.gradle.japicmp")
}

val baseline: Configuration by configurations.creating

dependencies {
  baseline("com.gradleup.shadow:shadow-gradle-plugin") {
    isTransitive = false
    version { strictly("8.3.2") }
  }
}

val japicmp by tasks.registering(JapicmpTask::class) {
  oldClasspath = baseline
  newClasspath.from(tasks.jar)
  ignoreMissingClasses = true
}

tasks.check.configure {
  dependsOn(japicmp)
}
