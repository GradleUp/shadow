{ project ->
    File artifact = file("${buildDir}/libs/${project.name}.jar") //shadowed artifact
    assert artifact.exists()

    File original = file("${buildDir}/libs/MyShadowBaseName.jar") //unshadowed
    assert original.exists()

    File repo = new File("${project.repositories.mavenLocal().url}/org/gradle/plugins/shadow/its/bn/baseName/1.0/baseName-1.0.jar" - 'file:')
    assert repo.exists()
}