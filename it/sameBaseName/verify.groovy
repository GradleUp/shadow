{ project ->
    File artifact = file("${buildDir}/distributions/artifact-${project.version}.jar") //shadowed artifact
    assert artifact.exists()

    File original = file("${buildDir}/libs/artifact-${project.version}.jar") //unshadowed
    assert original.exists()
}