plugins {
  application
  id("com.gradleup.shadow")
}

application {
  mainClass = "com.company.Main"
}

dependencies {
  implementation(project(":lib"))
}

val transformedAttribute = Attribute.of("custom-transformed", Boolean::class.javaObjectType)

dependencies.artifactTypes.maybeCreate("jar").attributes.attribute(transformedAttribute, false)

dependencies.registerTransform(CustomTransformAction::class) {
  from.attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
    .attribute(transformedAttribute, false)
  to.attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar").attribute(transformedAttribute, true)
}

tasks.shadowJar {
  configurations = project.configurations.runtimeClasspath.map { listOf(it) }
}

configurations.runtimeClasspath {
  attributes.attribute(transformedAttribute, true)
}

abstract class CustomTransformAction : TransformAction<TransformParameters.None> {

  @get:InputArtifact
  abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    val originalJar = inputArtifact.get().asFile

    // Omitted actual jar file transformation

    outputs.file(originalJar)
  }
}
