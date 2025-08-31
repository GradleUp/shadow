@file:Suppress("UnstableApiUsage")

plugins {
  `java-library`
  alias(libs.plugins.shadow)
}

dependencies {
  api(libs.apache.ant)
  api(libs.apache.commonsIo)
  api(libs.apache.log4j)
  api(libs.asm)
  api(libs.jdependency)
  api(libs.jdom2)
  api(libs.plexus.utils)
  api(libs.plexus.xml)
}
