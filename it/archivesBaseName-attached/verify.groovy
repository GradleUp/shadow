{ project ->
    File artifact = file("${buildDir}/libs/MyArchivesBaseName.jar") //original jar
    assert artifact.exists()

    File shadow = file("${buildDir}/libs/MyArchivesBaseName-shadow.jar") //shadow jar
    assert shadow.exists()

    File repoOriginal = new File("${project.repositories.mavenLocal().url}/org/gradle/plugins/shadow/its/abna/archivesBaseNameAttached/1.0/archivesBaseNameAttached-1.0.jar" - 'file:') //original
    assert repoOriginal.exists()

    File repoShadow = new File("${project.repositories.mavenLocal().url}/org/gradle/plugins/shadow/its/abna/archivesBaseNameAttached/1.0/archivesBaseNameAttached-1.0-shadow.jar" - 'file:') //shadow
    assert repoShadow.exists()
}