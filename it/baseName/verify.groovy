{ project ->
    File artifact = file("${buildDir}/libs/${project.name}.jar") //unshadowed
    assert artifact.exists()

    File original = file("${buildDir}/distributions/MyShadowBaseName.jar") //shadowed artifact
    assert original.exists()

    File repo = new File("${project.repositories.mavenLocal().url}/org/gradle/plugins/shadow/its/bn/baseName/1.0/baseName-1.0.jar" - 'file:')
    assert repo.exists()
}