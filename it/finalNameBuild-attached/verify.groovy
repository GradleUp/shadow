{ project ->
    File artifact = file("${buildDir}/libs/MyFinalName.jar") //original jar
    assert artifact.exists()

    File shadow = file("${buildDir}/libs/MyFinalName-shadow.jar") //shadow jar
    assert shadow.exists()

    File repoOriginal = new File("${project.repositories.mavenLocal().url}/org/apache/maven/its/shade/fnba/finalNameBuildAttached/1.0/finalNameBuildAttached-1.0.jar" - 'file:') //original
    assert repoOriginal.exists()

    File repoShadow = new File("${project.repositories.mavenLocal().url}/org/apache/maven/its/shade/fnba/finalNameBuildAttached/1.0/finalNameBuildAttached-1.0-shadow.jar" - 'file:') //shadow
    assert repoShadow.exists()
}