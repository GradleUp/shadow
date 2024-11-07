import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  kotlin("jvm") version "2.0.21"
  `java-gradle-plugin`
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
  explicitApi()
  compilerOptions {
    // https://docs.gradle.org/current/userguide/compatibility.html#kotlin
    apiVersion = KotlinVersion.KOTLIN_1_8
    jvmTarget = JvmTarget.JVM_1_8
    freeCompilerArgs.addAll(
      "-Xjvm-default=all",
    )
  }
}

dependencies {
  implementation("org.jdom:jdom2:2.0.6.1")
  implementation("org.ow2.asm:asm-commons:9.7.1")
  implementation("commons-io:commons-io:2.17.0")
  implementation("org.apache.ant:ant:1.10.15")
  implementation("org.codehaus.plexus:plexus-utils:4.0.2")
  implementation("org.codehaus.plexus:plexus-xml:4.0.4")
  implementation("org.apache.logging.log4j:log4j-core:2.24.1")
  implementation("org.vafer:jdependency:2.11")
}
