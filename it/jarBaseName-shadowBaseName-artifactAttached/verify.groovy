{ project ->

    File[] files = file("${buildDir}/libs").listFiles()
    assert files.size() == 1
    assert files[0].name == "${jar.baseName}-${currentVersion}.jar"
}
