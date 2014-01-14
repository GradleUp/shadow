{ project ->
    def unwanted = [
            'system.properties',
    ]

    def jar = file("${buildDir}/distributions/${project.name}-${currentVersion}.jar")

    assert jar.exists()

    def jarFile = zipTree(jar)

    unwanted.each { item ->
        assert jarFile.matching {
            include { fileTreeElement ->
                return fileTreeElement.relativePath.toString() == item
            }
        }.isEmpty(), "Found $item in jar"
    }
}
