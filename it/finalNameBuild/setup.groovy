import org.apache.commons.io.FileUtils

{ project ->
    File f = new File("${project.repositories.mavenLocal().url}/org/apache/maven/its/shade/fnb/" - 'file:')
    FileUtils.deleteDirectory(f)
}