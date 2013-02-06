{ project ->
    File artifact = file("${buildDir}/libs/MyArchivesBaseName.jar") //shadowed artifact
    assert artifact.exists()

    File original = file("${buildDir}/libs/original-MyArchivesBaseName.jar") //unshadowed
    assert original.exists()

    File repo = new File("${project.repositories.mavenLocal().url}/org/gradle/plugins/shadow/its/abn/archivesBaseName/1.0/archivesBaseName-1.0.jar" - 'file:')
    assert repo.exists()
}