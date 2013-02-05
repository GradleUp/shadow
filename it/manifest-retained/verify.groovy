import java.util.jar.*

{ project ->

    // NOTE: We deliberately use JarInputStream and not JarFile here!
    def jar = file("${buildDir}/libs/${project.name}-shadow-${currentVersion}.jar")
    JarInputStream jarStream = new JarInputStream(jar.newInputStream())
    Manifest mf = jarStream.getManifest()
    jarStream.close()

    assert mf
    assert mf.mainAttributes.getValue('Test-Entry') == 'PASSED'
    assert mf.mainAttributes.getValue('Main-Class') == 'org.apache.maven.Main'

}
