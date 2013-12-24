{ project ->
    def wanted = [
            'a.properties',
            'org/apache/a.properties',
            'org/apache/maven/a.properties',
            'b.properties',
            'org/apache/maven/b.properties',
            'META-INF/MANIFEST.MF'
    ]
    def unwanted = [
            'META-INF/maven/org.apache.maven.its.shade.fac/a/pom.properties',
            'org/a.properties',
            'org/b.properties',
            'org/apache/b.properties',
            'org/apache/maven/b/b.properties',
            'META-INF/maven/org.apache.maven.its.shade.fac/b/pom.properties',
            'META-INF/maven/org.apache.maven.its.shade.fac/b/pom.xml'
    ]

    def jar = file("${buildDir}/distributions/${project.name}-${currentVersion}.jar")

    assert jar.exists()

    def jarFile = zipTree(jar)

    wanted.each { item ->
        assert !jarFile.matching {
            include { fileTreeElement ->
                return fileTreeElement.relativePath.toString() == item
            }
        }.isEmpty(), "Did not find $item in jar"
    }

    unwanted.each { item ->
        assert jarFile.matching {
            include { fileTreeElement ->
                fileTreeElement.relativePath.toString() == item
            }
        }.isEmpty(), "Found $item in jar"
    }
}
