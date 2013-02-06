{ project ->
    File artifact = file("${buildDir}/libs/MyFinalName.jar") //shadowed artifact
    assert artifact.exists()

    File original = file("${buildDir}/libs/original-MyFinalName.jar") //unshadowed
    assert original.exists()

    File repo = new File("${project.repositories.mavenLocal().url}/org/gradle/plugins/shadow/its/abn/finalNameBuild/1.0/finalNameBuild-1.0.jar" - 'file:')
    assert repo.exists()
}