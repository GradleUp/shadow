plugins {
  groovy
  `java-gradle-plugin`
  id("shadow.convention.publish")
  id("shadow.convention.deploy")
  id("com.diffplug.spotless") version "7.0.0.BETA2"
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

spotless {
  kotlinGradle {
    ktlint()
    target("**/*.kts")
    targetExclude("build-logic/build/**")
  }
}

dependencies {
  compileOnly(localGroovy())

  implementation("org.jdom:jdom2:2.0.6.1")
  implementation("org.ow2.asm:asm-commons:9.7")
  implementation("commons-io:commons-io:2.17.0")
  implementation("org.apache.ant:ant:1.10.15")
  implementation("org.codehaus.plexus:plexus-utils:4.0.2")
  implementation("org.codehaus.plexus:plexus-xml:4.0.4")
  implementation("org.apache.logging.log4j:log4j-core:2.24.1")
  implementation("org.vafer:jdependency:2.11")

  testImplementation("org.spockframework:spock-core:2.3-groovy-3.0") {
    exclude(group = "org.codehaus.groovy")
  }
  testImplementation("org.spockframework:spock-junit4:2.3-groovy-3.0")
  testImplementation("xmlunit:xmlunit:1.6")
  testImplementation("org.apache.commons:commons-lang3:3.17.0")
  testImplementation("com.google.guava:guava:33.3.1-jre")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.1")
  testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.1")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val isCI = providers.environmentVariable("CI").isPresent

tasks.withType<Test>().configureEach {
  useJUnitPlatform()

  maxParallelForks = Runtime.getRuntime().availableProcessors()

  if (isCI) {
    testLogging.showStandardStreams = true
    minHeapSize = "1g"
    maxHeapSize = "1g"
  }

  systemProperty("shadowVersion", version)

  // Required to test configuration cache in tests when using withDebug()
  // https://github.com/gradle/gradle/issues/22765#issuecomment-1339427241
  jvmArgs(
    "--add-opens",
    "java.base/java.util=ALL-UNNAMED",
    "--add-opens",
    "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
    "--add-opens",
    "java.base/java.lang.invoke=ALL-UNNAMED",
    "--add-opens",
    "java.base/java.net=ALL-UNNAMED",
  )
}

tasks.register("release") {
  dependsOn(
    tasks.publish,
    tasks.publishPlugins,
    tasks.gitPublishPush,
  )
}
